package com.example

import android.Manifest
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.data.SmsClassifier
import com.example.data.local.AppDatabase
import com.example.model.ContactFilterMode
import com.example.model.ContactInfo
import com.example.model.CustomFilterRule
import com.example.model.PhoneNormalizer
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import com.example.telephony.TelephonyManagerHelper
import com.example.ui.CleanSmsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V160FeaturesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun contactFilter_targetAndProtectModes_classifyCorrectly() {
        val now = System.currentTimeMillis()
        val sarahMsg = SmsMessageItem(
            id = "c1",
            address = "+1 (555) 234-5678",
            contactName = "Sarah Miller",
            contactId = "contact_sarah",
            body = "Flash sale offer! 50% discount at http://shop.com/deal",
            date = now
        )
        val promoMsg = SmsMessageItem(
            id = "c2",
            address = "AD-SHOP",
            body = "Flash sale offer! 50% discount at http://shop.com/deal",
            date = now
        )

        val selectedTokens = setOf("contact_sarah", "sarah miller") + PhoneNormalizer.extractMatchKeys("+1 (555) 234-5678")

        // TARGET_CONTACTS mode: ONLY Sarah Miller's messages should be eligible
        val targeted = SmsClassifier.classifyMessages(
            rawMessages = listOf(sarahMsg, promoMsg),
            activeRules = emptyList(),
            filterPromotions = true,
            filterByContactEnabled = true,
            selectedContactTokens = selectedTokens,
            contactFilterMode = ContactFilterMode.TARGET_CONTACTS
        )
        assertTrue(targeted.first { it.id == "c1" }.isCleanCandidate)
        assertFalse(targeted.first { it.id == "c2" }.isCleanCandidate)
        assertEquals(SmsCategory.NORMAL, targeted.first { it.id == "c2" }.category)

        // PROTECT_CONTACTS mode: Sarah Miller is protected (VIP); AD-SHOP is flagged
        val protectedRes = SmsClassifier.classifyMessages(
            rawMessages = listOf(sarahMsg, promoMsg),
            activeRules = emptyList(),
            filterPromotions = true,
            filterByContactEnabled = true,
            selectedContactTokens = selectedTokens,
            contactFilterMode = ContactFilterMode.PROTECT_CONTACTS
        )
        assertFalse(protectedRes.first { it.id == "c1" }.isCleanCandidate)
        assertTrue(protectedRes.first { it.id == "c1" }.matchReason.contains("Protected"))
        assertTrue(protectedRes.first { it.id == "c2" }.isCleanCandidate)
        assertEquals(SmsCategory.PROMOTION, protectedRes.first { it.id == "c2" }.category)
    }

    @Test
    fun customRule_withContactFilter_matchesNormalizedPhoneAndName() {
        val now = System.currentTimeMillis()
        val msgByPhoneFormat = SmsMessageItem(
            id = "p1",
            address = "5558904321",
            contactName = "Jordan Lee",
            body = "Reminder about Tuesday appointment",
            date = now
        )
        val unrelated = SmsMessageItem(
            id = "p2",
            address = "+1 555 000 9999",
            contactName = "Mom",
            body = "Reminder about dinner",
            date = now
        )

        val contactRule = CustomFilterRule(
            id = "rule_contact_1",
            name = "Jordan Reminders",
            contactFilter = "Jordan Lee, +1 (555) 890-4321",
            keywords = "reminder",
            isEnabled = true
        )

        val classified = SmsClassifier.classifyMessages(
            rawMessages = listOf(msgByPhoneFormat, unrelated),
            activeRules = listOf(contactRule)
        )

        assertEquals(SmsCategory.CUSTOM_RULE, classified.first { it.id == "p1" }.category)
        assertTrue(classified.first { it.id == "p1" }.isCleanCandidate)
        assertEquals(SmsCategory.NORMAL, classified.first { it.id == "p2" }.category)
        assertFalse(classified.first { it.id == "p2" }.isCleanCandidate)
    }

    private fun waitUntil(timeoutMs: Long = 5000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for condition after ${timeoutMs}ms")
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
    }

    @Test
    fun editExistingCustomRule_updatesFieldsAndPreservesId() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = CleanSmsViewModel(app)

        val original = CustomFilterRule(
            id = "rule_edit_test",
            name = "Old Name",
            senderFilter = "Bank",
            contactFilter = "",
            keywords = "debit",
            olderThanDays = 10,
            isEnabled = true,
            createdAt = 123456789L
        )
        vm.saveCustomRule(original)
        waitUntil { vm.uiState.value.customRules.any { it.id == "rule_edit_test" } }

        val edited = original.copy(
            name = "Updated Rule Name",
            contactFilter = "Chase Bank",
            keywords = "debit, balance",
            olderThanDays = 45
        )
        vm.openEditRuleSheet(original)
        assertEquals("rule_edit_test", vm.uiState.value.editingRule?.id)

        vm.saveCustomRule(edited)
        waitUntil {
            vm.uiState.value.customRules.any { it.id == "rule_edit_test" && it.name == "Updated Rule Name" }
        }

        val stored = vm.uiState.value.customRules.first { it.id == "rule_edit_test" }
        assertEquals("Updated Rule Name", stored.name)
        assertEquals("Chase Bank", stored.contactFilter)
        assertEquals("debit, balance", stored.keywords)
        assertEquals(45, stored.olderThanDays)
        assertEquals(123456789L, stored.createdAt)
    }

    @Test
    fun testingRestoreDeletedToggle_savesAndRestoresDeletedMessages() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dao = AppDatabase.getInstance(app).cleanSmsDao()
        runBlocking {
            dao.clearAllQuarantine()
            dao.clearAllDeletedTestSnapshots()
            dao.clearInboxCache()
            dao.clearFilterSelections(com.example.data.local.ContactFilterSelectionEntity.SCOPE_SCANNER)
            dao.clearFilterSelections(com.example.data.local.ContactFilterSelectionEntity.SCOPE_VAULT)
        }

        val vm = CleanSmsViewModel(app)
        waitUntil { vm.uiState.value.classifiedMessages.any { it.isCleanCandidate && it.isSelected } }

        val candidate = vm.uiState.value.classifiedMessages.first { it.isCleanCandidate && it.isSelected }

        // Toggle ON -> snapshot should be saved on permanent delete
        vm.toggleTestingRestoreDeleted(true)
        assertTrue(vm.uiState.value.testingRestoreDeletedEnabled)

        vm.selectFilteredMessages(setOf(candidate.id), true)
        vm.promptPermanentDelete(fromQuarantine = false)
        assertTrue(vm.uiState.value.showPermanentDeleteDialog)
        vm.confirmPermanentDelete()

        waitUntil { vm.uiState.value.deletedTestMessages.any { it.id == candidate.id } }

        // Select snapshot and restore it back to inbox
        vm.selectAllDeletedTest(true)
        vm.restoreSelectedDeletedTestMessages()

        waitUntil {
            vm.uiState.value.deletedTestMessages.none { it.id == candidate.id } &&
                vm.uiState.value.allMessages.any { it.id == candidate.id }
        }
        assertTrue(vm.uiState.value.allMessages.any { it.id == candidate.id })

        // Toggle OFF verifies flag flips cleanly
        vm.toggleTestingRestoreDeleted(false)
        assertFalse(vm.uiState.value.testingRestoreDeletedEnabled)
    }

    @Test
    fun telephonyRoleHandback_capturesPreviousAppAndBuildsRestoreIntent() {
        val info = TelephonyManagerHelper.capturePreviousDefaultSmsAppIfNeeded(context)
        assertEquals("Google Messages", info.appName)
        assertEquals("com.google.android.apps.messaging", info.packageName)

        val restoreIntent = TelephonyManagerHelper.createRestoreDefaultSmsIntent(context, info.packageName)
        // Running on SDK 36 -> uses ACTION_MANAGE_DEFAULT_APPS_SETTINGS
        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, restoreIntent.action)
    }

    @Test
    fun performanceStressTest_classify5000Messages_completesFastAndComputesExactSummary() {
        val now = System.currentTimeMillis()
        val largeInbox = ArrayList<SmsMessageItem>(5000)
        for (i in 0 until 5000) {
            val item = when (i % 5) {
                0 -> SmsMessageItem(
                    id = "m_$i",
                    address = "AD-DEAL",
                    body = "Flash sale coupon! 50% discount http://shop.com/s/$i",
                    date = now - i * 1000L
                )
                1 -> SmsMessageItem(
                    id = "m_$i",
                    address = "Google",
                    body = "G-${100000 + i} is your verification code",
                    date = now - (3600_000L + i * 1000L)
                )
                2 -> SmsMessageItem(
                    id = "m_$i",
                    address = "+180000000",
                    body = "URGENT ALERT: Account suspended verify at http://bit.ly/x$i",
                    date = now - i * 1000L
                )
                3 -> SmsMessageItem(
                    id = "m_$i",
                    address = "Mom",
                    body = "Hey, dinner at 7pm #$i",
                    date = now - i * 1000L
                )
                else -> SmsMessageItem(
                    id = "m_$i",
                    address = "UBER",
                    body = "Your ride is arriving now", // Intentional duplicates across 1000 items
                    date = now - i * 1000L,
                    threadId = 99L
                )
            }
            largeInbox.add(item)
        }

        val startMs = System.currentTimeMillis()
        val classified = SmsClassifier.classifyMessages(
            rawMessages = largeInbox,
            activeRules = emptyList(),
            filterDuplicates = true,
            filterOtps = true,
            onlyExpiredOtps = true,
            filterPromotions = true,
            filterSpam = true
        )
        val summary = SmsClassifier.computeSummary(classified)
        val elapsedMs = System.currentTimeMillis() - startMs

        assertEquals(5000, classified.size)
        assertEquals(1000, summary.promoCount)
        assertEquals(1000, summary.otpCount)
        assertEquals(1000, summary.spamCount)
        assertEquals(999, summary.duplicateCount) // 1000 identical UBER messages -> 1 master + 999 duplicates
        assertEquals(3999, summary.allCleanableCount)
        assertTrue("Expected classification of 5000 messages under 2000ms, took ${elapsedMs}ms", elapsedMs < 2000L)
    }

    @Test
    fun manifestPermissionConstant_contactsAndDefaultSmsPermissionsPresent() {
        assertEquals("android.permission.READ_CONTACTS", Manifest.permission.READ_CONTACTS)
        assertEquals("android.permission.RECEIVE_WAP_PUSH", Manifest.permission.RECEIVE_WAP_PUSH)

        @Suppress("DEPRECATION")
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        )
        val requested = pkgInfo.requestedPermissions?.toSet() ?: emptySet()
        assertTrue("Missing READ_SMS", Manifest.permission.READ_SMS in requested)
        assertTrue("Missing WRITE_SMS", PERMISSION_WRITE_SMS in requested)
        assertTrue("Missing SEND_SMS", Manifest.permission.SEND_SMS in requested)
        assertTrue("Missing RECEIVE_SMS", Manifest.permission.RECEIVE_SMS in requested)
        assertTrue("Missing RECEIVE_MMS", Manifest.permission.RECEIVE_MMS in requested)
        assertTrue("Missing RECEIVE_WAP_PUSH", Manifest.permission.RECEIVE_WAP_PUSH in requested)
        assertTrue("Missing READ_CONTACTS", Manifest.permission.READ_CONTACTS in requested)
    }
}
