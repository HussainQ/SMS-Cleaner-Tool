package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CustomRuleEntity
import com.example.data.local.DatabaseMigrations
import com.example.data.local.DeletedTestSnapshotEntity
import com.example.update.AppUpgradeManager
import com.example.update.GitHubAssetDto
import com.example.update.GitHubReleaseDto
import com.example.update.GitHubUpdateRepository
import com.example.update.SemanticVersion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateAndMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun semanticVersion_parsesAndOrdersNumericAndPrereleaseCorrectly() {
        val versions = listOf(
            "v1.10.0",
            "v1.6.0-pre.2",
            "1.5.0-pre.1",
            "v1.0.0",
            "1.6.0",
            "1.2.0",
            "1.6.0-pre.1",
            "v1.5.0"
        ).map { SemanticVersion.parse(it)!! }

        val sorted = versions.sorted().map { it.displayString() }
        assertEquals(
            listOf(
                "1.0.0",
                "1.2.0",
                "1.5.0-pre.1",
                "1.5.0",
                "1.6.0-pre.1",
                "1.6.0-pre.2",
                "1.6.0",
                "1.10.0"
            ),
            sorted
        )

        assertEquals(10600, SemanticVersion.parse("v1.6.0-pre.1")!!.toVersionCode())
        assertEquals("HussainQ" to "SMS-Cleaner-Tool", SemanticVersion.normalizeRepoSlug("https://github.com/HussainQ/SMS-Cleaner-Tool.git"))
    }

    @Test
    fun evaluateReleases_multipleVersionsBehind_buildsFullUpgradeChainAndRespectsChannel() {
        val releases = listOf(
            GitHubReleaseDto(
                tagName = "v2.0.0",
                name = "Draft Future Release",
                draft = true,
                prerelease = false
            ),
            GitHubReleaseDto(
                tagName = "v1.8.0-pre.1",
                name = "CleanSMS 1.8.0 Pre-Release",
                body = "QA enhancements\nSHA256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                prerelease = true,
                assets = listOf(
                    GitHubAssetDto(
                        name = "app-debug-unsigned.apk",
                        browserDownloadUrl = "https://github.com/HussainQ/SMS-Cleaner-Tool/releases/download/v1.8.0-pre.1/unsigned.apk",
                        size = 18_000_000L
                    ),
                    GitHubAssetDto(
                        name = "CleanSMS-v1.8.0-pre.1-nonprod-local-test.apk",
                        browserDownloadUrl = "https://github.com/HussainQ/SMS-Cleaner-Tool/releases/download/v1.8.0-pre.1/CleanSMS-v1.8.0-pre.1-nonprod-local-test.apk",
                        size = 22_000_000L
                    )
                )
            ),
            GitHubReleaseDto(
                tagName = "v1.2.0",
                body = "RCS thread deduplication",
                prerelease = false
            ),
            GitHubReleaseDto(
                tagName = "v1.7.0",
                name = "CleanSMS 1.7.0 Stable",
                body = "GitHub auto-update checker",
                prerelease = false,
                assets = listOf(
                    GitHubAssetDto(
                        name = "CleanSMS-v1.7.0.apk",
                        browserDownloadUrl = "https://github.com/HussainQ/SMS-Cleaner-Tool/releases/download/v1.7.0/CleanSMS-v1.7.0.apk",
                        size = 21_500_000L
                    )
                )
            ),
            GitHubReleaseDto(
                tagName = "v1.5.0",
                body = "Permissions support",
                prerelease = false
            ),
            GitHubReleaseDto(
                tagName = "v1.6.0-pre.1",
                body = "Per-contact filter",
                prerelease = true
            )
        )

        // Installed on v1.1.0, stable-only channel
        val stableResult = GitHubUpdateRepository.evaluateReleases(
            repoSlug = "HussainQ/SMS-Cleaner-Tool",
            releases = releases,
            installedVersionName = "1.1.0",
            installedVersionCode = 10100,
            includePrereleases = false
        )
        assertTrue(stableResult.isUpdateAvailable)
        assertEquals("v1.7.0", stableResult.latestRelease?.tagName)
        assertEquals(3, stableResult.versionsBehindCount)
        assertEquals(listOf("v1.2.0", "v1.5.0", "v1.7.0"), stableResult.upgradeChain.map { it.tagName })
        assertTrue(stableResult.upgradePathSummary.contains("v1.1.0 → v1.2.0 → v1.5.0 → v1.7.0"))

        // Installed on v1.1.0, include pre-releases
        val preResult = GitHubUpdateRepository.evaluateReleases(
            repoSlug = "HussainQ/SMS-Cleaner-Tool",
            releases = releases,
            installedVersionName = "1.1.0",
            installedVersionCode = 10100,
            includePrereleases = true
        )
        assertTrue(preResult.isUpdateAvailable)
        assertEquals("v1.8.0-pre.1", preResult.latestRelease?.tagName)
        assertEquals(5, preResult.versionsBehindCount)
        assertEquals(
            "CleanSMS-v1.8.0-pre.1-nonprod-local-test.apk",
            preResult.latestRelease?.apkAsset?.name
        )
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            preResult.verifiedSha256
        )

        // Already on latest
        val upToDate = GitHubUpdateRepository.evaluateReleases(
            repoSlug = "HussainQ/SMS-Cleaner-Tool",
            releases = releases,
            installedVersionName = "1.8.0",
            installedVersionCode = 10800,
            includePrereleases = true
        )
        assertFalse(upToDate.isUpdateAvailable)
        assertEquals(0, upToDate.versionsBehindCount)
    }

    @Test
    fun gitHubUpdateRepository_pullsReleasesAndSha256AssetOverRetrofitAndMoshi() = runBlocking {
        val expectedSha = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        val releasesJson = """
            [
              {
                "tag_name": "v1.7.0",
                "name": "CleanSMS v1.7.0",
                "body": "Direct upgrade supported.",
                "html_url": "https://github.com/HussainQ/SMS-Cleaner-Tool/releases/tag/v1.7.0",
                "prerelease": false,
                "draft": false,
                "published_at": "2026-11-15T18:00:00Z",
                "assets": [
                  {
                    "name": "CleanSMS-v1.7.0.apk",
                    "browser_download_url": "https://github.com/HussainQ/SMS-Cleaner-Tool/releases/download/v1.7.0/CleanSMS-v1.7.0.apk",
                    "size": 22000000
                  },
                  {
                    "name": "CleanSMS-v1.7.0.apk.sha256",
                    "browser_download_url": "https://github.com/HussainQ/SMS-Cleaner-Tool/releases/download/v1.7.0/CleanSMS-v1.7.0.apk.sha256",
                    "size": 95
                  }
                ]
              }
            ]
        """.trimIndent()

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val bodyStr = if (url.endsWith(".sha256")) {
                    "$expectedSha  CleanSMS-v1.7.0.apk"
                } else {
                    releasesJson
                }
                val contentType = if (url.endsWith(".sha256")) "text/plain" else "application/json"
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyStr.toResponseBody(contentType.toMediaType()))
                    .build()
            }
            .build()

        val repo = GitHubUpdateRepository(okHttpClient = fakeClient)
        val result = repo.checkForUpdates(
            repoSlug = "HussainQ/SMS-Cleaner-Tool",
            installedVersionName = "1.4.0",
            installedVersionCode = 10400,
            includePrereleases = true
        )

        assertTrue(result.isSuccess)
        val check = result.getOrThrow()
        assertTrue(check.isUpdateAvailable)
        assertEquals("v1.7.0", check.latestRelease?.tagName)
        assertEquals(expectedSha, check.verifiedSha256)
        assertEquals(
            "https://github.com/HussainQ/SMS-Cleaner-Tool/releases/download/v1.7.0/CleanSMS-v1.7.0.apk",
            check.latestRelease?.apkAsset?.downloadUrl
        )
    }

    @Test
    fun roomMigration_fromVersion1ToVersion4_preservesExistingQuarantineDataWithoutLoss() {
        val dbName = "test_upgrade_v1_to_v4.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create legacy v1 SQLite schema directly on disk (missing RCS/contact/snapshot tables)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TABLE `quarantine_messages` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `address` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `date` INTEGER NOT NULL,
                    `categoryString` TEXT NOT NULL,
                    `matchReason` TEXT NOT NULL,
                    `otpCode` TEXT
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO `quarantine_messages` (`id`, `address`, `body`, `date`, `categoryString`, `matchReason`, `otpCode`)
                VALUES ('legacy_1', 'PROMO', '50% off deal', 1700000000000, 'PROMOTION', 'Legacy v1 promo', NULL)
                """.trimIndent()
            )
            sqlite.version = 1
        }

        val upgradedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val dao = upgradedDb.cleanSmsDao()
            val quarantined = dao.getAllQuarantined()
            assertEquals(1, quarantined.size)
            assertEquals("50% off deal", quarantined.first().body)

            // Verify newly migrated v4 tables work cleanly
            dao.insertRule(
                CustomRuleEntity(
                    id = "r_new",
                    name = "Post-upgrade rule",
                    senderFilter = "",
                    contactFilter = "Mom",
                    keywords = "promo",
                    matchAnyKeyword = true,
                    olderThanDays = null,
                    startDateEpoch = null,
                    endDateEpoch = null,
                    isEnabled = true,
                    createdAt = 1700000001000L
                )
            )
            dao.insertDeletedTestSnapshots(
                listOf(
                    DeletedTestSnapshotEntity(
                        id = "snap_1",
                        address = "AD",
                        body = "Test snapshot",
                        date = 1700000002000L,
                        categoryString = "PROMOTION",
                        matchReason = "Test",
                        otpCode = null
                    )
                )
            )
            assertEquals(1, dao.getAllDeletedTestSnapshots().size)

            // Verify newly migrated v5 Contact Filter tables work cleanly
            dao.upsertFilterSelection(
                com.example.data.local.ContactFilterSelectionEntity(
                    compositeKey = "SCANNER:mom",
                    scope = "SCANNER",
                    contactId = "mom",
                    displayName = "Mom",
                    phoneNumbersCsv = "+15550009999",
                    matchTokensCsv = ",mom,+15550009999,5550009999,",
                    threadId = 99L
                )
            )
            dao.upsertInboxCache(
                listOf(
                    com.example.data.local.InboxMessageCacheEntity(
                        id = "v5_msg_1",
                        address = "+15550009999",
                        addressKey = "5550009999",
                        body = "Migrated cache item",
                        date = 1700000003000L,
                        threadId = 99L,
                        contactId = "mom",
                        contactName = "Mom",
                        contactNameLower = "mom"
                    )
                )
            )
            assertEquals(listOf(99L), dao.observeSelectedScannerThreadIdsFlow().first())
        }
        upgradedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun roomMigration_fromVersion3ToVersion4_preservesOAuthAndRulesAndAddsContactFilter() {
        val dbName = "test_upgrade_v3_to_v4.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create v3 schema (custom_rules without contactFilter column, plus oauth_credentials)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TABLE `quarantine_messages` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `address` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `date` INTEGER NOT NULL,
                    `categoryString` TEXT NOT NULL,
                    `matchReason` TEXT NOT NULL,
                    `otpCode` TEXT,
                    `protocolString` TEXT NOT NULL DEFAULT 'SMS',
                    `threadId` INTEGER NOT NULL DEFAULT 0,
                    `isRcsChat` INTEGER NOT NULL DEFAULT 0,
                    `contactName` TEXT,
                    `type` INTEGER NOT NULL DEFAULT 1,
                    `quarantinedAt` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE TABLE `custom_rules` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `senderFilter` TEXT NOT NULL,
                    `keywords` TEXT NOT NULL,
                    `matchAnyKeyword` INTEGER NOT NULL,
                    `olderThanDays` INTEGER,
                    `startDateEpoch` INTEGER,
                    `endDateEpoch` INTEGER,
                    `isEnabled` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE TABLE `backup_records` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `provider` TEXT NOT NULL,
                    `backupTimestamp` INTEGER NOT NULL,
                    `messageCount` INTEGER NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `fileContentJson` TEXT NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE TABLE `oauth_credentials` (
                    `serviceId` TEXT NOT NULL PRIMARY KEY,
                    `serviceName` TEXT NOT NULL,
                    `accountName` TEXT NOT NULL,
                    `accessToken` TEXT NOT NULL,
                    `tokenType` TEXT NOT NULL,
                    `scopes` TEXT NOT NULL,
                    `authorizedAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    `isLocallyStored` INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO `custom_rules` (`id`, `name`, `senderFilter`, `keywords`, `matchAnyKeyword`, `olderThanDays`, `startDateEpoch`, `endDateEpoch`, `isEnabled`, `createdAt`)
                VALUES ('v3_rule', 'Legacy V3 Rule', 'Bank', 'alert', 1, 30, NULL, NULL, 1, 1700000000000)
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO `oauth_credentials` (`serviceId`, `serviceName`, `accountName`, `accessToken`, `tokenType`, `scopes`, `authorizedAt`, `expiresAt`, `isLocallyStored`)
                VALUES ('google_drive', 'Google Drive', 'shqsid@gmail.com', 'tok_abc', 'Bearer', 'drive.appdata', 1000, 2000, 1)
                """.trimIndent()
            )
            sqlite.version = 3
        }

        val upgradedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val dao = upgradedDb.cleanSmsDao()
            val cred = dao.getCredentialByServiceId("google_drive")
            assertNotNull(cred)
            assertEquals("tok_abc", cred?.accessToken)

            val rules = dao.getAllRules()
            assertEquals(1, rules.size)
            assertEquals("Legacy V3 Rule", rules.first().name)
            assertEquals("", rules.first().contactFilter)
        }
        upgradedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun appUpgradeManager_multiVersionJump_runsAllMilestoneMigrationsIdempotently() {
        val prefs = context.getSharedPreferences(AppUpgradeManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .putInt("last_migrated_version_code", 10100)
            .putString("last_migrated_version_name", "1.1.0")
            .commit()

        val manager = AppUpgradeManager(context)
        val report = manager.ensureStateMigrated(
            targetVersionCode = 10602,
            targetVersionName = "1.6.2-pre.1"
        )

        assertTrue(report.wasMultiVersionJump)
        assertEquals("1.1.0", report.previousVersionName)
        assertEquals(10100, report.previousVersionCode)
        assertEquals(5, report.databaseSchemaVersion)
        assertEquals(6, report.appliedMilestones.size)
        assertTrue(report.appliedMilestones.any { it.contains("v1.6.2") })

        // Verify v1.6.2-pre.1 orders cleanly above v1.6.1-pre.1
        assertTrue(SemanticVersion.parse("v1.6.2-pre.1")!! > SemanticVersion.parse("v1.6.1-pre.1")!!)
        assertEquals(10602, SemanticVersion.parse("v1.6.2-pre.1")!!.toVersionCode())

        // Second call at same version should be idempotent
        val secondReport = manager.ensureStateMigrated(
            targetVersionCode = 10602,
            targetVersionName = "1.6.2-pre.1"
        )
        assertFalse(secondReport.wasMultiVersionJump)
        assertTrue(secondReport.appliedMilestones.isEmpty())
    }
}
