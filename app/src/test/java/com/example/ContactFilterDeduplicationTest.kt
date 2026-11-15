package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SmsClassifier
import com.example.data.local.AppDatabase
import com.example.model.ContactFilterMode
import com.example.model.ContactInfo
import com.example.model.DuplicateKeepStrategy
import com.example.model.MessageProtocol
import com.example.model.PhoneNormalizer
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import com.example.ui.CleanSmsViewModel
import com.example.update.GitHubAssetDto
import com.example.update.GitHubReleaseDto
import com.example.update.GitHubUpdateRepository
import com.example.update.SemanticVersion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContactFilterDeduplicationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun waitUntil(timeoutMs: Long = 5000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for condition after ${timeoutMs}ms")
            }
            ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
    }

    @Test
    fun filterByContact_withDeduplicateOnly_preservesNormalMessagesAndMasterCopies() {
        val baseTime = System.currentTimeMillis() - 3600_000L

        val messages = listOf(
            // Selected contact Sarah Miller — Thread 101 (SMS): 2 unique normal texts + 3 copies of same text
            SmsMessageItem(
                id = "sarah_norm_1",
                address = "+1 (555) 234-5678",
                contactName = "Sarah Miller",
                contactId = "c_sarah",
                body = "Hey, are we still meeting at 6pm?",
                date = baseTime + 1000L,
                threadId = 101L
            ),
            SmsMessageItem(
                id = "sarah_norm_2",
                address = "+1 (555) 234-5678",
                contactName = "Sarah Miller",
                contactId = "c_sarah",
                body = "I brought the documents you asked for.",
                date = baseTime + 2000L,
                threadId = 101L
            ),
            SmsMessageItem(
                id = "sarah_dup_master",
                address = "+1 (555) 234-5678",
                contactName = "Sarah Miller",
                contactId = "c_sarah",
                body = "On my way now!",
                date = baseTime + 3000L,
                threadId = 101L
            ),
            SmsMessageItem(
                id = "sarah_dup_extra_1",
                address = "+1 (555) 234-5678",
                contactName = "Sarah Miller",
                contactId = "c_sarah",
                body = "On my way now!",
                date = baseTime + 4000L,
                threadId = 101L
            ),
            SmsMessageItem(
                id = "sarah_dup_extra_2",
                address = "+1 (555) 234-5678",
                contactName = "Sarah Miller",
                contactId = "c_sarah",
                body = "On my way now!",
                date = baseTime + 5000L,
                threadId = 101L
            ),
            // Selected contact Sarah Miller — Thread 102 (second RCS thread): 1 normal + 2 duplicate copies
            SmsMessageItem(
                id = "sarah_rcs_norm",
                address = "+15552345678",
                contactName = "Sarah Miller",
                contactId = "c_sarah",
                body = "Flight landed safely.",
                date = baseTime + 6000L,
                threadId = 102L,
                protocol = MessageProtocol.RCS_CHAT,
                isRcsChat = true
            ),
            SmsMessageItem(
                id = "sarah_rcs_dup_master",
                address = "+15552345678",
                contactName = "Sarah Miller",
                contactId = "c_sarah",
                body = "Picking up coffee first",
                date = baseTime + 7000L,
                threadId = 102L,
                protocol = MessageProtocol.RCS_CHAT,
                isRcsChat = true
            ),
            SmsMessageItem(
                id = "sarah_rcs_dup_extra",
                address = "+15552345678",
                contactName = "Sarah Miller",
                contactId = "c_sarah",
                body = "Picking up coffee first",
                date = baseTime + 8000L,
                threadId = 102L,
                protocol = MessageProtocol.RCS_CHAT,
                isRcsChat = true
            ),
            // Unselected contact Alex Mercer — Thread 201: contains duplicates that MUST NOT be flagged
            SmsMessageItem(
                id = "alex_norm",
                address = "+1 (555) 999-1111",
                contactName = "Alex Mercer",
                contactId = "c_alex",
                body = "Dinner tonight?",
                date = baseTime + 9000L,
                threadId = 201L
            ),
            SmsMessageItem(
                id = "alex_dup_1",
                address = "+1 (555) 999-1111",
                contactName = "Alex Mercer",
                contactId = "c_alex",
                body = "Ping!",
                date = baseTime + 10000L,
                threadId = 201L
            ),
            SmsMessageItem(
                id = "alex_dup_2",
                address = "+1 (555) 999-1111",
                contactName = "Alex Mercer",
                contactId = "c_alex",
                body = "Ping!",
                date = baseTime + 11000L,
                threadId = 201L
            )
        )

        val selectedTokens = setOf("c_sarah", "sarah miller") +
            PhoneNormalizer.extractMatchKeys("+1 (555) 234-5678")

        val classified = SmsClassifier.classifyMessages(
            rawMessages = messages,
            activeRules = emptyList(),
            filterDuplicates = true,
            filterOtps = false,
            onlyExpiredOtps = true,
            filterPromotions = false,
            filterSpam = false,
            duplicateKeepStrategy = DuplicateKeepStrategy.KEEP_OLDEST,
            filterByContactEnabled = true,
            selectedContactTokens = selectedTokens,
            contactFilterMode = ContactFilterMode.TARGET_CONTACTS
        )

        val byId = classified.associateBy { it.id }

        // Only the 3 duplicate extras for Sarah across her two threads should be clean candidates
        assertTrue(byId.getValue("sarah_dup_extra_1").isCleanCandidate)
        assertEquals(SmsCategory.DUPLICATE, byId.getValue("sarah_dup_extra_1").category)
        assertTrue(byId.getValue("sarah_dup_extra_2").isCleanCandidate)
        assertEquals(SmsCategory.DUPLICATE, byId.getValue("sarah_dup_extra_2").category)
        assertTrue(byId.getValue("sarah_rcs_dup_extra").isCleanCandidate)
        assertEquals(SmsCategory.DUPLICATE, byId.getValue("sarah_rcs_dup_extra").category)

        // Sarah's normal messages & master copies MUST NOT be flagged or selected for deletion
        assertFalse(byId.getValue("sarah_norm_1").isCleanCandidate)
        assertFalse(byId.getValue("sarah_norm_1").isSelected)
        assertEquals(SmsCategory.NORMAL, byId.getValue("sarah_norm_1").category)

        assertFalse(byId.getValue("sarah_norm_2").isCleanCandidate)
        assertFalse(byId.getValue("sarah_dup_master").isCleanCandidate)
        assertFalse(byId.getValue("sarah_rcs_norm").isCleanCandidate)
        assertFalse(byId.getValue("sarah_rcs_dup_master").isCleanCandidate)

        // Alex's duplicates MUST be excluded since Alex is not in the selected contact filter
        assertFalse(byId.getValue("alex_norm").isCleanCandidate)
        assertFalse(byId.getValue("alex_dup_1").isCleanCandidate)
        assertFalse(byId.getValue("alex_dup_2").isCleanCandidate)

        // Duplicate thread clusters must ONLY include Sarah's two threads (never Alex's)
        val clusters = SmsClassifier.extractDuplicateThreadClusters(classified)
        assertEquals(2, clusters.size)
        assertTrue(clusters.all { it.threadId == 101L || it.threadId == 102L })
        assertEquals(3, clusters.sumOf { it.duplicateCount })
    }

    @Test
    fun filterByContact_multipleContacts_appliesEnabledRulesToAllThreadsOfSelectedContacts() {
        val oldOtpTime = System.currentTimeMillis() - (60 * 60 * 1000L) // 1 hour ago
        val now = System.currentTimeMillis()

        val messages = listOf(
            // Contact 1: Mom (thread 11) — normal message + duplicate copy
            SmsMessageItem(id = "mom_norm", address = "5551112222", contactName = "Mom", body = "Call me later", date = now - 5000, threadId = 11L),
            SmsMessageItem(id = "mom_dup_1", address = "5551112222", contactName = "Mom", body = "Got home!", date = now - 4000, threadId = 11L),
            SmsMessageItem(id = "mom_dup_2", address = "5551112222", contactName = "Mom", body = "Got home!", date = now - 3000, threadId = 11L),
            // Contact 2: BankBot (thread 22) — expired OTP + non-OTP balance inquiry
            SmsMessageItem(id = "bank_otp", address = "BankBot", contactName = "BankBot", body = "987654 is your verification code", date = oldOtpTime, threadId = 22L),
            SmsMessageItem(id = "bank_norm", address = "BankBot", contactName = "BankBot", body = "Monthly statement is ready", date = now - 2000, threadId = 22L),
            // Contact 3: Unselected coworker (thread 33) — duplicate + expired OTP (must NOT be flagged)
            SmsMessageItem(id = "coworker_dup_1", address = "5554443333", contactName = "Taylor", body = "Meeting moved", date = now - 2000, threadId = 33L),
            SmsMessageItem(id = "coworker_dup_2", address = "5554443333", contactName = "Taylor", body = "Meeting moved", date = now - 1000, threadId = 33L),
            SmsMessageItem(id = "coworker_otp", address = "5554443333", contactName = "Taylor", body = "112233 is your verification code", date = oldOtpTime, threadId = 33L)
        )

        val selectedTokens = setOf("mom", "bankbot") +
            PhoneNormalizer.extractMatchKeys("5551112222")

        val classified = SmsClassifier.classifyMessages(
            rawMessages = messages,
            activeRules = emptyList(),
            filterDuplicates = true,
            filterOtps = true,
            onlyExpiredOtps = true,
            filterPromotions = false,
            filterSpam = false,
            filterByContactEnabled = true,
            selectedContactTokens = selectedTokens,
            contactFilterMode = ContactFilterMode.TARGET_CONTACTS
        )

        val byId = classified.associateBy { it.id }
        assertEquals(SmsCategory.DUPLICATE, byId.getValue("mom_dup_2").category)
        assertEquals(SmsCategory.NORMAL, byId.getValue("mom_norm").category)
        assertEquals(SmsCategory.NORMAL, byId.getValue("mom_dup_1").category)

        assertEquals(SmsCategory.OTP, byId.getValue("bank_otp").category)
        assertEquals(SmsCategory.NORMAL, byId.getValue("bank_norm").category)

        assertEquals(SmsCategory.NORMAL, byId.getValue("coworker_dup_1").category)
        assertEquals(SmsCategory.NORMAL, byId.getValue("coworker_dup_2").category)
        assertEquals(SmsCategory.NORMAL, byId.getValue("coworker_otp").category)
    }

    @Test
    fun viewModel_filterByContactWithDeduplicate_quarantinesOnlyDuplicatesAndKeepsContactThread() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = AppDatabase.getInstance(app).cleanSmsDao()
        runBlocking {
            dao.clearAllQuarantine()
            dao.clearAllDeletedTestSnapshots()
        }

        val vm = CleanSmsViewModel(app)
        waitUntil { vm.uiState.value.allMessages.isNotEmpty() }

        // Configure rules: ONLY De-duplicate ON + Filter By Contact targeting Sarah Miller
        vm.toggleOtpFilter(false)
        vm.togglePromotionFilter(false)
        vm.toggleSpamFilter(false)
        vm.toggleDuplicateFilter(true)

        val sarahContact = vm.uiState.value.availableContacts.firstOrNull {
            it.displayName.contains("Sarah", ignoreCase = true)
        } ?: ContactInfo(
            id = "sarah_test",
            displayName = "Sarah Miller",
            phoneNumbers = listOf("+15552345678")
        )

        val beforeSarahMessages = vm.uiState.value.allMessages.filter {
            it.contactName?.contains("Sarah", ignoreCase = true) == true ||
                PhoneNormalizer.primaryKey(it.address) in sarahContact.normalizedTokens
        }
        assertTrue("Demo dataset must contain multiple Sarah messages", beforeSarahMessages.size >= 2)

        vm.setContactFilterMode(ContactFilterMode.TARGET_CONTACTS)
        vm.setSelectedContacts(listOf(sarahContact))

        waitUntil {
            val sarahClassified = vm.uiState.value.classifiedMessages.filter {
                it.contactName?.contains("Sarah", ignoreCase = true) == true ||
                    PhoneNormalizer.primaryKey(it.address) in sarahContact.normalizedTokens
            }
            // Wait until reclassification reflects contact-scoped duplicate-only output:
            // non-duplicate messages for Sarah remain NORMAL while duplicate copies are DUPLICATE
            sarahClassified.any { it.category == SmsCategory.NORMAL && !it.isCleanCandidate } &&
                vm.uiState.value.summary.customRuleCount == 0
        }

        val sarahAfterFilter = vm.uiState.value.classifiedMessages.filter {
            it.contactName?.contains("Sarah", ignoreCase = true) == true ||
                PhoneNormalizer.primaryKey(it.address) in sarahContact.normalizedTokens
        }
        val cleanableSarah = sarahAfterFilter.filter { it.isCleanCandidate }
        assertTrue(
            "Cleanable messages for Sarah must only be duplicates, preserving normal/master messages",
            cleanableSarah.size < beforeSarahMessages.size
        )
        assertTrue(cleanableSarah.all { it.category == SmsCategory.DUPLICATE })
    }

    @Test
    fun semanticVersion_v161Prerelease_triggersInAppUpdateFromV160() {
        val v160 = SemanticVersion.parse("1.6.0-pre.1")!!
        val v161 = SemanticVersion.parse("v1.6.1-pre.1")!!
        assertTrue(v161 > v160)
        assertEquals(10601, v161.toVersionCode())

        val releases = listOf(
            GitHubReleaseDto(
                tagName = "v1.6.0-pre.1",
                prerelease = true
            ),
            GitHubReleaseDto(
                tagName = "v1.6.1-pre.1",
                name = "CleanSMS v1.6.1-pre.1",
                prerelease = true,
                assets = listOf(
                    GitHubAssetDto(
                        name = "CleanSMS-v1.6.1-pre.1-nonprod-local-test.apk",
                        browserDownloadUrl = "https://github.com/HussainQ/SMS-Cleaner-Tool/releases/download/v1.6.1-pre.1/CleanSMS-v1.6.1-pre.1-nonprod-local-test.apk",
                        size = 22_000_000L
                    )
                )
            )
        )

        val updateFrom160 = GitHubUpdateRepository.evaluateReleases(
            repoSlug = "HussainQ/SMS-Cleaner-Tool",
            releases = releases,
            installedVersionName = "1.6.0-pre.1",
            installedVersionCode = 10600,
            includePrereleases = true
        )
        assertTrue(updateFrom160.isUpdateAvailable)
        assertEquals("v1.6.1-pre.1", updateFrom160.latestRelease?.tagName)

        val checkOn161 = GitHubUpdateRepository.evaluateReleases(
            repoSlug = "HussainQ/SMS-Cleaner-Tool",
            releases = releases,
            installedVersionName = "1.6.1-pre.1",
            installedVersionCode = 10601,
            includePrereleases = true
        )
        assertFalse(checkOn161.isUpdateAvailable)
    }
}
