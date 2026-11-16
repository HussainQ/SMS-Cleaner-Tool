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

    // Testing Deleted Message Snapshots queries
    @Query("SELECT * FROM deleted_test_snapshots ORDER BY deletedAt DESC")
    fun getAllDeletedTestSnapshotsFlow(): Flow<List<DeletedTestSnapshotEntity>>

    @Query("SELECT * FROM deleted_test_snapshots ORDER BY deletedAt DESC")
    suspend fun getAllDeletedTestSnapshots(): List<DeletedTestSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedTestSnapshots(snapshots: List<DeletedTestSnapshotEntity>)

    @Query("DELETE FROM deleted_test_snapshots WHERE id IN (:ids)")
    suspend fun deleteTestSnapshotsByIds(ids: List<String>)

    @Query("DELETE FROM deleted_test_snapshots")
    suspend fun clearAllDeletedTestSnapshots()

    @Query("DELETE FROM deleted_test_snapshots WHERE deletedAt > 0 AND deletedAt < :cutoffEpochMs")
    suspend fun purgeExpiredDeletedSnapshots(cutoffEpochMs: Long)

    @Transaction
    suspend fun restoreDeletedBatchToInboxCache(
        ids: List<String>,
        restoredCacheEntities: List<InboxMessageCacheEntity>
    ) {
        if (restoredCacheEntities.isNotEmpty()) {
            upsertInboxCache(restoredCacheEntities)
        }
        ids.chunked(500).forEach { batch ->
            deleteTestSnapshotsByIds(batch)
        }
    }

    // =========================================================================
    // Filter By Contact: Background Threading with Room @Query + Coroutine Flow
    // =========================================================================

    @Query("SELECT * FROM inbox_message_cache ORDER BY date DESC")
    fun observeAllInboxCacheFlow(): Flow<List<InboxMessageCacheEntity>>

    @Query("SELECT COUNT(*) FROM inbox_message_cache")
    fun observeInboxCacheCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInboxCache(messages: List<InboxMessageCacheEntity>)

    @Query("DELETE FROM inbox_message_cache WHERE id IN (:ids)")
    suspend fun deleteInboxCacheByIds(ids: List<String>)

    @Query("DELETE FROM inbox_message_cache")
    suspend fun clearInboxCache()

    @Transaction
    suspend fun replaceInboxCache(messages: List<InboxMessageCacheEntity>) {
        clearInboxCache()
        if (messages.isNotEmpty()) {
            upsertInboxCache(messages)
        }
    }

    @Query(
        """
        SELECT
            CASE
                WHEN contactId IS NOT NULL AND TRIM(contactId) != '' THEN 'cid:' || LOWER(TRIM(contactId))
                ELSE 'addr:' || addressKey
            END AS contactKey,
            MAX(contactId) AS contactId,
            COALESCE(NULLIF(MAX(contactName), ''), MAX(address)) AS displayName,
            MAX(address) AS primaryAddress,
            COUNT(*) AS messageCount,
            GROUP_CONCAT(DISTINCT CASE WHEN threadId > 0 THEN threadId ELSE NULL END) AS threadIdsCsv
        FROM inbox_message_cache
        WHERE (:searchQuery = ''
               OR contactNameLower LIKE '%' || LOWER(TRIM(:searchQuery)) || '%'
               OR LOWER(address) LIKE '%' || LOWER(TRIM(:searchQuery)) || '%'
               OR addressKey LIKE '%' || LOWER(TRIM(:searchQuery)) || '%')
        GROUP BY contactKey
        ORDER BY messageCount DESC, LOWER(displayName) ASC
        """
    )
    fun observeInboxContactSummariesFlow(searchQuery: String): Flow<List<ContactThreadSummaryRow>>

    @Query("SELECT * FROM contact_filter_selections WHERE scope = :scope ORDER BY selectedAt ASC")
    fun observeFilterSelectionsFlow(scope: String): Flow<List<ContactFilterSelectionEntity>>

    @Query("SELECT COUNT(*) FROM contact_filter_selections WHERE scope = :scope AND contactId = :contactId")
    suspend fun countFilterSelection(scope: String, contactId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFilterSelection(selection: ContactFilterSelectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFilterSelections(selections: List<ContactFilterSelectionEntity>)

    @Query("DELETE FROM contact_filter_selections WHERE scope = :scope AND contactId = :contactId")
    suspend fun deleteFilterSelection(scope: String, contactId: String)

    @Query("DELETE FROM contact_filter_selections WHERE scope = :scope")
    suspend fun clearFilterSelections(scope: String)

    @Transaction
    suspend fun replaceFilterSelections(scope: String, selections: List<ContactFilterSelectionEntity>) {
        clearFilterSelections(scope)
        if (selections.isNotEmpty()) {
            upsertFilterSelections(selections)
        }
    }

    @Query(
        """
        SELECT DISTINCT m.threadId
        FROM inbox_message_cache m
        INNER JOIN contact_filter_selections s ON s.scope = 'SCANNER'
        WHERE m.threadId > 0 AND (
            (s.threadId IS NOT NULL AND s.threadId > 0 AND m.threadId = s.threadId)
            OR (m.contactId IS NOT NULL AND TRIM(m.contactId) != '' AND LOWER(TRIM(m.contactId)) = LOWER(TRIM(s.contactId)))
            OR (m.contactId IS NOT NULL AND TRIM(m.contactId) != '' AND INSTR(s.matchTokensCsv, ',' || LOWER(TRIM(m.contactId)) || ',') > 0)
            OR (m.contactNameLower != '' AND INSTR(s.matchTokensCsv, ',' || m.contactNameLower || ',') > 0)
            OR (m.addressKey != '' AND INSTR(s.matchTokensCsv, ',' || m.addressKey || ',') > 0)
            OR INSTR(s.matchTokensCsv, ',' || LOWER(TRIM(m.address)) || ',') > 0
        )
        """
    )
    fun observeSelectedScannerThreadIdsFlow(): Flow<List<Long>>

    @Query("SELECT * FROM inbox_message_cache WHERE threadId IN (:threadIds) ORDER BY date DESC")
    fun observeInboxMessagesByThreadIdsFlow(threadIds: List<Long>): Flow<List<InboxMessageCacheEntity>>

    @Query(
        """
        SELECT
            CASE
                WHEN contactId IS NOT NULL AND TRIM(contactId) != '' THEN 'cid:' || LOWER(TRIM(contactId))
                WHEN TRIM(COALESCE(contactName, '')) != '' THEN 'name:' || LOWER(TRIM(contactName))
                ELSE 'addr:' || CASE WHEN addressKey != '' THEN addressKey ELSE LOWER(TRIM(address)) END
            END AS contactKey,
            MAX(contactId) AS contactId,
            COALESCE(NULLIF(MAX(contactName), ''), MAX(address)) AS displayName,
            MAX(address) AS primaryAddress,
            COUNT(*) AS messageCount,
            GROUP_CONCAT(DISTINCT CASE WHEN threadId > 0 THEN threadId ELSE NULL END) AS threadIdsCsv
        FROM (
            SELECT contactId, address, addressKey, contactName, threadId FROM quarantine_messages
            UNION ALL
            SELECT contactId, address, addressKey, contactName, threadId FROM deleted_test_snapshots
        )
        WHERE (:searchQuery = ''
               OR LOWER(COALESCE(contactName, '')) LIKE '%' || LOWER(TRIM(:searchQuery)) || '%'
               OR LOWER(address) LIKE '%' || LOWER(TRIM(:searchQuery)) || '%'
               OR LOWER(addressKey) LIKE '%' || LOWER(TRIM(:searchQuery)) || '%')
        GROUP BY contactKey
        ORDER BY messageCount DESC, LOWER(displayName) ASC
        """
    )
    fun observeVaultContactSummariesFlow(searchQuery: String): Flow<List<ContactThreadSummaryRow>>

    @Query(
        """
        SELECT q.*
        FROM quarantine_messages q
        WHERE (:applyContactFilter = 0)
           OR EXISTS (
               SELECT 1 FROM contact_filter_selections s
               WHERE s.scope = 'VAULT' AND (
                   (s.threadId IS NOT NULL AND s.threadId > 0 AND q.threadId = s.threadId)
                   OR (q.contactId IS NOT NULL AND TRIM(q.contactId) != '' AND INSTR(s.matchTokensCsv, ',' || LOWER(TRIM(q.contactId)) || ',') > 0)
                   OR (TRIM(COALESCE(q.contactName, '')) != '' AND INSTR(s.matchTokensCsv, ',' || LOWER(TRIM(q.contactName)) || ',') > 0)
                   OR (q.addressKey != '' AND INSTR(s.matchTokensCsv, ',' || q.addressKey || ',') > 0)
                   OR INSTR(s.matchTokensCsv, ',' || LOWER(TRIM(q.address)) || ',') > 0
               )
           )
        ORDER BY q.quarantinedAt DESC
        """
    )
    fun observeFilteredQuarantinedFlow(applyContactFilter: Boolean): Flow<List<QuarantineEntity>>

    @Query(
        """
        SELECT d.*
        FROM deleted_test_snapshots d
        WHERE (:applyContactFilter = 0)
           OR EXISTS (
               SELECT 1 FROM contact_filter_selections s
               WHERE s.scope = 'VAULT' AND (
                   (s.threadId IS NOT NULL AND s.threadId > 0 AND d.threadId = s.threadId)
                   OR (d.contactId IS NOT NULL AND TRIM(d.contactId) != '' AND INSTR(s.matchTokensCsv, ',' || LOWER(TRIM(d.contactId)) || ',') > 0)
                   OR (TRIM(COALESCE(d.contactName, '')) != '' AND INSTR(s.matchTokensCsv, ',' || LOWER(TRIM(d.contactName)) || ',') > 0)
                   OR (d.addressKey != '' AND INSTR(s.matchTokensCsv, ',' || d.addressKey || ',') > 0)
                   OR INSTR(s.matchTokensCsv, ',' || LOWER(TRIM(d.address)) || ',') > 0
               )
           )
        ORDER BY d.deletedAt DESC
        """
    )
    fun observeFilteredDeletedTestFlow(applyContactFilter: Boolean): Flow<List<DeletedTestSnapshotEntity>>
}

