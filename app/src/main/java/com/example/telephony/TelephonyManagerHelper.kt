package com.example.telephony

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Telephony

data class PreviousSmsAppInfo(
    val packageName: String,
    val appName: String
)

data class DefaultSmsRoleStatus(
    val isDefaultSmsApp: Boolean,
    val currentDefaultPackage: String?,
    val previousAppInfo: PreviousSmsAppInfo
)

object TelephonyManagerHelper {

    private const val PREFS_NAME = "cleansms_telephony_prefs"
    private const val KEY_PREV_SMS_PKG = "previous_default_sms_package"
    private const val KEY_PREV_SMS_NAME = "previous_default_sms_app_name"
    private const val FALLBACK_GOOGLE_MESSAGES_PKG = "com.google.android.apps.messaging"
    private const val FALLBACK_GOOGLE_MESSAGES_NAME = "Google Messages"

    fun isDefaultSmsApp(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            }
            // Fallback on API 29+ if RoleManager is unavailable, without invoking
            // Telephony.Sms.getDefaultSmsPackage (which logs read_cell_broadcasts errors).
            return resolveCurrentDefaultSmsToHandlerPackage(context) == context.packageName
        }
        val defaultPackage = try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (_: Throwable) {
            null
        }
        return defaultPackage == context.packageName
    }

    fun getDefaultSmsPackage(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            ) {
                return context.packageName
            }
            return resolveCurrentDefaultSmsToHandlerPackage(context)
        }
        return try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Checks whether this app currently holds the Default SMS role and returns
     * both the active default package and the previous/fallback SMS app to hand back to.
     */
    fun inspectDefaultSmsRoleStatus(context: Context): DefaultSmsRoleStatus {
        val isDefault = isDefaultSmsApp(context)
        val activePackage = if (isDefault) context.packageName else getDefaultSmsPackage(context)
        val previousInfo = if (!isDefault) {
            capturePreviousDefaultSmsAppIfNeeded(context)
        } else {
            getPreviousDefaultSmsAppInfo(context)
        }
        return DefaultSmsRoleStatus(
            isDefaultSmsApp = isDefault,
            currentDefaultPackage = activePackage,
            previousAppInfo = previousInfo
        )
    }

    /**
     * Captures the currently active Default SMS app before CleanSMS requests the role,
     * persisting its package name and display label so we can seamlessly hand the role back.
     */
    fun capturePreviousDefaultSmsAppIfNeeded(context: Context): PreviousSmsAppInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentDefaultPkg = getDefaultSmsPackage(context)

        if (!currentDefaultPkg.isNullOrBlank() && currentDefaultPkg != context.packageName) {
            val label = resolveAppLabel(context, currentDefaultPkg)
            prefs.edit()
                .putString(KEY_PREV_SMS_PKG, currentDefaultPkg)
                .putString(KEY_PREV_SMS_NAME, label)
                .apply()
            return PreviousSmsAppInfo(currentDefaultPkg, label)
        }

        val savedPkg = prefs.getString(KEY_PREV_SMS_PKG, null)
        val savedName = prefs.getString(KEY_PREV_SMS_NAME, null)
        if (!savedPkg.isNullOrBlank() && savedPkg != context.packageName) {
            return PreviousSmsAppInfo(savedPkg, savedName ?: resolveAppLabel(context, savedPkg))
        }

        // Discover an alternative installed SMS handler (preferring Google Messages)
        val discovered = discoverAlternativeSmsApp(context)
        if (discovered != null) {
            prefs.edit()
                .putString(KEY_PREV_SMS_PKG, discovered.packageName)
                .putString(KEY_PREV_SMS_NAME, discovered.appName)
                .apply()
            return discovered
        }

        return PreviousSmsAppInfo(FALLBACK_GOOGLE_MESSAGES_PKG, FALLBACK_GOOGLE_MESSAGES_NAME)
    }

    fun getPreviousDefaultSmsAppInfo(context: Context): PreviousSmsAppInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPkg = prefs.getString(KEY_PREV_SMS_PKG, null)
        val savedName = prefs.getString(KEY_PREV_SMS_NAME, null)
        if (!savedPkg.isNullOrBlank() && savedPkg != context.packageName) {
            return PreviousSmsAppInfo(savedPkg, savedName ?: resolveAppLabel(context, savedPkg))
        }
        return capturePreviousDefaultSmsAppIfNeeded(context)
    }

    private fun resolveAppLabel(context: Context, packageName: String): String {
        if (packageName == FALLBACK_GOOGLE_MESSAGES_PKG) {
            return try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString().ifBlank { FALLBACK_GOOGLE_MESSAGES_NAME }
            } catch (_: Throwable) {
                FALLBACK_GOOGLE_MESSAGES_NAME
            }
        }
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun resolveCurrentDefaultSmsToHandlerPackage(context: Context): String? {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            val pkg = resolved?.activityInfo?.packageName
            if (!pkg.isNullOrBlank() &&
                pkg != "android" &&
                pkg != "com.android.internal.app"
            ) {
                pkg
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun discoverAlternativeSmsApp(context: Context): PreviousSmsAppInfo? {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            val handlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            val other = handlers
                .mapNotNull { it.activityInfo?.packageName }
                .filter { it != context.packageName }
                .let { pkgs ->
                    pkgs.firstOrNull { it == FALLBACK_GOOGLE_MESSAGES_PKG } ?: pkgs.firstOrNull()
                }
            other?.let { PreviousSmsAppInfo(it, resolveAppLabel(context, it)) }
        } catch (_: Throwable) {
            null
        }
    }

    fun createRequestDefaultSmsIntent(context: Context): Intent {
        capturePreviousDefaultSmsAppIfNeeded(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun createManageDefaultAppsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    /**
     * Creates an Intent to hand the Default SMS Role back to the previous SMS app.
     * On Android 10+ (API 29+), ACTION_CHANGE_DEFAULT is ignored when targeting another package,
     * so we launch Android's Default Apps Settings directly; on Android 9 and below,
     * ACTION_CHANGE_DEFAULT prompts directly to restore the previous package.
     */
    fun createRestoreDefaultSmsIntent(context: Context, targetPackage: String): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, targetPackage)
            }
        }
    }

    fun createRestoreDefaultSmsIntent(targetPackage: String): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, targetPackage)
            }
        }
    }
}
