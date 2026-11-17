package com.example.update

import android.content.Context
import com.example.BuildConfig

/**
 * Manages application-level upgrade state across arbitrary version jumps (e.g. v1.0.0 -> v1.7.1+).
 * Pairs with explicit Room DatabaseMigrations (v1 -> v2 -> v3 -> v4 -> v5 -> v6) so updating directly from
 * any older release preserves rules, quarantined items, thread folder states, cloud snapshots, and telephony preferences.
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

    fun isCompactMessagesUi(): Boolean {
        return prefs.getBoolean(KEY_COMPACT_MESSAGES_UI, true)
    }

    fun setCompactMessagesUi(compact: Boolean) {
        prefs.edit().putBoolean(KEY_COMPACT_MESSAGES_UI, compact).apply()
    }

    fun isUseDeviceMaterialTheme(): Boolean {
        return prefs.getBoolean(KEY_USE_DEVICE_MATERIAL_THEME, true)
    }

    fun setUseDeviceMaterialTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_DEVICE_MATERIAL_THEME, enabled).apply()
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
            if (effectivePrev in 1 until 10602 && targetVersionCode >= 10602) {
                applied.add("v1.6.2: Background Room @Query + Coroutine Flow contact filtering & Recently Deleted batch restoration")
            }
            if (effectivePrev in 1 until 10700 && targetVersionCode >= 10700) {
                applied.add("v1.7.0: Full SMS/MMS/RCS Messages landing tab, top conversation search & configurable Compact UI settings")
            }
            if (effectivePrev in 1 until 10701 && targetVersionCode >= 10701) {
                applied.add("v1.7.1: Cross-app Archived & Spam message folders (Room v6 + Telephony sync) & direct Compact UI toggle")
            }
            if (effectivePrev in 1 until 10702 && targetVersionCode >= 10702) {
                applied.add("v1.7.2: Device Material UI & Material You dynamic theming with Settings toggle")
            }
            if (effectivePrev in 1 until 10703 && targetVersionCode >= 10703) {
                applied.add("v1.7.3: Lightweight refactor (local vector icons, R8 shrinking & OkHttp updater), faster scanner/Room pipelines & indeterminate scan progress bar")
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
            databaseSchemaVersion = 6,
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
        private const val KEY_COMPACT_MESSAGES_UI = "compact_messages_ui"
        private const val KEY_USE_DEVICE_MATERIAL_THEME = "use_device_material_theme"
        private const val KEY_LAST_VERSION_CODE = "last_migrated_version_code"
        private const val KEY_LAST_VERSION_NAME = "last_migrated_version_name"
        private const val KEY_LAST_MIGRATED_EPOCH = "last_migrated_epoch"
    }
}
