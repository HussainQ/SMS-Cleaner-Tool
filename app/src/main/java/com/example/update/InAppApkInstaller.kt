package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Handles private app-cache APK storage, unknown-app-source permission checks,
 * and FileProvider-backed PackageInstaller intents for direct in-app updates.
 */
object InAppApkInstaller {

    fun getUpdatesDirectory(context: Context): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun resolveApkDestination(context: Context, rawFileName: String): File {
        val safeName = rawFileName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
        return File(getUpdatesDirectory(context), safeName)
    }

    fun cleanupStaleApks(context: Context, keepFileName: String? = null) {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (keepFileName == null || file.name != keepFileName) {
                file.delete()
            }
        }
    }

    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createUnknownAppSourcesSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun getApkContentUri(context: Context, apkFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
    }

    fun createInstallApkIntent(context: Context, apkFile: File): Intent {
        val apkUri = getApkContentUri(context, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }
}
