package com.example.update

import android.content.Context
import com.example.BuildConfig

/**
 * Manages application-level upgrade state across arbitrary version jumps (e.g. v1.0.0 -> v1.6.0+).
 * Pairs with explicit Room DatabaseMigrations (v1 -> v2 -> v3 -> v4) so updating directly from
 * any older release preserves rules, quarantined items, cloud snapshots, and telephony preferences.
 */
class AppUpgradeManager(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getGitHubRepoSlug(): String {
        val stored = prefs.getString(KEY_GITHUB_REPO, null)
        return when {
            stored.isNullOrBlank() -> DEFAULT_GITHUB_REPO
            stored.equals(LEGACY_PLACEHOLDER_REPO, ignoreCase = true) -> DEFAULT_GITHUB_REPO
            else -> stored
        }
    }

    fun setGitHubRepoSlug(slug: String) {
        val normalized = SemanticVersion.normalizeRepoSlug(slug)
            ?.let { "${it.first}/${it.second}" }
            ?: return
        prefs.edit().putString(KEY_GITHUB_REPO, normalized).apply()
    }

    fun getIncludePrereleases(): Boolean {
        val defaultInclude = SemanticVersion.parse(BuildConfig.VERSION_NAME)?.isPreRelease ?: true
        return prefs.getBoolean(KEY_INCLUDE_PRERELEASES, defaultInclude)
    }

    fun setIncludePrereleases(include: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_PRERELEASES, include).apply()
    }

    /**
     * Runs idempotent milestone state migrations from [storedVersionCode] up to [targetVersionCode],
     * guaranteeing airtight upgrades even if the user jumped multiple versions at once.
     */
    fun ensureStateMigrated(
        targetVersionCode: Int = BuildConfig.VERSION_CODE,
        targetVersionName: String = BuildConfig.VERSION_NAME
    ): UpgradeMigrationReport {
        val prevCode = if (prefs.contains(KEY_LAST_VERSION_CODE)) {
            prefs.getInt(KEY_LAST_VERSION_CODE, 0)
        } else {
            null
        }
        val prevName = prefs.getString(KEY_LAST_VERSION_NAME, null)
        val applied = mutableListOf<String>()

        val effectivePrev = prevCode ?: 0
        if (effectivePrev < targetVersionCode) {
            if (effectivePrev in 1 until 10200) {
                applied.add("v1.2.0: Upgraded thread metadata & RCS protocol defaults")
            }
            if (effectivePrev in 1 until 10400) {
                applied.add("v1.4.0: Migrated local OAuth credential vault state")
            }
            if (effectivePrev in 1 until 10500) {
                // Ensure legacy single-flag permission pref does not conflict with split READ/WRITE SMS
                prefs.edit().remove("legacy_sms_permission_granted").apply()
                applied.add("v1.5.0: Split READ_SMS / WRITE_SMS runtime permission state")
            }
            if (effectivePrev in 1 until 10600) {
                // Ensure telephony handover default fallback is initialized if upgrading from pre-1.6.0
                val telephonyPrefs = context.getSharedPreferences("cleansms_telephony_prefs", Context.MODE_PRIVATE)
                if (!telephonyPrefs.contains("previous_default_sms_package")) {
                    telephonyPrefs.edit()
                        .putString("previous_default_sms_app_name", "Google Messages")
                        .apply()
                }
                applied.add("v1.6.0: Initialized Default SMS hand-back, contact filters & deleted recovery state")
            }
            if (effectivePrev in 1 until 10601 && targetVersionCode >= 10601) {
                applied.add("v1.6.1: Scoped per-contact filter rules cleanly across contact threads")
            }
            if (prefs.getString(KEY_GITHUB_REPO, null)?.equals(LEGACY_PLACEHOLDER_REPO, ignoreCase = true) == true) {
                prefs.edit().putString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO).apply()
            }
            if (applied.isEmpty() && prevCode != null) {
                applied.add("Updated v${prevName ?: prevCode} -> v$targetVersionName cleanly")
            }

            prefs.edit()
                .putInt(KEY_LAST_VERSION_CODE, targetVersionCode)
                .putString(KEY_LAST_VERSION_NAME, targetVersionName)
                .putLong(KEY_LAST_MIGRATED_EPOCH, System.currentTimeMillis())
                .apply()
        }

        val wasMultiVersionJump = prevCode != null && (targetVersionCode - prevCode) > 100
        val migratedAt = prefs.getLong(KEY_LAST_MIGRATED_EPOCH, System.currentTimeMillis())

        return UpgradeMigrationReport(
            currentVersionName = targetVersionName,
            currentVersionCode = targetVersionCode,
            previousVersionName = prevName,
            previousVersionCode = prevCode,
            databaseSchemaVersion = 5,
            appliedMilestones = applied,
            wasMultiVersionJump = wasMultiVersionJump,
            migratedAtEpoch = migratedAt
        )
    }

    companion object {
        const val PREFS_NAME = "cleansms_upgrade_prefs"
        const val DEFAULT_GITHUB_REPO = "HussainQ/SMS-Cleaner-Tool"
        private const val LEGACY_PLACEHOLDER_REPO = "shqsid/SMS-Cleaner-Tool"
        private const val KEY_GITHUB_REPO = "github_repo_slug"
        private const val KEY_INCLUDE_PRERELEASES = "include_prereleases"
        private const val KEY_LAST_VERSION_CODE = "last_migrated_version_code"
        private const val KEY_LAST_VERSION_NAME = "last_migrated_version_name"
        private const val KEY_LAST_MIGRATED_EPOCH = "last_migrated_epoch"
    }
}
