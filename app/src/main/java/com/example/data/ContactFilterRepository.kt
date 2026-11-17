package com.example.data

import com.example.data.local.CleanSmsDao
import com.example.data.local.ContactFilterSelectionEntity
import com.example.data.local.ContactThreadSummaryRow
import com.example.data.local.InboxMessageCacheEntity
import com.example.model.ContactFilterMode
import com.example.model.ContactInfo
import com.example.model.ContactThreadEntry
import com.example.model.ContactThreadFilterScope
import com.example.model.ContactThreadHelper
import com.example.model.PhoneNormalizer
import com.example.model.SmsMessageItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed Repository for the 'Filter By Contact' feature.
 *
 * Offloads contact aggregation, thread ID JOIN resolution, search filtering, and Vault group
 * queries to Room @Query methods returning Coroutine Flows on background IO dispatchers so large
 * SMS/RCS message processing cycles never hitch the Compose UI thread.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ContactFilterRepository(
    private val dao: CleanSmsDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scannerSearchQueryFlow = MutableStateFlow("")
    private val vaultSearchQueryFlow = MutableStateFlow("")
    private val scannerFilterEnabledFlow = MutableStateFlow(false)
    private val scannerFilterModeFlow = MutableStateFlow(ContactFilterMode.TARGET_CONTACTS)
    private val vaultFilterEnabledFlow = MutableStateFlow(false)
    private val deviceContactsFlow = MutableStateFlow<List<ContactInfo>>(emptyList())

    fun setScannerSearchQuery(query: String) {
        scannerSearchQueryFlow.value = query
    }

    fun setVaultSearchQuery(query: String) {
        vaultSearchQueryFlow.value = query
    }

    fun setScannerFilterEnabled(enabled: Boolean) {
        scannerFilterEnabledFlow.value = enabled
    }

    fun setScannerFilterMode(mode: ContactFilterMode) {
        scannerFilterModeFlow.value = mode
    }

    fun setVaultFilterEnabled(enabled: Boolean) {
        vaultFilterEnabledFlow.value = enabled
    }

    fun updateDeviceContacts(contacts: List<ContactInfo>) {
        deviceContactsFlow.value = contacts
    }

    /**
     * Streams cached inbox messages from Room on the background IO dispatcher.
     */
    fun observeCachedInboxMessagesFlow(): Flow<List<SmsMessageItem>> {
        return dao.observeAllInboxCacheFlow()
            .map { entities -> entities.map { it.toSmsMessageItem() } }
            .flowOn(ioDispatcher)
    }

    /**
     * Aggregates inbox messages by contact/thread via Room SQLite GROUP BY + LIKE search,
     * merged with any saved device contacts that have 0 messages, off the Main thread.
     */
    fun observeScannerContactEntriesFlow(): Flow<List<ContactThreadEntry>> {
        val dbSummariesFlow = scannerSearchQueryFlow
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce { query -> if (query.isEmpty()) 0L else 60L }
            .flatMapLatest { query ->
                dao.observeInboxContactSummariesFlow(query)
            }

        return combine(
            dbSummariesFlow,
            deviceContactsFlow,
            scannerSearchQueryFlow
        ) { rows, deviceContacts, rawQuery ->
            mergeInboxSummariesWithDeviceContacts(
                rows = rows,
                deviceContacts = deviceContacts,
                searchQuery = rawQuery.trim()
            )
        }.flowOn(ioDispatcher)
    }

    /**
     * Reactive selected contacts list persisted in Room for Scanner Filter By Contact.
     */
    fun observeScannerSelectedContactsFlow(): Flow<List<ContactInfo>> {
        return dao.observeFilterSelectionsFlow(ContactFilterSelectionEntity.SCOPE_SCANNER)
            .map { entities -> entities.map { it.toContactInfo() } }
            .flowOn(ioDispatcher)
    }

    /**
     * Resolves all thread IDs belonging to checked Scanner contacts directly via Room SQLite
     * INNER JOIN over `inbox_message_cache` x `contact_filter_selections`.
     */
    fun observeScannerFilterScopeFlow(): Flow<ContactThreadFilterScope> {
        return combine(
            scannerFilterEnabledFlow,
            scannerFilterModeFlow,
            observeScannerSelectedContactsFlow(),
            dao.observeSelectedScannerThreadIdsFlow()
        ) { enabled, mode, selectedContacts, joinedThreadIds ->
            if (!enabled || selectedContacts.isEmpty()) {
                ContactThreadFilterScope(enabled = enabled, mode = mode)
            } else {
                val contactIds = selectedContacts.mapTo(HashSet()) { it.id }
                val tokens = ContactThreadHelper.collectTokensForContacts(selectedContacts)
                val threadIds = HashSet<Long>(joinedThreadIds.size + selectedContacts.size)
                for (tid in joinedThreadIds) {
                    if (tid > 0L) threadIds.add(tid)
                }
                for (contact in selectedContacts) {
                    contact.threadId?.takeIf { it > 0L }?.let { threadIds.add(it) }
                }
                ContactThreadFilterScope(
                    enabled = true,
                    mode = mode,
                    selectedContactIds = contactIds,
                    selectedThreadIds = threadIds,
                    matchTokens = tokens
                )
            }
        }.flowOn(ioDispatcher)
    }

    /**
     * Aggregates Vault contacts across `quarantine_messages` and `deleted_test_snapshots`
     * via SQLite UNION ALL + GROUP BY + LIKE search on Room's background executor.
     */
    fun observeVaultContactEntriesFlow(): Flow<List<ContactThreadEntry>> {
        val dbVaultSummariesFlow = vaultSearchQueryFlow
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce { query -> if (query.isEmpty()) 0L else 60L }
            .flatMapLatest { query ->
                dao.observeVaultContactSummariesFlow(query)
            }

        return combine(
            dbVaultSummariesFlow,
            deviceContactsFlow
        ) { rows, deviceContacts ->
            mapVaultSummaries(rows, deviceContacts)
        }.flowOn(ioDispatcher)
    }

    /**
     * Reactive selected contacts list persisted in Room for Vault Filter By Contact.
     */
    fun observeVaultSelectedContactsFlow(): Flow<List<ContactInfo>> {
        return dao.observeFilterSelectionsFlow(ContactFilterSelectionEntity.SCOPE_VAULT)
            .map { entities -> entities.map { it.toContactInfo() } }
            .flowOn(ioDispatcher)
    }

    /**
     * Filtered Quarantine messages resolved via Room @Query EXISTS subquery against
     * `contact_filter_selections` on the background IO dispatcher.
     */
    fun observeFilteredQuarantinedFlow(): Flow<List<SmsMessageItem>> {
        return combine(
            vaultFilterEnabledFlow,
            dao.observeFilterSelectionsFlow(ContactFilterSelectionEntity.SCOPE_VAULT)
        ) { enabled, selections ->
            enabled && selections.isNotEmpty()
        }
            .distinctUntilChanged()
            .flatMapLatest { applyFilter ->
                dao.observeFilteredQuarantinedFlow(applyFilter)
            }
            .map { entities -> entities.map { it.toSmsMessageItem() } }
            .flowOn(ioDispatcher)
    }

    /**
     * Filtered Deleted-Test snapshots resolved via Room @Query EXISTS subquery against
     * `contact_filter_selections` on the background IO dispatcher.
     */
    fun observeFilteredDeletedTestFlow(): Flow<List<SmsMessageItem>> {
        return combine(
            vaultFilterEnabledFlow,
            dao.observeFilterSelectionsFlow(ContactFilterSelectionEntity.SCOPE_VAULT)
        ) { enabled, selections ->
            enabled && selections.isNotEmpty()
        }
            .distinctUntilChanged()
            .flatMapLatest { applyFilter ->
                dao.observeFilteredDeletedTestFlow(applyFilter)
            }
            .map { entities -> entities.map { it.toSmsMessageItem() } }
            .flowOn(ioDispatcher)
    }

    // =========================================================================
    // Suspend Write Operations (Always off Main Thread)
    // =========================================================================

    suspend fun syncInboxMessages(messages: List<SmsMessageItem>) = withContext(ioDispatcher) {
        val entities = messages.map { InboxMessageCacheEntity.fromSmsMessageItem(it) }
        dao.replaceInboxCache(entities)
    }

    suspend fun upsertInboxMessages(messages: List<SmsMessageItem>) = withContext(ioDispatcher) {
        if (messages.isEmpty()) return@withContext
        dao.upsertInboxCache(messages.map { InboxMessageCacheEntity.fromSmsMessageItem(it) })
    }

    suspend fun removeInboxMessagesByIds(ids: List<String>) = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext
        // SQLite bind parameter limit is 999 per statement; chunk safely for large batches.
        ids.chunked(500).forEach { batch ->
            dao.deleteInboxCacheByIds(batch)
        }
    }

    /**
     * Atomically moves a batch of messages from Recently Deleted back into the cached Inbox table
     * so Room's Filter By Contact & classification Flows immediately reflect restored items.
     */
    suspend fun restoreDeletedBatchToInboxCache(restoredItems: List<SmsMessageItem>) = withContext(ioDispatcher) {
        if (restoredItems.isEmpty()) return@withContext
        val cacheEntities = restoredItems.map { InboxMessageCacheEntity.fromSmsMessageItem(it) }
        val ids = restoredItems.map { it.id }
        dao.restoreDeletedBatchToInboxCache(ids = ids, restoredCacheEntities = cacheEntities)
    }

    suspend fun deleteRecentlyDeletedByIdsBatch(ids: List<String>) = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext
        ids.chunked(500).forEach { batch ->
            dao.deleteTestSnapshotsByIds(batch)
        }
    }

    suspend fun purgeExpiredRecentlyDeleted(retentionDays: Int = 30) = withContext(ioDispatcher) {
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24L * 60L * 60L * 1000L)
        dao.purgeExpiredDeletedSnapshots(cutoff)
    }

    /**
     * Toggles a single contact in Room (`contact_filter_selections`) and returns `true` if now checked.
     */
    suspend fun toggleSelectedContact(scope: String, contact: ContactInfo): Boolean = withContext(ioDispatcher) {
        val existingCount = dao.countFilterSelection(scope, contact.id)
        if (existingCount > 0) {
            dao.deleteFilterSelection(scope, contact.id)
            false
        } else {
            dao.upsertFilterSelection(ContactFilterSelectionEntity.fromContactInfo(scope, contact))
            true
        }
    }

    suspend fun replaceSelectedContacts(scope: String, contacts: List<ContactInfo>) = withContext(ioDispatcher) {
        val entities = contacts.distinctBy { it.id }.map {
            ContactFilterSelectionEntity.fromContactInfo(scope, it)
        }
        dao.replaceFilterSelections(scope, entities)
    }

    suspend fun clearSelectedContacts(scope: String) = withContext(ioDispatcher) {
        dao.clearFilterSelections(scope)
    }

    suspend fun pruneInvalidVaultSelections(validIds: Set<String>) = withContext(ioDispatcher) {
        if (validIds.isEmpty()) {
            dao.clearFilterSelections(ContactFilterSelectionEntity.SCOPE_VAULT)
        } else {
            val current = dao.observeFilterSelectionsFlow(ContactFilterSelectionEntity.SCOPE_VAULT)
            // Handled reactively when vault entries change via replaceSelectedContacts if needed
        }
    }

    private fun parseThreadIdsCsv(csv: String?): Set<Long> {
        if (csv.isNullOrBlank()) return emptySet()
        val set = HashSet<Long>(4)
        var start = 0
        val len = csv.length
        while (start <= len) {
            val comma = csv.indexOf(',', start)
            val end = if (comma == -1) len else comma
            if (end > start) {
                val tid = csv.substring(start, end).trim().toLongOrNull()
                if (tid != null && tid > 0L) set.add(tid)
            }
            if (comma == -1) break
            start = comma + 1
        }
        return set
    }

    private fun mergeInboxSummariesWithDeviceContacts(
        rows: List<ContactThreadSummaryRow>,
        deviceContacts: List<ContactInfo>,
        searchQuery: String
    ): List<ContactThreadEntry> {
        val hasDeviceContacts = deviceContacts.isNotEmpty()
        val deviceById = if (hasDeviceContacts) {
            deviceContacts.associateBy { it.id.trim().lowercase() }
        } else {
            emptyMap()
        }
        val deviceByNameOrPhone = if (hasDeviceContacts) {
            HashMap<String, ContactInfo>(deviceContacts.size * 3).apply {
                for (dc in deviceContacts) {
                    dc.displayName.trim().lowercase().takeIf { it.isNotEmpty() }?.let {
                        putIfAbsent(it, dc)
                    }
                    for (phone in dc.phoneNumbers) {
                        for (key in PhoneNormalizer.extractMatchKeys(phone)) {
                            putIfAbsent(key, dc)
                        }
                    }
                }
            }
        } else {
            emptyMap()
        }

        val entries = ArrayList<ContactThreadEntry>(rows.size + deviceContacts.size)
        val coveredDeviceIds = if (hasDeviceContacts) HashSet<String>(deviceContacts.size) else null

        for (row in rows) {
            val matchedDevice = if (hasDeviceContacts) {
                val cidLower = row.contactId?.trim()?.lowercase().orEmpty()
                val primaryKey = PhoneNormalizer.primaryKey(row.primaryAddress)
                deviceById[cidLower]
                    ?: deviceByNameOrPhone[primaryKey]
                    ?: deviceByNameOrPhone[row.displayName.trim().lowercase()]
            } else {
                null
            }

            val threadIds = parseThreadIdsCsv(row.threadIdsCsv)
            val contact = if (matchedDevice != null) {
                coveredDeviceIds?.add(matchedDevice.id)
                if (matchedDevice.threadId != null) {
                    matchedDevice
                } else {
                    matchedDevice.copy(threadId = threadIds.firstOrNull())
                }
            } else {
                val synthesizedId = row.contactId?.takeIf { it.isNotBlank() }
                    ?: "thread_${PhoneNormalizer.primaryKey(row.primaryAddress)}"
                ContactInfo(
                    id = synthesizedId,
                    displayName = row.displayName.ifBlank { row.primaryAddress },
                    phoneNumbers = listOf(row.primaryAddress),
                    isFromDeviceContacts = !row.contactId.isNullOrBlank(),
                    threadId = threadIds.firstOrNull()
                )
            }

            entries.add(
                ContactThreadEntry(
                    contact = contact,
                    messageCount = row.messageCount,
                    threadIds = threadIds
                )
            )
        }

        if (hasDeviceContacts) {
            val q = searchQuery
            for (dc in deviceContacts) {
                if (coveredDeviceIds?.contains(dc.id) == true) continue
                if (q.isNotEmpty()) {
                    val matchesSearch = dc.displayName.contains(q, ignoreCase = true) ||
                        dc.phoneNumbers.any { it.contains(q, ignoreCase = true) }
                    if (!matchesSearch) continue
                }
                entries.add(
                    ContactThreadEntry(
                        contact = dc,
                        messageCount = 0,
                        threadIds = dc.threadId?.takeIf { it > 0L }?.let { setOf(it) } ?: emptySet()
                    )
                )
            }
        }

        return entries.sortedWith(
            compareByDescending<ContactThreadEntry> { it.messageCount > 0 }
                .thenByDescending { it.messageCount }
                .thenBy { it.contact.displayName.lowercase() }
        )
    }

    private fun mapVaultSummaries(
        rows: List<ContactThreadSummaryRow>,
        deviceContacts: List<ContactInfo>
    ): List<ContactThreadEntry> {
        if (rows.isEmpty()) return emptyList()
        val deviceById = deviceContacts.associateBy { it.id.trim().lowercase() }
        val deviceByPhoneOrName = HashMap<String, ContactInfo>()
        for (dc in deviceContacts) {
            dc.displayName.trim().lowercase().takeIf { it.isNotEmpty() }?.let {
                deviceByPhoneOrName.putIfAbsent(it, dc)
            }
            for (phone in dc.phoneNumbers) {
                for (key in PhoneNormalizer.extractMatchKeys(phone)) {
                    deviceByPhoneOrName.putIfAbsent(key, dc)
                }
            }
        }

        return rows.map { row ->
            val threadIds = parseThreadIdsCsv(row.threadIdsCsv)
            val matchedDevice = row.contactId?.trim()?.lowercase()?.let { deviceById[it] }
                ?: deviceByPhoneOrName[PhoneNormalizer.primaryKey(row.primaryAddress)]
                ?: deviceByPhoneOrName[row.displayName.trim().lowercase()]

            val contact = matchedDevice ?: ContactInfo(
                id = "vault_${row.contactKey}",
                displayName = row.displayName.ifBlank { row.primaryAddress },
                phoneNumbers = listOf(row.primaryAddress).distinct(),
                isFromDeviceContacts = !row.contactId.isNullOrBlank(),
                threadId = threadIds.firstOrNull()
            )
            ContactThreadEntry(
                contact = contact,
                messageCount = row.messageCount,
                threadIds = threadIds
            )
        }
    }
}
