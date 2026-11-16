package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ContactFilterRepository
import com.example.data.local.AppDatabase
import com.example.data.local.ContactFilterSelectionEntity
import com.example.data.local.DeletedTestSnapshotEntity
import com.example.data.local.InboxMessageCacheEntity
import com.example.data.local.QuarantineEntity
import com.example.model.ContactFilterMode
import com.example.model.ContactInfo
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import com.example.ui.CleanSmsViewModel
import com.example.ui.screens.VaultRestoreScreen
import com.example.ui.theme.CleanSMSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
class BackgroundContactFlowAndBatchRestoreTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var inMemoryDb: AppDatabase
    private lateinit var contactFilterRepo: ContactFilterRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        inMemoryDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        contactFilterRepo = ContactFilterRepository(
            dao = inMemoryDb.cleanSmsDao(),
            ioDispatcher = Dispatchers.IO
        )
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
    }

    @Test
    fun roomContactFilterFlow_aggregatesAndFilters2000MessagesAndVaultItemsOnBackgroundDispatcher() = runBlocking {
        val largeMessages = ArrayList<SmsMessageItem>(2000)
        for (i in 0 until 2000) {
            val group = i % 4
            val (address, name, cid, thread) = when (group) {
                0 -> listOf("+15550000001", "Alice", "c_alice", "101")
                1 -> listOf("+15550000002", "Bob", "c_bob", "202")
                2 -> listOf("+15550000003", "Charlie", "c_charlie", "303")
                else -> listOf("+15550000004", "Dana", "c_dana", "404")
            }
            largeMessages.add(
                SmsMessageItem(
                    id = "bulk_$i",
                    address = address,
                    contactName = name,
                    contactId = cid,
                    body = "Message payload #$i",
                    date = 1_700_000_000_000L + i,
                    threadId = thread.toLong()
                )
            )
        }

        // Sync 2,000 messages into indexed Room inbox_message_cache
        contactFilterRepo.syncInboxMessages(largeMessages)

        // Room SQLite GROUP BY + Flow returns all 4 aggregated contacts with 500 msgs each
        val allEntries = contactFilterRepo.observeScannerContactEntriesFlow().first { it.size == 4 }
        assertEquals(4, allEntries.size)
        assertTrue(allEntries.all { it.messageCount == 500 })

        // Room SQLite LIKE query filters down asynchronously via Coroutine Flow
        contactFilterRepo.setScannerSearchQuery("Bob")
        val bobOnly = contactFilterRepo.observeScannerContactEntriesFlow().first {
            it.size == 1 && it.first().contact.displayName == "Bob"
        }
        assertEquals(500, bobOnly.first().messageCount)
        assertEquals(setOf(202L), bobOnly.first().threadIds)

        // Check Alice via Room contact_filter_selections & verify SQLite INNER JOIN resolves thread 101
        val aliceContact = ContactInfo(
            id = "c_alice",
            displayName = "Alice",
            phoneNumbers = listOf("+15550000001"),
            isFromDeviceContacts = true,
            threadId = 101L
        )
        contactFilterRepo.setScannerSearchQuery("")
        contactFilterRepo.setScannerFilterEnabled(true)
        contactFilterRepo.setScannerFilterMode(ContactFilterMode.TARGET_CONTACTS)
        contactFilterRepo.replaceSelectedContacts(
            scope = ContactFilterSelectionEntity.SCOPE_SCANNER,
            contacts = listOf(aliceContact)
        )

        val scope = contactFilterRepo.observeScannerFilterScopeFlow().first {
            it.isActive && 101L in it.selectedThreadIds
        }
        assertTrue(101L in scope.selectedThreadIds)
        assertFalse(202L in scope.selectedThreadIds)

        // Vault Room @Query Flow filters Quarantine & Recently Deleted via SQLite EXISTS subquery
        val dao = inMemoryDb.cleanSmsDao()
        dao.insertQuarantined(
            listOf(
                QuarantineEntity.fromSmsMessageItem(largeMessages[0].copy(category = SmsCategory.DUPLICATE)),
                QuarantineEntity.fromSmsMessageItem(largeMessages[1].copy(category = SmsCategory.PROMOTION))
            )
        )
        dao.insertDeletedTestSnapshots(
            listOf(
                DeletedTestSnapshotEntity.fromSmsMessageItem(largeMessages[4].copy(category = SmsCategory.SPAM)),
                DeletedTestSnapshotEntity.fromSmsMessageItem(largeMessages[5].copy(category = SmsCategory.SPAM))
            )
        )

        contactFilterRepo.setVaultFilterEnabled(true)
        contactFilterRepo.replaceSelectedContacts(
            scope = ContactFilterSelectionEntity.SCOPE_VAULT,
            contacts = listOf(aliceContact)
        )

        val filteredQuarantine = contactFilterRepo.observeFilteredQuarantinedFlow().first {
            it.size == 1 && it.first().id == "bulk_0"
        }
        assertEquals(listOf("bulk_0"), filteredQuarantine.map { it.id })

        val filteredDeleted = contactFilterRepo.observeFilteredDeletedTestFlow().first {
            it.size == 1 && it.first().id == "bulk_4"
        }
        assertEquals(listOf("bulk_4"), filteredDeleted.map { it.id })

        // Sanity check InboxMessageCacheEntity count
        assertEquals(2000, dao.observeInboxCacheCountFlow().first())
    }

    @Test
    fun batchRestoration_movesMultipleSelectedMessagesFromRecentlyDeletedToPrimaryInbox() {
        val dao = AppDatabase.getInstance(app).cleanSmsDao()
        runBlocking {
            dao.clearInboxCache()
            dao.clearAllQuarantine()
            dao.clearAllDeletedTestSnapshots()
            dao.clearFilterSelections(ContactFilterSelectionEntity.SCOPE_SCANNER)
            dao.clearFilterSelections(ContactFilterSelectionEntity.SCOPE_VAULT)

            val now = System.currentTimeMillis()
            dao.insertDeletedTestSnapshots(
                listOf(
                    DeletedTestSnapshotEntity.fromSmsMessageItem(
                        SmsMessageItem(
                            id = "del_1",
                            address = "+15551110001",
                            contactName = "Alice",
                            contactId = "c_alice",
                            body = "Flight itinerary confirmation #1",
                            date = now - 3000L,
                            threadId = 11L,
                            category = SmsCategory.PROMOTION
                        )
                    ),
                    DeletedTestSnapshotEntity.fromSmsMessageItem(
                        SmsMessageItem(
                            id = "del_2",
                            address = "+15552220002",
                            contactName = "Bob",
                            contactId = "c_bob",
                            body = "Parcel pickup code 4490",
                            date = now - 2000L,
                            threadId = 22L,
                            category = SmsCategory.OTP
                        )
                    ),
                    DeletedTestSnapshotEntity.fromSmsMessageItem(
                        SmsMessageItem(
                            id = "del_3",
                            address = "+15553330003",
                            contactName = "Charlie",
                            contactId = "c_charlie",
                            body = "Unwanted spam link kept in trash",
                            date = now - 1000L,
                            threadId = 33L,
                            category = SmsCategory.SPAM
                        )
                    )
                )
            )
        }

        val vm = CleanSmsViewModel(app)
        composeRule.setContent {
            val state by vm.uiState.collectAsStateWithLifecycle()
            CleanSMSTheme {
                VaultRestoreScreen(uiState = state, viewModel = vm)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.deletedTestMessages.size == 3 && !vm.uiState.value.isScanning
        }

        // Switch to Recently Deleted tab
        composeRule.onNodeWithTag("vault_tab_deleted_test").performClick()
        composeRule.waitForIdle()

        // Multi-select del_1 and del_2 via their checkboxes (leave del_3 unchecked)
        composeRule.onNodeWithTag("vault_deleted_list").performScrollToKey("del_1")
        composeRule.onNodeWithTag("checkbox_del_1").performClick()
        composeRule.onNodeWithTag("vault_deleted_list").performScrollToKey("del_2")
        composeRule.onNodeWithTag("checkbox_del_2").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.deletedTestMessages.count { it.isSelected } == 2
        }

        // Verify Batch Restore CTA displays exact count and executes batch restoration to Primary Inbox
        composeRule.onNodeWithTag("restore_deleted_test_button").assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            !vm.uiState.value.isBatchRestoring &&
                vm.uiState.value.deletedTestMessages.size == 1 &&
                vm.uiState.value.allMessages.any { it.id == "del_1" } &&
                vm.uiState.value.allMessages.any { it.id == "del_2" }
        }

        val stateAfterRestore = vm.uiState.value
        assertEquals(listOf("del_3"), stateAfterRestore.deletedTestMessages.map { it.id })
        assertTrue(stateAfterRestore.allMessages.any { it.id == "del_1" })
        assertTrue(stateAfterRestore.allMessages.any { it.id == "del_2" })
        assertFalse(stateAfterRestore.allMessages.any { it.id == "del_3" })

        // Verify restored items are also present in Room inbox_message_cache
        runBlocking {
            val cachedIds = dao.observeAllInboxCacheFlow().first().mapTo(HashSet()) { it.id }
            assertTrue("del_1" in cachedIds)
            assertTrue("del_2" in cachedIds)
            assertTrue(dao.getAllDeletedTestSnapshots().map { it.id } == listOf("del_3"))
        }

        // Now test purging the remaining selected deleted message permanently
        composeRule.onNodeWithTag("vault_deleted_list").performScrollToKey("del_3")
        composeRule.onNodeWithTag("checkbox_del_3").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.deletedTestMessages.any { it.id == "del_3" && it.isSelected }
        }
        composeRule.onNodeWithTag("purge_selected_deleted_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.deletedTestMessages.isEmpty()
        }
        assertTrue(vm.uiState.value.deletedTestMessages.isEmpty())
    }
}
