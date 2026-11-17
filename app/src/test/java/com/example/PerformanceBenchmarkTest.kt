package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ContactFilterRepository
import com.example.data.SmsClassifier
import com.example.data.local.AppDatabase
import com.example.data.local.ContactFilterSelectionEntity
import com.example.model.ContactFilterMode
import com.example.model.ContactInfo
import com.example.model.CustomFilterRule
import com.example.model.MessageFolderFilter
import com.example.model.MessageProtocol
import com.example.model.MessagingHelper
import com.example.model.RcsDeliveryStatus
import com.example.model.SmsMessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceBenchmarkTest {

    private fun generateRealisticMessages(count: Int, now: Long): List<SmsMessageItem> {
        val list = ArrayList<SmsMessageItem>(count)
        for (i in 0 until count) {
            val threadIdx = i % 250
            val address = "+1555%07d".format(threadIdx)
            val body = when (i % 6) {
                0 -> "Hey! Are we still on for lunch today at 12:30? Message #$i"
                1 -> "G-${100000 + (i % 899999)} is your Google verification code."
                2 -> "Flash Sale! Flat 50% discount with promo code SAVE50 at http://shop.example.com/$i"
                3 -> "Duplicate delivery notice: Package #9912 arrives tomorrow"
                4 -> "URGENT ALERT: Account suspended. Verify immediately at http://bit.ly/check"
                else -> "Monthly bank alert statement ready for review ($threadIdx)"
            }
            list.add(
                SmsMessageItem(
                    id = "bench_$i",
                    address = if (i % 6 == 1) "Google" else if (i % 6 == 2) "SHOPAD" else address,
                    body = body,
                    date = now - ((count - i) * 60_000L),
                    type = if (i % 4 == 0) 2 else 1,
                    protocol = if (i % 5 == 0) MessageProtocol.RCS_CHAT else MessageProtocol.SMS,
                    threadId = (threadIdx + 1).toLong(),
                    rcsDeliveryStatus = if (i % 5 == 0) RcsDeliveryStatus.READ else null,
                    isRcsChat = i % 5 == 0,
                    contactName = if (i % 3 == 0) "Contact $threadIdx" else null,
                    contactId = if (i % 3 == 0) "cid_$threadIdx" else null,
                    isRead = i % 7 != 0,
                    isArchived = threadIdx in 200..224
                )
            )
        }
        return list
    }

    @Test
    fun benchmark_classifierAndMessagingThreads_5000Messages() {
        val now = 1_750_000_000_000L
        val messages = generateRealisticMessages(5000, now)
        val rules = listOf(
            CustomFilterRule(
                id = "r1",
                name = "Bank Alerts > 1d",
                senderFilter = "1555",
                keywords = "statement, alert",
                matchAnyKeyword = true,
                olderThanDays = 1,
                isEnabled = true
            ),
            CustomFilterRule(
                id = "r2",
                name = "Contact 3",
                contactFilter = "Contact 3, +15550000003",
                keywords = "lunch",
                matchAnyKeyword = true,
                isEnabled = true
            )
        )
        val selectedTokens = setOf("cid_0", "cid_3", "5550000003", "5550000006", "contact 0", "contact 3")
        val selectedThreads = setOf(1L, 4L, 7L)

        // Warmup
        repeat(2) {
            val classified = SmsClassifier.classifyMessages(
                rawMessages = messages,
                activeRules = rules,
                filterByContactEnabled = true,
                selectedContactTokens = selectedTokens,
                contactFilterMode = ContactFilterMode.TARGET_CONTACTS,
                selectedContactThreadIds = selectedThreads
            )
            SmsClassifier.computeSummary(classified)
            SmsClassifier.extractDuplicateThreadClusters(classified)
            MessagingHelper.buildConversationThreads(
                messages = classified,
                searchQuery = "lunch",
                folderFilter = MessageFolderFilter.INBOX
            )
        }

        // Measure Classification Pipeline (5 runs)
        var classifyTotalNs = 0L
        var lastCleanCount = 0
        repeat(5) {
            val start = System.nanoTime()
            val classified = SmsClassifier.classifyMessages(
                rawMessages = messages,
                activeRules = rules,
                filterByContactEnabled = true,
                selectedContactTokens = selectedTokens,
                contactFilterMode = ContactFilterMode.TARGET_CONTACTS,
                selectedContactThreadIds = selectedThreads
            )
            val summary = SmsClassifier.computeSummary(classified)
            SmsClassifier.extractDuplicateThreadClusters(classified)
            classifyTotalNs += (System.nanoTime() - start)
            lastCleanCount = summary.allCleanableCount
        }

        val preClassified = SmsClassifier.classifyMessages(rawMessages = messages, activeRules = rules)

        // Measure Conversation Thread Builder + Search (5 runs)
        var threadBuildTotalNs = 0L
        var lastThreadCount = 0
        repeat(5) {
            val start = System.nanoTime()
            val inboxAll = MessagingHelper.buildConversationThreads(
                messages = preClassified,
                searchQuery = "",
                folderFilter = MessageFolderFilter.INBOX
            )
            val searched = MessagingHelper.buildConversationThreads(
                messages = preClassified,
                searchQuery = "Contact 12",
                folderFilter = MessageFolderFilter.INBOX
            )
            threadBuildTotalNs += (System.nanoTime() - start)
            lastThreadCount = inboxAll.size + searched.size
        }

        val classifyAvgMs = classifyTotalNs / 5_000_000.0
        val threadAvgMs = threadBuildTotalNs / 5_000_000.0
        println("BENCHMARK_RESULT classifier_5k_avg_ms=${"%.2f".format(classifyAvgMs)} clean_candidates=$lastCleanCount")
        println("BENCHMARK_RESULT threads_5k_avg_ms=${"%.2f".format(threadAvgMs)} thread_sum=$lastThreadCount")

        assertTrue(lastCleanCount > 0)
        assertTrue(lastThreadCount > 0)
    }

    @Test
    fun benchmark_roomContactFilterQueryFlow_2500Messages() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ContactFilterRepository(dao = db.cleanSmsDao(), ioDispatcher = Dispatchers.IO)
            val messages = generateRealisticMessages(2500, 1_750_000_000_000L)
            val contact = ContactInfo(
                id = "cid_0",
                displayName = "Contact 0",
                phoneNumbers = listOf("+15550000000"),
                isFromDeviceContacts = true,
                threadId = 1L
            )

            val startSync = System.nanoTime()
            repo.syncInboxMessages(messages)
            repo.setScannerFilterEnabled(true)
            repo.replaceSelectedContacts(ContactFilterSelectionEntity.SCOPE_SCANNER, listOf(contact))
            val entries = repo.observeScannerContactEntriesFlow().first { it.isNotEmpty() }
            val scope = repo.observeScannerFilterScopeFlow().first { it.selectedThreadIds.isNotEmpty() }
            val elapsedMs = (System.nanoTime() - startSync) / 1_000_000.0

            println("BENCHMARK_RESULT room_2500_sync_and_flow_ms=${"%.2f".format(elapsedMs)} entries=${entries.size} threads=${scope.selectedThreadIds.size}")
            assertTrue(entries.isNotEmpty())
            assertTrue(scope.selectedThreadIds.contains(1L))
        } finally {
            db.close()
        }
    }
}
