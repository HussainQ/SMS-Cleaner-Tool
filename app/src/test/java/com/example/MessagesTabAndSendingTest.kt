package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ContactFilterSelectionEntity
import com.example.model.ComposeAttachment
import com.example.model.MessageProtocol
import com.example.model.MessagingHelper
import com.example.model.MessagingSendProtocol
import com.example.model.SmsMessageItem
import com.example.telephony.MmsPduBuilder
import com.example.ui.CleanSmsViewModel
import com.example.ui.theme.CleanSMSTheme
import com.example.update.AppUpgradeManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessagesTabAndSendingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences(AppUpgradeManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        app.getSharedPreferences(AppUpgradeManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val dao = AppDatabase.getInstance(app).cleanSmsDao()
        runBlocking {
            dao.clearAllQuarantine()
            dao.clearAllDeletedTestSnapshots()
            dao.clearInboxCache()
            dao.clearFilterSelections(ContactFilterSelectionEntity.SCOPE_SCANNER)
            dao.clearFilterSelections(ContactFilterSelectionEntity.SCOPE_VAULT)
        }
    }

    @Test
    fun messagingHelper_groupsThreadsByProtocol_tracksUnread_andFiltersBySearch() {
        val msgs = listOf(
            SmsMessageItem(
                id = "1",
                address = "+15550001111",
                contactName = "Alice",
                body = "Hey there",
                date = 1000L,
                type = 1,
                threadId = 10L,
                protocol = MessageProtocol.RCS_CHAT,
                isRcsChat = true,
                isRead = false
            ),
            SmsMessageItem(
                id = "2",
                address = "+15550001111",
                contactName = "Alice",
                body = "On my way!",
                date = 2000L,
                type = 2,
                threadId = 10L,
                protocol = MessageProtocol.RCS_CHAT,
                isRcsChat = true,
                isRead = true
            ),
            SmsMessageItem(
                id = "3",
                address = "+15550002222",
                contactName = "Bob",
                body = "See photo attached",
                date = 3000L,
                type = 1,
                threadId = 20L,
                protocol = MessageProtocol.MMS,
                attachmentUri = "content://mms/part/99",
                attachmentMimeType = "image/jpeg",
                isRead = false
            )
        )

        val allThreads = MessagingHelper.buildConversationThreads(msgs)
        assertEquals(2, allThreads.size)
        // Newest thread (Bob, date=3000L) appears first
        assertEquals("Bob", allThreads[0].displayName)
        assertEquals(MessageProtocol.MMS, allThreads[0].protocol)
        assertTrue(allThreads[0].hasAttachments)
        assertEquals(1, allThreads[0].unreadCount)

        // Alice thread is sorted oldest->newest inside thread
        assertEquals("Alice", allThreads[1].displayName)
        assertEquals(listOf("1", "2"), allThreads[1].messages.map { it.id })
        assertEquals(1, allThreads[1].unreadCount)
        assertEquals(MessageProtocol.RCS_CHAT, allThreads[1].protocol)

        val filtered = MessagingHelper.buildConversationThreads(msgs, searchQuery = "on my way")
        assertEquals(1, filtered.size)
        assertEquals("Alice", filtered[0].displayName)
    }

    @Test
    fun messagingHelper_parsesSmsToAndMmsToSendIntents() {
        val smsToIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+15551234567")).apply {
            putExtra("sms_body", "Hello from dialer")
        }
        val parsed = MessagingHelper.parseSendIntent(smsToIntent)
        assertEquals("+15551234567" to "Hello from dialer", parsed)

        val mmsIntent = Intent(Intent.ACTION_SEND, Uri.parse("mms:+15557654321?body=Check%20this"))
        val parsedMms = MessagingHelper.parseSendIntent(mmsIntent)
        assertEquals("+15557654321" to "Check this", parsedMms)
    }

    @Test
    fun mmsPduBuilder_encodesValidSendReqHeadersAndMultipartParts() {
        val bytes = MmsPduBuilder.buildSendReqPduBytes(
            recipient = "+15551230000",
            bodyText = "MMS Hello",
            attachmentBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04),
            attachmentMimeType = "image/jpeg",
            attachmentFileName = "photo.jpg",
            transactionId = "T12345"
        )
        // Starts with X-Mms-Message-Type (0x8C) -> m-send-req (0x80)
        assertTrue(bytes.size > 30)
        assertEquals(0x8C.toByte(), bytes[0])
        assertEquals(0x80.toByte(), bytes[1])
        val asAscii = String(bytes, Charsets.ISO_8859_1)
        assertTrue(asAscii.contains("+15551230000/TYPE=PLMN"))
        assertTrue(asAscii.contains("MMS Hello"))
        assertTrue(asAscii.contains("photo.jpg"))
    }

    @Test
    fun appOpensDirectlyToMessagesTab_andSupportsReadingAndReplyingSmsMmsRcs() {
        val vm = CleanSmsViewModel(app)
        composeRule.setContent {
            CleanSMSTheme {
                CleanSmsApp(viewModel = vm)
            }
        }

        // App must land on MESSAGES tab as the default opening page
        assertEquals(NavigationTab.MESSAGES, vm.uiState.value.currentTab)
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.conversationThreads.isNotEmpty()
        }

        composeRule.onNodeWithTag("messages_inbox_list").assertIsDisplayed()

        // Find unread Jordan Lee RCS thread & open it -> unread count clears to 0
        val jordanThread = vm.uiState.value.conversationThreads.first { it.displayName.contains("Jordan") }
        assertTrue(jordanThread.unreadCount > 0)

        composeRule.onNodeWithTag("messages_inbox_list")
            .performScrollToNode(hasTestTag("conversation_row_${jordanThread.threadKey}"))
        composeRule.onNodeWithTag("conversation_row_${jordanThread.threadKey}").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("conversation_thread_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("conversation_header_title").assertIsDisplayed()
        val updatedJordan = vm.uiState.value.conversationThreads.first { it.threadKey == jordanThread.threadKey }
        assertEquals(0, updatedJordan.unreadCount)

        // Send an RCS reply
        composeRule.onNodeWithTag("message_composer_input").performTextInput("On my way to the gym now!")
        composeRule.onNodeWithTag("send_message_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.allMessages.any { it.body == "On my way to the gym now!" && it.type == 2 }
        }
        composeRule.onNodeWithText("On my way to the gym now!").assertIsDisplayed()

        // Also test MMS sending with an attached media item in the active thread
        composeRule.runOnIdle {
            vm.setComposerAttachment(
                ComposeAttachment(
                    uriString = "content://media/external/images/media/42",
                    mimeType = "image/jpeg",
                    displayName = "gym_selfie.jpg"
                )
            )
        }
        composeRule.onNodeWithTag("composer_attachment_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("message_composer_input").performTextInput("Front entrance")
        composeRule.onNodeWithTag("send_message_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.allMessages.any {
                it.body == "Front entrance" && it.protocol == MessageProtocol.MMS && it.attachmentLabel == "gym_selfie.jpg"
            }
        }
        composeRule.onNodeWithText("Front entrance").assertIsDisplayed()
    }

    @Test
    fun messagesTab_newConversationAndHierarchicalBackPressWorkEndToEnd() {
        val vm = CleanSmsViewModel(app)
        var dispatcherOwner: androidx.activity.OnBackPressedDispatcherOwner? = null

        composeRule.setContent {
            dispatcherOwner = LocalOnBackPressedDispatcherOwner.current
            CleanSMSTheme {
                CleanSmsApp(viewModel = vm)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.conversationThreads.isNotEmpty()
        }

        // Open New Message dialog via FAB
        composeRule.onNodeWithTag("new_message_fab").performClick()
        composeRule.onNodeWithTag("new_message_dialog").assertIsDisplayed()

        // Back 1: Dismisses New Message dialog before touching list/tabs
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertFalse(vm.uiState.value.showNewMessageDialog)

        // Open New Message dialog again and start a brand-new conversation to a phone number
        composeRule.onNodeWithTag("new_message_fab").performClick()
        composeRule.onNodeWithTag("new_message_recipient_input").performTextInput("+15559990001")
        composeRule.onNodeWithTag("new_message_start_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("conversation_thread_screen").assertIsDisplayed()
        assertNotNull(vm.uiState.value.activeConversationKey)

        // Switch composer protocol to SMS explicitly and send a message
        composeRule.onNodeWithTag("composer_protocol_sms").performClick()
        assertEquals(MessagingSendProtocol.SMS, vm.uiState.value.composerProtocol)
        composeRule.onNodeWithTag("message_composer_input").performTextInput("Hi! Testing new chat thread")
        composeRule.onNodeWithTag("send_message_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.allMessages.any { it.body == "Hi! Testing new chat thread" && it.protocol == MessageProtocol.SMS }
        }

        // Back 2: Closes conversation thread -> returns to Messages inbox list (does NOT exit app!)
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertNull(vm.uiState.value.activeConversationKey)
        composeRule.onNodeWithTag("messages_inbox_list").assertIsDisplayed()
        assertEquals(NavigationTab.MESSAGES, vm.uiState.value.currentTab)
        assertFalse(dispatcherOwner!!.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun messagingHelper_filtersByProtocolAndMmsSubject() {
        val msgs = listOf(
            SmsMessageItem(
                id = "s1",
                address = "+15550001111",
                contactName = "Courier",
                body = "Package delivered",
                date = 1000L,
                type = 1,
                threadId = 101L,
                protocol = MessageProtocol.SMS
            ),
            SmsMessageItem(
                id = "m1",
                address = "+15550002222",
                contactName = "Maya",
                body = "Look at this snapshot",
                mmsSubject = "Beach Weekend",
                date = 2000L,
                type = 1,
                threadId = 102L,
                protocol = MessageProtocol.MMS,
                attachmentUri = "content://mms/part/1",
                attachmentMimeType = "image/jpeg"
            ),
            SmsMessageItem(
                id = "r1",
                address = "+15550003333",
                contactName = "Jordan",
                body = "Are we still on for gym?",
                date = 3000L,
                type = 1,
                threadId = 103L,
                protocol = MessageProtocol.RCS_CHAT,
                isRcsChat = true
            )
        )

        // Protocol filter isolation
        val mmsOnly = MessagingHelper.buildConversationThreads(msgs, protocolFilter = MessageProtocol.MMS)
        assertEquals(1, mmsOnly.size)
        assertEquals("Maya", mmsOnly[0].displayName)

        val rcsOnly = MessagingHelper.buildConversationThreads(msgs, protocolFilter = MessageProtocol.RCS_CHAT)
        assertEquals(1, rcsOnly.size)
        assertEquals("Jordan", rcsOnly[0].displayName)

        // Search by MMS subject + protocol filter together
        val subjectMatch = MessagingHelper.buildConversationThreads(
            messages = msgs,
            searchQuery = "Beach Weekend",
            protocolFilter = MessageProtocol.MMS
        )
        assertEquals(1, subjectMatch.size)
        assertEquals("Maya", subjectMatch[0].displayName)

        // Search by sender name across all protocols
        val senderMatch = MessagingHelper.buildConversationThreads(msgs, searchQuery = "Courier")
        assertEquals(1, senderMatch.size)
        assertEquals(MessageProtocol.SMS, senderMatch[0].protocol)
    }

    @Test
    fun messagesTab_topSearchBarAndProtocolFilterChipsFilterLiveAndClearOnBack() {
        val vm = CleanSmsViewModel(app)
        var dispatcherOwner: androidx.activity.OnBackPressedDispatcherOwner? = null

        composeRule.setContent {
            dispatcherOwner = LocalOnBackPressedDispatcherOwner.current
            CleanSMSTheme {
                CleanSmsApp(viewModel = vm)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.conversationThreads.size >= 3
        }

        val initialCount = vm.uiState.value.conversationThreads.size
        assertTrue(initialCount >= 3)

        // Top search bar is displayed immediately at the top of Messages tab
        composeRule.onNodeWithTag("messages_search_input").assertIsDisplayed()

        // Filter by sender name ("Jordan")
        composeRule.onNodeWithTag("messages_search_input").performTextInput("Jordan")
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.conversationThreads.size == 1
        }
        assertTrue(vm.uiState.value.conversationThreads[0].displayName.contains("Jordan"))

        // Back clears active search query first and restores full list
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.messagesSearchQuery.isEmpty() &&
                vm.uiState.value.conversationThreads.size == initialCount
        }

        // Filter by message content across threads ("dinner")
        composeRule.onNodeWithTag("messages_search_input").performTextInput("dinner")
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.conversationThreads.size == 1 &&
                vm.uiState.value.conversationThreads[0].lastMessage.body.contains("dinner", ignoreCase = true)
        }

        // Clear search via trailing clear button
        composeRule.onNodeWithTag("messages_search_clear_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.conversationThreads.size == initialCount
        }

        // Filter via RCS protocol chip -> all visible threads are RCS_CHAT
        composeRule.onNodeWithTag("messages_filter_rcs").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.messagesProtocolFilter == MessageProtocol.RCS_CHAT &&
                vm.uiState.value.conversationThreads.isNotEmpty() &&
                vm.uiState.value.conversationThreads.all { it.protocol == MessageProtocol.RCS_CHAT }
        }

        // Back clears protocol filter back to All
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.messagesProtocolFilter == null &&
                vm.uiState.value.conversationThreads.size == initialCount
        }
    }

    @Test
    fun messagesTab_compactUiDefaultAndSettingsSyncBetweenDialogAndRulesCloud() {
        val vm = CleanSmsViewModel(app)
        var dispatcherOwner: androidx.activity.OnBackPressedDispatcherOwner? = null

        composeRule.setContent {
            dispatcherOwner = LocalOnBackPressedDispatcherOwner.current
            CleanSMSTheme {
                CleanSmsApp(viewModel = vm)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.conversationThreads.isNotEmpty()
        }

        // Compact mode is enabled by default
        assertTrue(vm.uiState.value.compactMessagesUi)

        // Open Messages Display Settings dialog directly from the top search row button
        composeRule.onNodeWithTag("messages_settings_btn").performClick()
        composeRule.onNodeWithTag("messages_settings_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("messages_density_switch").assertIsOn()

        // Switch to Comfortable mode from dialog
        composeRule.onNodeWithTag("compact_mode_option_comfortable").performClick()
        composeRule.waitForIdle()
        assertFalse(vm.uiState.value.compactMessagesUi)
        assertFalse(AppUpgradeManager(app).isCompactMessagesUi())
        composeRule.onNodeWithTag("messages_density_switch").assertIsOff()

        // Pressing Back dismisses the Messages Display Settings dialog cleanly
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertFalse(vm.uiState.value.showMessagesSettingsDialog)
        assertEquals(0, composeRule.onAllNodesWithTag("messages_settings_dialog").fetchSemanticsNodes().size)

        // Navigate to Settings tab and verify synchronized toggle
        if (composeRule.onAllNodesWithTag("nav_tab_settings").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("nav_tab_settings").performClick()
        } else {
            composeRule.onNodeWithTag("nav_rail_settings").performClick()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_compact_messages_switch").performScrollTo()
        composeRule.onNodeWithTag("settings_compact_messages_switch").assertIsOff()

        // Re-enable Compact Messages UI from Settings
        composeRule.onNodeWithTag("settings_compact_messages_switch").performClick()
        composeRule.waitForIdle()
        assertTrue(vm.uiState.value.compactMessagesUi)
        assertTrue(AppUpgradeManager(app).isCompactMessagesUi())
        composeRule.onNodeWithTag("settings_compact_messages_switch").assertIsOn()
    }
}
