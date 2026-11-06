package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CleanSmsDao {

    // Quarantine queries
    @Query("SELECT * FROM quarantine_messages ORDER BY quarantinedAt DESC")
    fun getAllQuarantinedFlow(): Flow<List<QuarantineEntity>>

    @Query("SELECT * FROM quarantine_messages ORDER BY quarantinedAt DESC")
    suspend fun getAllQuarantined(): List<QuarantineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuarantined(messages: List<QuarantineEntity>)

    @Query("DELETE FROM quarantine_messages WHERE id = :id")
    suspend fun deleteQuarantinedById(id: String)

    @Query("DELETE FROM quarantine_messages WHERE id IN (:ids)")
    suspend fun deleteQuarantinedByIds(ids: List<String>)

    @Query("DELETE FROM quarantine_messages")
    suspend fun clearAllQuarantine()

    // Custom Rules queries
    @Query("SELECT * FROM custom_rules ORDER BY createdAt DESC")
    fun getAllRulesFlow(): Flow<List<CustomRuleEntity>>

    @Query("SELECT * FROM custom_rules ORDER BY createdAt DESC")
    suspend fun getAllRules(): List<CustomRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CustomRuleEntity)

    @Update
    suspend fun updateRule(rule: CustomRuleEntity)

    @Query("DELETE FROM custom_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    // Backup queries
    @Query("SELECT * FROM backup_records ORDER BY backupTimestamp DESC")
    fun getAllBackupsFlow(): Flow<List<BackupRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: BackupRecordEntity)

    @Query("DELETE FROM backup_records WHERE id = :id")
    suspend fun deleteBackupById(id: String)

    // OAuth Credentials queries
    @Query("SELECT * FROM oauth_credentials ORDER BY authorizedAt DESC")
    fun getAllCredentialsFlow(): Flow<List<OAuthCredentialEntity>>

    @Query("SELECT * FROM oauth_credentials ORDER BY authorizedAt DESC")
    suspend fun getAllCredentials(): List<OAuthCredentialEntity>

    @Query("SELECT * FROM oauth_credentials WHERE serviceId = :serviceId LIMIT 1")
    suspend fun getCredentialByServiceId(serviceId: String): OAuthCredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: OAuthCredentialEntity)

    @Query("DELETE FROM oauth_credentials WHERE serviceId = :serviceId")
    suspend fun deleteCredentialByServiceId(serviceId: String)

    @Query("DELETE FROM oauth_credentials")
    suspend fun clearAllCredentials()
}

