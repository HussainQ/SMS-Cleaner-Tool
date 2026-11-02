package com.example.cloud

import com.example.BuildConfig

/**
 * Handles application secrets and credentials securely.
 *
 * Secrets are injected from the AI Studio Secrets panel via .env at build time
 * and accessed via [BuildConfig]. Neither real API keys nor private credentials
 * are hardcoded or committed to git.
 *
 * Safe test/sandbox fallbacks are provided when no production secret is configured.
 */
object CloudSecretsConfig {

    val googleDriveClientId: String
        get() = try {
            BuildConfig.GOOGLE_DRIVE_CLIENT_ID.ifBlank { "test-cleansms-drive.apps.googleusercontent.com" }
        } catch (e: Throwable) {
            "test-cleansms-drive.apps.googleusercontent.com"
        }

    val dropboxApiKey: String
        get() = try {
            BuildConfig.DROPBOX_API_KEY.ifBlank { "mock_test_dropbox_key_cleansms_2026" }
        } catch (e: Throwable) {
            "mock_test_dropbox_key_cleansms_2026"
        }

    val cloudBackupEndpoint: String
        get() = try {
            BuildConfig.CLOUD_BACKUP_ENDPOINT.ifBlank { "https://api.cleansms-backup-test.internal/v1" }
        } catch (e: Throwable) {
            "https://api.cleansms-backup-test.internal/v1"
        }

    /**
     * Checks if current secrets are using test/mock credentials.
     */
    fun isUsingTestCredentials(): Boolean {
        return googleDriveClientId.startsWith("test-") || dropboxApiKey.startsWith("mock_test")
    }

    /**
     * Masks a secret key for safe UI presentation or logging (e.g. "TEST••••7890").
     */
    fun maskSecret(secret: String): String {
        if (secret.length <= 8) return "••••••••"
        return "${secret.take(4)}••••${secret.takeLast(4)}"
    }
}
