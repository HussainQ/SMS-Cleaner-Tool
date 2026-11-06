package com.example.model

/**
 * Represents an on-device OAuth credential/secret pulled from the device
 * IF AND ONLY IF explicitly authorized by the user.
 *
 * Kept strictly on the local device in private sandbox storage.
 */
data class LocalOAuthCredential(
    val serviceId: String,          // e.g. "google_drive", "dropbox"
    val serviceName: String,        // e.g. "Google Drive", "Dropbox"
    val accountName: String,        // e.g. "shqsid@gmail.com"
    val accessToken: String,        // OAuth access token or auth token pulled from device
    val tokenType: String = "Bearer",
    val scopes: String = "https://www.googleapis.com/auth/drive.appdata",
    val authorizedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L), // 30 days
    val isLocallyStored: Boolean = true
) {
    /**
     * Safely masks the secret/token for UI display (e.g., "ya29••••38d1").
     */
    val maskedToken: String
        get() {
            if (accessToken.length <= 8) return "••••••••"
            return "${accessToken.take(4)}••••${accessToken.takeLast(4)}"
        }
}

/**
 * Service metadata representing available Cloud/OAuth integrations.
 */
data class OAuthServiceDefinition(
    val id: String,
    val name: String,
    val description: String,
    val requiredScopes: List<String>,
    val defaultAccount: String = "shqsid@gmail.com"
)
