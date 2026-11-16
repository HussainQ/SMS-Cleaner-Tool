package com.example

import android.app.Application
import android.content.Context
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ContactFilterSelectionEntity
import com.example.data.local.ThreadFolderStateEntity
import com.example.model.MessageFolderFilter
import com.example.model.MessageProtocol
import com.example.model.MessagingHelper
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import com.example.ui.CleanSmsViewModel
import com.example.ui.theme.CleanSMSTheme
import com.example.update.AppUpgradeManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ArchiveAndSpamFoldersTest {

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
        val dao = AppDatabase.getInstance(app).cleanSmsDao()
        runBlocking {
            dao.clearThreadFolderStates()
            dao.clearAllQuarantine()
            dao.clearAllDeletedTestSnapshots()
            dao.clearInboxCache()
        }
    }

    @After
    fun tearDown() {
        app.getSharedPreferences(AppUpgradeManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val dao = AppDatabase.getInstance(app).cleanSmsDao()
        runBlocking {
            dao.clearThreadFolderStates()
            dao.clearAllQuarantine()
            dao.clearAllDeletedTestSnapshots()
            dao.clearInboxCache()
            dao.clearFilterSelections(ContactFilterSelectionEntity.SCOPE_SCANNER)
            dao.clearFilterSelections(ContactFilterSelectionEntity.SCOPE_VAULT)
        }
    }

    @Test
    fun messagingHelper_isolatesInboxArchivedAndSpamFolders_andRespectsUserOverride() {
        val msgs = listOf(
            SmsMessageItem(
                id = "inbox_1",
                address = "+15551001001",
                contactName = "Regular Friend",
                body = "Lunch at 12?",
                date = 5000L,
                type = 1,
                threadId = 11L,
                category = SmsCategory.NORMAL
            ),
            SmsMessageItem(
                id = "arch_1",
                address = "+15551002002",
                contactName = "Archived Thread",
                body = "Lease renewal completed",
                date = 4000L,
                type = 1,
                threadId = 22L,
                isArchived = true,
                folderSource = MessagingHelper.FOLDER_SOURCE_PROVIDER_ARCHIVED
            ),
            SmsMessageItem(
                id = "spam_1",
                address = "+15551003003",
                contactName = "Suspicious Sender",
                body = "Claim free prize now",
                date = 3000L,
                type = 1,
                threadId = 33L,
                isSpamMarked = true,
                category = SmsCategory.SPAM,
                folderSource = MessagingHelper.FOLDER_SOURCE_PROVIDER_SPAM
            ),
            SmsMessageItem(
                id = "override_1",
                address = "+15551004004",
                contactName = "Trusted Promo",
                body = "You won a loyalty coffee",
                date = 2000L,
                type = 1,
                threadId = 44L,
                category = SmsCategory.SPAM,
                isArchived = false,
                isSpamMarked = false,
                folderSource = MessagingHelper.FOLDER_SOURCE_USER_OVERRIDE
            )
        )

        val inbox = MessagingHelper.buildConversationThreads(msgs, folderFilter = MessageFolderFilter.INBOX)
        assertEquals(setOf("Regular Friend", "Trusted Promo"), inbox.map { it.displayName }.toSet())

        val archived = MessagingHelper.buildConversationThreads(msgs, folderFilter = MessageFolderFilter.ARCHIVED)
        assertEquals(listOf("Archived Thread"), archived.map { it.displayName })
        assertTrue(archived.first().isArchived)
        assertEquals(MessagingHelper.FOLDER_SOURCE_PROVIDER_ARCHIVED, archived.first().folderSource)

        val spam = MessagingHelper.buildConversationThreads(msgs, folderFilter = MessageFolderFilter.SPAM)
        assertEquals(listOf("Suspicious Sender"), spam.map { it.displayName })
        assertTrue(spam.first().isSpam)
    }

    @Test
    fun viewModel_persistsCrossAppArchiveAndSpamStatesAcrossRescansAndUserOverrides() {
        val dao = AppDatabase.getInstance(app).cleanSmsDao()
        // Pre-seed a cross-app folder state for an address (e.g., from another SMS app without Telephony flags)
        runBlocking {
            dao.upsertThreadFolderState(
                ThreadFolderStateEntity(
                    stateKey = "tid:101",
                    addressKey = "5552345678",
                    threadId = 101L,
                    isArchived = true,
                    isSpam = false,
                    sourceApp = MessagingHelper.FOLDER_SOURCE_PROVIDER_ARCHIVED
                )
            )
        }

        val vm = CleanSmsViewModel(app)
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.allMessages.isNotEmpty()
        }

        // Seeded Demo archived ("Oakwood Leasing") + cross-app spam ("+18885559922") + Room tid:101 must NOT be in Inbox
        val inboxNames = vm.uiState.value.conversationThreads.map { it.displayName }
        assertFalse(inboxNames.any { it.contains("Oakwood Leasing") })
        assertFalse(inboxNames.any { it.contains("+18885559922") })
        assertTrue(vm.uiState.value.archivedThreadsCount >= 2)
        assertTrue(vm.uiState.value.spamThreadsCount >= 1)

        // Switch to Archived folder -> shows persisted tid:101 + Oakwood Leasing
        vm.setMessagesFolderFilter(MessageFolderFilter.ARCHIVED)
        composeRule.waitForIdle()
        assertTrue(vm.uiState.value.conversationThreads.any { it.displayName.contains("Oakwood Leasing") })
        val tid101Thread = vm.uiState.value.conversationThreads.first { it.threadId == 101L }

        // Rescan / Refresh should KEEP tid:101 & Oakwood Leasing inside Archived (cross-app persistence)
        vm.refreshMessages()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            !vm.uiState.value.isScanning
        }
        assertTrue(vm.uiState.value.conversationThreads.any { it.threadId == 101L && it.isArchived })

        // Explicit Unarchive writes FOLDER_SOURCE_USER_OVERRIDE and moves it cleanly back to Inbox across rescans
        vm.setThreadArchived(tid101Thread, archived = false)
        vm.refreshMessages()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            !vm.uiState.value.isScanning
        }
        vm.setMessagesFolderFilter(MessageFolderFilter.INBOX)
        assertTrue(vm.uiState.value.conversationThreads.any { it.threadId == 101L && !it.isArchived })
    }

    @Test
    fun messagesScreen_folderTabs_compactUiToggle_archiveSpamActions_andBackNavigation() {
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

        // 1. Direct 1-tap Compact UI toggle sits right next to search bar & syncs state immediately
        composeRule.onNodeWithTag("messages_compact_toggle_btn").assertIsDisplayed().assertIsOn()
        composeRule.onNodeWithTag("messages_compact_toggle_btn").performClick()
        composeRule.waitForIdle()
        assertFalse(vm.uiState.value.compactMessagesUi)
        composeRule.onNodeWithTag("messages_compact_toggle_btn").assertIsOff()

        composeRule.onNodeWithTag("messages_compact_toggle_btn").performClick()
        composeRule.waitForIdle()
        assertTrue(vm.uiState.value.compactMessagesUi)
        composeRule.onNodeWithTag("messages_compact_toggle_btn").assertIsOn()

        // 2. Switch to Archived folder tab -> shows Oakwood Leasing -> Back returns cleanly to Inbox
        composeRule.onNodeWithTag("messages_folder_archived").performClick()
        composeRule.waitForIdle()
        assertEquals(MessageFolderFilter.ARCHIVED, vm.uiState.value.messagesFolderFilter)
        assertTrue(vm.uiState.value.conversationThreads.any { it.displayName == "Oakwood Leasing" })

        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(MessageFolderFilter.INBOX, vm.uiState.value.messagesFolderFilter)

        // 3. Switch to Spam folder -> open spam thread -> tap Not Spam in detail header -> returns to Inbox
        composeRule.onNodeWithTag("messages_folder_spam").performClick()
        composeRule.waitForIdle()
        val spamThread = vm.uiState.value.conversationThreads.first { it.isSpam }
        composeRule.onNodeWithTag("messages_inbox_list")
            .performScrollToNode(hasTestTag("conversation_row_${spamThread.threadKey}"))
        composeRule.onNodeWithTag("conversation_row_${spamThread.threadKey}").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("conversation_spam_button").performClick()
        composeRule.waitForIdle()

        // Thread moved out of Spam folder & back into Inbox
        composeRule.runOnIdle {
            vm.clearSnackbar()
            vm.setMessagesFolderFilter(MessageFolderFilter.INBOX)
        }
        composeRule.waitForIdle()
        assertTrue(vm.uiState.value.conversationThreads.any { it.threadKey == spamThread.threadKey && !it.isSpam })

        // 4. Open an Inbox thread & Archive it via header button -> Archived count increments
        val beforeArchived = vm.uiState.value.archivedThreadsCount
        val jordan = vm.uiState.value.conversationThreads.first { it.displayName.contains("Jordan") }
        composeRule.onNodeWithTag("messages_inbox_list")
            .performScrollToNode(hasTestTag("conversation_row_${jordan.threadKey}"))
        composeRule.onNodeWithTag("conversation_row_${jordan.threadKey}").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.activeConversationKey == jordan.threadKey
        }
        composeRule.onNodeWithTag("conversation_archive_button").performClick()
        composeRule.waitForIdle()

        assertEquals(beforeArchived + 1, vm.uiState.value.archivedThreadsCount)
        assertFalse(vm.uiState.value.conversationThreads.any { it.threadKey == jordan.threadKey })

        // 5. Verify conversation row overflow menu also archives the top Inbox thread cleanly
        composeRule.runOnIdle { vm.clearSnackbar() }
        composeRule.waitForIdle()
        val topInboxThread = vm.uiState.value.conversationThreads.first()
        composeRule.onNodeWithTag("messages_inbox_list")
            .performScrollToNode(hasTestTag("conversation_row_${topInboxThread.threadKey}"))
        composeRule.onNodeWithTag("conversation_menu_${topInboxThread.threadKey}").performClick()
        composeRule.waitForIdle()
        val dialogNodes = composeRule.onAllNodes(hasTestTag("conversation_actions_dialog")).fetchSemanticsNodes()
        val archiveNodes = composeRule.onAllNodes(hasTestTag("action_archive_${topInboxThread.threadKey}")).fetchSemanticsNodes()
        if (dialogNodes.isEmpty() || dialogNodes.first().boundsInRoot.width <= 0f) {
            throw AssertionError(
                "Dialog state debug: topKey=${topInboxThread.threadKey}, activeKey=${vm.uiState.value.activeConversationKey}, " +
                    "dialogNodes=${dialogNodes.size}, bounds=${dialogNodes.firstOrNull()?.boundsInRoot}, " +
                    "globalBounds=${dialogNodes.firstOrNull()?.boundsInWindow}, archiveNodes=${archiveNodes.size}"
            )
        }
        composeRule.onNodeWithTag("conversation_actions_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("action_archive_${topInboxThread.threadKey}").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.archivedThreadsCount == beforeArchived + 2
        }

        assertEquals(beforeArchived + 2, vm.uiState.value.archivedThreadsCount)
        assertFalse(vm.uiState.value.conversationThreads.any { it.threadKey == topInboxThread.threadKey })
    }
}
