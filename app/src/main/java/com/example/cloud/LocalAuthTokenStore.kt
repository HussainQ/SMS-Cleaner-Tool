package com.example.cloud

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.CleanSmsDao
import com.example.data.local.OAuthCredentialEntity
import com.example.model.LocalOAuthCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manages strictly on-device local storage for authorized OAuth credentials and keys.
 *
 * Guarantees that credentials are stored exclusively in the application's private sandbox
 * and are never sent off-device or shared with unauthorized third parties.
 */
class LocalAuthTokenStore(
    private val context: Context,
    private val dao: CleanSmsDao
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_VAULT_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Flow of all locally saved authorized credentials.
     */
    fun getAllCredentialsFlow(): Flow<List<LocalOAuthCredential>> {
        return dao.getAllCredentialsFlow().map { list ->
            list.map { it.toLocalCredential() }
        }
    }

    /**
     * Retrieves stored credential for a specific service ID (e.g. "google_drive", "dropbox").
     */
    suspend fun getCredential(serviceId: String): LocalOAuthCredential? = withContext(Dispatchers.IO) {
        dao.getCredentialByServiceId(serviceId)?.toLocalCredential()
    }

    /**
     * Checks if a service is currently authorized and has a valid local credential.
     */
    suspend fun isAuthorized(serviceId: String): Boolean = withContext(Dispatchers.IO) {
        val cred = dao.getCredentialByServiceId(serviceId) ?: return@withContext false
        cred.expiresAt > System.currentTimeMillis()
    }

    /**
     * Stores the credential locally on-device in both the encrypted/private database
     * and local app sandbox preferences.
     */
    suspend fun saveCredentialLocally(credential: LocalOAuthCredential) = withContext(Dispatchers.IO) {
        val entity = OAuthCredentialEntity.fromLocalCredential(credential)
        dao.insertCredential(entity)

        // Store active status and masked key in private preferences
        prefs.edit()
            .putBoolean("authorized_${credential.serviceId}", true)
            .putString("account_${credential.serviceId}", credential.accountName)
            .putLong("authorized_at_${credential.serviceId}", credential.authorizedAt)
            .putString("masked_token_${credential.serviceId}", credential.maskedToken)
            .apply()
    }

    /**
     * Revokes and completely purges the local credential and token for a service.
     */
    suspend fun revokeAndPurge(serviceId: String) = withContext(Dispatchers.IO) {
        dao.deleteCredentialByServiceId(serviceId)

        prefs.edit()
            .remove("authorized_$serviceId")
            .remove("account_$serviceId")
            .remove("authorized_at_$serviceId")
            .remove("masked_token_$serviceId")
            .apply()
    }

    /**
     * Purges all local credentials.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAllCredentials()
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_VAULT_NAME = "cleansms_device_oauth_vault"
    }
}
