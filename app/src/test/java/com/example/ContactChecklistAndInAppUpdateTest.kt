package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import org.robolectric.shadows.ShadowLooper
import com.example.data.SmsClassifier
import com.example.model.ContactFilterMode
import com.example.model.ContactInfo
import com.example.model.ContactThreadHelper
import com.example.model.DuplicateKeepStrategy
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.components.AppUpdatesDialog
import com.example.ui.screens.VaultRestoreScreen
import com.example.ui.theme.CleanSMSTheme
import com.example.update.GitHubApkAsset
import com.example.update.GitHubReleaseInfo
import com.example.update.GitHubUpdateRepository
import com.example.update.InAppApkInstaller
import com.example.update.InAppDownloadState
import com.example.update.InAppUpdateInstallPhase
import com.example.update.SemanticVersion
import com.example.update.UpdateCheckResult
import com.example.update.UpdateCheckStatus
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContactChecklistAndInAppUpdateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun threadScopedDeduplication_onlyRemovesDuplicatesInCheckedContactThreads() {
        val alice = ContactInfo(
            id = "c_alice",
            displayName = "Alice",
            phoneNumbers = listOf("+15551110001"),
            isFromDeviceContacts = true,
            threadId = 101L
        )
        val bob = ContactInfo(
            id = "c_bob",
            displayName = "Bob",
            phoneNumbers = listOf("+15552220002"),
            isFromDeviceContacts = true,
            threadId = 202L
        )

        val messages = listOf(
            // Thread 101 (Alice): 1 unique + 1 duplicate (even with alias format sharing thread 101)
            SmsMessageItem(
                id = "a1",
                address = "+15551110001",
                contactName = "Alice",
                contactId = "c_alice",
                body = "Lunch at 1pm?",
                date = 3000L,
                threadId = 101L
            ),
            SmsMessageItem(
                id = "a2",
                address = "+15551110001",
                contactName = "Alice",
                contactId = "c_alice",
                body = "Lunch at 1pm?",
                date = 2000L,
                threadId = 101L
            ),
            SmsMessageItem(
                id = "a3",
                address = "+15551110001",
                contactName = "Alice",
                contactId = "c_alice",
                body = "Confirmed see you there",
                date = 1000L,
                threadId = 101L
            ),
            // Thread 202 (Bob - unchecked): 1 original + 1 duplicate MUST stay untouched
            SmsMessageItem(
                id = "b1",
                address = "+15552220002",
                contactName = "Bob",
                contactId = "c_bob",
                body = "Deploy build now",
                date = 3500L,
                threadId = 202L
            ),
            SmsMessageItem(
                id = "b2",
                address = "+15552220002",
                contactName = "Bob",
                contactId = "c_bob",
                body = "Deploy build now",
                date = 2500L,
                threadId = 202L
            )
        )

        val scope = ContactThreadHelper.buildScope(
            enabled = true,
            mode = ContactFilterMode.TARGET_CONTACTS,
            selectedContacts = listOf(alice),
            messages = messages
        )
        assertTrue(101L in scope.selectedThreadIds)
        assertFalse(202L in scope.selectedThreadIds)

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
            selectedContactTokens = scope.matchTokens,
            contactFilterMode = ContactFilterMode.TARGET_CONTACTS,
            selectedContactThreadIds = scope.selectedThreadIds
        )

        val byId = classified.associateBy { it.id }
        // Alice thread: keeps oldest a2 (KEEP_OLDEST), flags duplicate a1, keeps unique a3
        assertEquals(SmsCategory.DUPLICATE, byId.getValue("a1").category)
        assertTrue(byId.getValue("a1").isCleanCandidate)
        assertEquals(SmsCategory.NORMAL, byId.getValue("a2").category)
        assertFalse(byId.getValue("a2").isCleanCandidate)
        assertEquals(SmsCategory.NORMAL, byId.getValue("a3").category)
        assertFalse(byId.getValue("a3").isCleanCandidate)

        // Bob thread (unchecked): both copies remain NORMAL & untouched
        assertEquals(SmsCategory.NORMAL, byId.getValue("b1").category)
        assertFalse(byId.getValue("b1").isCleanCandidate)
        assertEquals(SmsCategory.NORMAL, byId.getValue("b2").category)
        assertFalse(byId.getValue("b2").isCleanCandidate)
    }

    @Test
    fun vaultContactEntries_and_filterByContact_groupReviewWorksCorrectly() {
        val aliceMsg1 = SmsMessageItem(
            id = "q_a1",
            address = "+15551110001",
            contactName = "Alice",
            contactId = "c_alice",
            body = "Duplicate promo 1",
            date = 1000L,
            threadId = 10L,
            category = SmsCategory.DUPLICATE,
            isQuarantined = true
        )
        val aliceMsg2 = SmsMessageItem(
            id = "q_a2",
            address = "+15551110001",
            contactName = "Alice",
            contactId = "c_alice",
            body = "Duplicate promo 2",
            date = 2000L,
            threadId = 10L,
            category = SmsCategory.DUPLICATE,
            isQuarantined = true
        )
        val bobMsg = SmsMessageItem(
            id = "q_b1",
            address = "+15552220002",
            contactName = "Bob",
            contactId = "c_bob",
            body = "Special offer today",
            date = 3000L,
            threadId = 20L,
            category = SmsCategory.PROMOTION,
            isQuarantined = true
        )

        val entries = ContactThreadHelper.buildVaultContactEntries(
            vaultMessages = listOf(aliceMsg1, aliceMsg2, bobMsg),
            availableContacts = emptyList()
        )
        assertEquals(2, entries.size)
        assertTrue(entries.first().contact.displayName.startsWith("Alice"))
        assertEquals(2, entries.first().messageCount)

        val aliceOnly = ContactThreadHelper.filterMessagesByContacts(
            messages = listOf(aliceMsg1, aliceMsg2, bobMsg),
            selectedContacts = listOf(entries.first().contact)
        )
        assertEquals(listOf("q_a1", "q_a2"), aliceOnly.map { it.id })
    }

    @Test
    fun scannerUi_contactChecklist_checksContactAndFiltersSelectedThreads() {
        val vm = CleanSmsViewModel(app)
        composeRule.setContent {
            CleanSMSTheme {
                CleanSmsApp(viewModel = vm)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.scannerContactEntries.isNotEmpty()
        }

        val entries = vm.uiState.value.scannerContactEntries
        assertTrue("Expected non-empty demo contact checklist entries", entries.isNotEmpty())
        val firstContact = entries.first().contact

        // Scroll outer ScannerScreen container to bring the checklist LazyColumn directly into viewport
        composeRule.onNodeWithTag("scanner_contact_filter_list").performScrollTo()
        composeRule.onNodeWithTag("scanner_contact_filter_list")
            .performScrollToNode(hasTestTag("scanner_contact_filter_checkbox_${firstContact.id}"))
        composeRule.onNodeWithTag("scanner_contact_filter_checkbox_${firstContact.id}").performClick()
        composeRule.waitForIdle()

        val stateAfterCheck = vm.uiState.value
        assertTrue(stateAfterCheck.filterByContactEnabled)
        assertTrue(stateAfterCheck.selectedContacts.any { it.id == firstContact.id })
        assertTrue(stateAfterCheck.contactFilterScope.isActive)
        composeRule.onNodeWithTag("scanner_contact_filter_scope_banner").assertExists()

        // Clear unchecks all
        composeRule.onNodeWithTag("scanner_contact_filter_clear_btn")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(vm.uiState.value.selectedContacts.isEmpty())
    }

    @Test
    fun vaultScreen_contactFilterAndGroupReview_filtersListAndSelectsGroup() {
        val vm = CleanSmsViewModel(app)
        composeRule.setContent {
            val state by vm.uiState.collectAsStateWithLifecycle()
            CleanSMSTheme {
                VaultRestoreScreen(uiState = state, viewModel = vm)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.allMessages.distinctBy { ContactThreadHelper.vaultGroupKey(it) }.size >= 2
        }

        // Select two messages from distinct senders and move them to Quarantine
        val distinctSenderMsgs = vm.uiState.value.allMessages
            .distinctBy { ContactThreadHelper.vaultGroupKey(it) }
            .take(2)
        assertTrue("Expected >= 2 distinct senders in demo inbox", distinctSenderMsgs.size == 2)

        distinctSenderMsgs.forEach { msg ->
            if (!msg.isSelected) {
                vm.toggleMessageSelection(msg.id)
            }
        }
        vm.quarantineSelectedMessages()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.vaultContactEntries.size >= 2
        }

        val firstVaultContact = vm.uiState.value.vaultContactEntries.first().contact

        // Verify Group by Contact starts true and toggles cleanly
        assertTrue(vm.uiState.value.vaultGroupByContact)
        composeRule.onNodeWithTag("vault_group_by_contact_chip").performClick()
        composeRule.waitForIdle()
        assertFalse(vm.uiState.value.vaultGroupByContact)
        composeRule.onNodeWithTag("vault_group_by_contact_chip").performClick()
        composeRule.waitForIdle()
        assertTrue(vm.uiState.value.vaultGroupByContact)

        // Expand Vault Contact Filter panel & check first contact
        composeRule.onNodeWithTag("vault_contact_filter_toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("vault_contact_filter_list")
            .performScrollToNode(hasTestTag("vault_contact_filter_checkbox_${firstVaultContact.id}"))
        composeRule.onNodeWithTag("vault_contact_filter_checkbox_${firstVaultContact.id}").performClick()
        composeRule.waitForIdle()

        assertTrue(vm.uiState.value.vaultFilterByContactEnabled)
        assertTrue(vm.uiState.value.vaultGroupByContact)

        // Collapse drawer so message list has generous space on compact test viewport
        composeRule.onNodeWithTag("vault_contact_filter_toggle").performClick()
        composeRule.waitForIdle()

        // Deselect all first so we specifically test the per-group Select Group button
        vm.selectAllQuarantined(false)
        composeRule.waitForIdle()

        val matchingVaultMsg = vm.uiState.value.quarantinedMessages.first {
            ContactThreadHelper.messageMatchesTokens(
                it,
                ContactThreadHelper.collectTokensForContacts(listOf(firstVaultContact))
            )
        }
        val groupKey = ContactThreadHelper.vaultGroupKey(matchingVaultMsg)
        composeRule.onNodeWithTag("vault_quarantine_list")
            .performScrollToNode(hasTestTag("vault_group_select_$groupKey"))
        composeRule.onNodeWithTag("vault_group_select_$groupKey").performClick()
        composeRule.waitForIdle()

        val updatedMatching = vm.uiState.value.quarantinedMessages.first { it.id == matchingVaultMsg.id }
        assertTrue(updatedMatching.isSelected)
    }

    @Test
    fun downloadApkUpdate_verifiesZipHeaderAndSha256AndRejectsInvalidPayloads() = runBlocking {
        val validApkBytes = ByteArray(1024) { index ->
            when (index) {
                0 -> 0x50.toByte() // 'P'
                1 -> 0x4B.toByte() // 'K'
                2 -> 0x03.toByte()
                3 -> 0x04.toByte()
                else -> (index % 127).toByte()
            }
        }
        val expectedSha = MessageDigest.getInstance("SHA-256")
            .digest(validApkBytes)
            .joinToString("") { "%02x".format(it) }

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/valid.apk") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/vnd.android.package-archive")
            exchange.sendResponseHeaders(200, validApkBytes.size.toLong())
            exchange.responseBody.use { it.write(validApkBytes) }
        }
        server.createContext("/bad.html") { exchange ->
            val html = "<html><body>Not an APK</body></html>".toByteArray()
            exchange.sendResponseHeaders(200, html.size.toLong())
            exchange.responseBody.use { it.write(html) }
        }
        server.start()

        try {
            val port = server.address.port
            val repo = GitHubUpdateRepository()
            val destValid = InAppApkInstaller.resolveApkDestination(app, "CleanSMS-1.6.1.apk")

            var lastProgress = -1L
            val okResult = repo.downloadApkUpdate(
                downloadUrl = "http://127.0.0.1:$port/valid.apk",
                destinationFile = destValid,
                expectedSha256 = expectedSha,
                onProgress = { downloaded, _ -> lastProgress = downloaded }
            )

            assertTrue("Expected valid APK download to succeed", okResult.isSuccess)
            val downloaded = okResult.getOrThrow()
            assertEquals(validApkBytes.size.toLong(), downloaded.bytesDownloaded)
            assertEquals(expectedSha, downloaded.computedSha256)
            assertTrue(downloaded.sha256Verified)
            assertEquals(validApkBytes.size.toLong(), lastProgress)
            assertTrue(File(downloaded.filePath).exists())

            val destWrongSha = InAppApkInstaller.resolveApkDestination(app, "CleanSMS-mismatch.apk")
            val mismatchResult = repo.downloadApkUpdate(
                downloadUrl = "http://127.0.0.1:$port/valid.apk",
                destinationFile = destWrongSha,
                expectedSha256 = "0".repeat(64)
            )
            assertTrue(mismatchResult.isFailure)
            assertTrue(mismatchResult.exceptionOrNull() is SecurityException)

            val destHtml = InAppApkInstaller.resolveApkDestination(app, "CleanSMS-bad.apk")
            val htmlResult = repo.downloadApkUpdate(
                downloadUrl = "http://127.0.0.1:$port/bad.html",
                destinationFile = destHtml,
                expectedSha256 = null
            )
            assertTrue(htmlResult.isFailure)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun inAppApkInstaller_cleanupAndPaths_workInPrivateCacheDir() {
        val oldFile = InAppApkInstaller.resolveApkDestination(app, "CleanSMS-1.5.0.apk")
        oldFile.writeText("old")
        val latestFile = InAppApkInstaller.resolveApkDestination(app, "CleanSMS-1.6.1.apk")
        latestFile.writeText("latest")

        InAppApkInstaller.cleanupStaleApks(app, keepFileName = latestFile.name)

        assertFalse(oldFile.exists())
        assertTrue(latestFile.exists())
        assertTrue(latestFile.parentFile?.name == "updates")
    }

    @Test
    fun appUpdatesDialog_showsInAppDownloadAndInstallActionsWithoutOpeningBrowser() {
        var downloadClicked = false
        var cancelClicked = false
        var installClicked = false
        var permissionClicked = false

        val fakeRelease = GitHubReleaseInfo(
            tagName = "v1.7.0",
            title = "CleanSMS 1.7.0",
            version = SemanticVersion(1, 7, 0),
            isPrerelease = false,
            publishedAt = "2026-11-15T12:00:00Z",
            htmlUrl = "https://github.com/HussainQ/SMS-Cleaner-Tool/releases/tag/v1.7.0",
            releaseNotes = "- New feature",
            apkAsset = GitHubApkAsset(
                name = "CleanSMS-1.7.0.apk",
                downloadUrl = "https://github.com/HussainQ/SMS-Cleaner-Tool/releases/download/v1.7.0/CleanSMS-1.7.0.apk",
                sizeBytes = 5L * 1024L * 1024L,
                sha256Checksum = "a".repeat(64)
            )
        )

        val updateResult = UpdateCheckResult(
            repoSlug = "HussainQ/SMS-Cleaner-Tool",
            installedVersionName = "1.6.1-pre.1",
            installedVersionCode = 10601,
            includePrereleases = true,
            latestRelease = fakeRelease,
            upgradeChain = listOf(fakeRelease),
            isUpdateAvailable = true,
            upgradePathSummary = "v1.6.1-pre.1 → v1.7.0"
        )

        var currentPhase by mutableStateOf(InAppDownloadState())

        composeRule.setContent {
            CleanSMSTheme {
                AppUpdatesDialog(
                    uiState = CleanSmsUiState(
                        showUpdatesDialog = true,
                        updateCheckStatus = UpdateCheckStatus.UPDATE_AVAILABLE,
                        updateCheckResult = updateResult,
                        inAppDownloadState = currentPhase
                    ),
                    onCheckNow = {},
                    onTogglePrereleases = {},
                    onSaveRepoSlug = {},
                    onDownloadAndInstall = {
                        downloadClicked = true
                        currentPhase = InAppDownloadState(
                            phase = InAppUpdateInstallPhase.DOWNLOADING,
                            downloadedBytes = 2_500_000L,
                            totalBytes = 5_000_000L,
                            progressFraction = 0.5f,
                            apkFileName = "CleanSMS-1.7.0.apk"
                        )
                    },
                    onCancelDownload = { cancelClicked = true },
                    onTriggerInstall = { installClicked = true },
                    onRequestInstallPermission = { permissionClicked = true },
                    onNotify = {},
                    onDismiss = {}
                )
            }
        }

        // Initial CTA triggers in-app download rather than browser
        composeRule.onNodeWithTag("pull_latest_release_btn").performClick()
        assertTrue(downloadClicked)
        composeRule.waitForIdle()

        // Progress card + Cancel button should display while downloading
        composeRule.onNodeWithTag("in_app_update_progress_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("update_download_progress_bar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_update_download_btn").performScrollTo().performClick()
        assertTrue(cancelClicked)

        // PERMISSION_REQUIRED state prompts for unknown-app permission
        composeRule.runOnIdle {
            currentPhase = InAppDownloadState(
                phase = InAppUpdateInstallPhase.PERMISSION_REQUIRED,
                apkFilePath = "/tmp/CleanSMS-1.7.0.apk",
                apkFileName = "CleanSMS-1.7.0.apk"
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grant_install_permission_btn").performScrollTo().performClick()
        assertTrue(permissionClicked)

        // READY_TO_INSTALL state triggers direct package installer
        composeRule.runOnIdle {
            currentPhase = InAppDownloadState(
                phase = InAppUpdateInstallPhase.READY_TO_INSTALL,
                apkFilePath = "/tmp/CleanSMS-1.7.0.apk",
                apkFileName = "CleanSMS-1.7.0.apk",
                sha256Matched = true
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("install_downloaded_apk_btn").performScrollTo().performClick()
        assertTrue(installClicked)
    }
}
