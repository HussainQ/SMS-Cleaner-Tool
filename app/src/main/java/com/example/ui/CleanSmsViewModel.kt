package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloud.CloudBackupManager
import com.example.data.SmsClassifier
import com.example.data.SmsRepository
import com.example.data.local.AppDatabase
import com.example.data.local.CustomRuleEntity
import com.example.data.local.QuarantineEntity
import com.example.model.BackupHistoryRecord
import com.example.model.CloudBackupPayload
import com.example.model.CustomFilterRule
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import com.example.telephony.TelephonyManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CleanSmsUiState(
    val isDemoMode: Boolean = true,
    val hasSmsPermission: Boolean = false,
    val isDefaultSmsApp: Boolean = false,
    val allMessages: List<SmsMessageItem> = emptyList(),
    val classifiedMessages: List<SmsMessageItem> = emptyList(),
    val quarantinedMessages: List<SmsMessageItem> = emptyList(),
    val customRules: List<CustomFilterRule> = emptyList(),
    val backupHistory: List<BackupHistoryRecord> = emptyList(),
    val isScanning: Boolean = false,
    val searchQuery: String = "",
    val selectedCategoryTab: SmsCategory = SmsCategory.ALL,
    // Cleaning filter settings
    val filterDuplicates: Boolean = true,
    val filterOtps: Boolean = true,
    val onlyExpiredOtps: Boolean = true,
    val filterPromotions: Boolean = true,
    val filterSpam: Boolean = true,
    val applyCustomRules: Boolean = true,
    // Duplicate thread fixing settings
    val duplicateKeepStrategy: com.example.model.DuplicateKeepStrategy = com.example.model.DuplicateKeepStrategy.KEEP_OLDEST,
    val groupByThreadsInDuplicates: Boolean = true,
    val duplicateThreadClusters: List<com.example.model.DuplicateThreadCluster> = emptyList(),
    // Cloud settings
    val selectedCloudProvider: String = "Google Drive",
    val isGoogleDriveConnected: Boolean = false,
    val isDropboxConnected: Boolean = false,
    val autoBackupBeforeClean: Boolean = true,
    // On-device OAuth credentials vault
    val authorizedOAuthCredentials: List<com.example.model.LocalOAuthCredential> = emptyList(),
    val availableDeviceAccounts: List<String> = listOf("shqsid@gmail.com"),
    val serviceToAuthorize: com.example.model.OAuthServiceDefinition? = null,
    val selectedAccountForAuth: String = "shqsid@gmail.com",
    val isAuthorizingService: Boolean = false,
    // Active dialogs
    val showPermanentDeleteDialog: Boolean = false,
    val deleteTargetMessages: List<SmsMessageItem> = emptyList(),
    val isDeletingFromQuarantine: Boolean = false,
    val showAddCustomRuleSheet: Boolean = false,
    val showRestoreCloudDialog: Boolean = false,
    val activeRestorePayload: CloudBackupPayload? = null,
    val detailMessage: SmsMessageItem? = null,
    val snackbarMessage: String? = null
)

class CleanSmsViewModel(
    application: Application,
    private val repository: SmsRepository = SmsRepository(application),
    private val cloudManager: CloudBackupManager = CloudBackupManager(application),
    private val db: AppDatabase = AppDatabase.getInstance(application),
    val deviceOAuthManager: com.example.cloud.DeviceOAuthManager = com.example.cloud.DeviceOAuthManager(application, db.cleanSmsDao())
) : AndroidViewModel(application) {

    private val dao = db.cleanSmsDao()

    private val _uiState = MutableStateFlow(CleanSmsUiState())
    val uiState: StateFlow<CleanSmsUiState> = _uiState.asStateFlow()

    init {
        checkTelephonyState()
        observeLocalDatabase()
        loadInitialData()
    }

    fun checkTelephonyState() {
        val context = getApplication<Application>()
        val isDefault = TelephonyManagerHelper.isDefaultSmsApp(context)
        _uiState.update { it.copy(isDefaultSmsApp = isDefault) }
    }

    fun setSmsPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasSmsPermission = granted) }
        if (granted && !_uiState.value.isDemoMode) {
            refreshMessages()
        }
    }

    fun setDemoMode(isDemo: Boolean) {
        _uiState.update { it.copy(isDemoMode = isDemo) }
        refreshMessages()
    }

    private fun observeLocalDatabase() {
        viewModelScope.launch {
            dao.getAllQuarantinedFlow().collect { entities ->
                val items = entities.map { it.toSmsMessageItem() }
                _uiState.update { it.copy(quarantinedMessages = items) }
            }
        }

        viewModelScope.launch {
            dao.getAllRulesFlow().collect { entities ->
                val rules = entities.map {
                    CustomFilterRule(
                        id = it.id,
                        name = it.name,
                        senderFilter = it.senderFilter,
                        keywords = it.keywords,
                        matchAnyKeyword = it.matchAnyKeyword,
                        olderThanDays = it.olderThanDays,
                        startDateEpoch = it.startDateEpoch,
                        endDateEpoch = it.endDateEpoch,
                        isEnabled = it.isEnabled,
                        createdAt = it.createdAt
                    )
                }
                _uiState.update { it.copy(customRules = rules) }
                // Re-classify messages with updated rules
                reclassifyCurrentMessages()
            }
        }

        viewModelScope.launch {
            dao.getAllBackupsFlow().collect { records ->
                val history = records.map {
                    BackupHistoryRecord(
                        id = it.id,
                        provider = it.provider,
                        backupTimestamp = it.backupTimestamp,
                        messageCount = it.messageCount,
                        fileName = it.fileName,
                        sizeBytes = it.sizeBytes,
                        fileContentJson = it.fileContentJson
                    )
                }
                _uiState.update { it.copy(backupHistory = history) }
            }
        }

        viewModelScope.launch {
            deviceOAuthManager.authorizedCredentialsFlow.collect { creds ->
                _uiState.update { state ->
                    val hasDrive = creds.any { it.serviceId == com.example.cloud.DeviceOAuthManager.SERVICE_GOOGLE_DRIVE }
                    val hasDropbox = creds.any { it.serviceId == com.example.cloud.DeviceOAuthManager.SERVICE_DROPBOX }
                    state.copy(
                        authorizedOAuthCredentials = creds,
                        isGoogleDriveConnected = hasDrive,
                        isDropboxConnected = hasDropbox
                    )
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val accounts = deviceOAuthManager.getDetectedDeviceAccounts()
            _uiState.update {
                it.copy(
                    availableDeviceAccounts = accounts,
                    selectedAccountForAuth = accounts.firstOrNull() ?: "shqsid@gmail.com"
                )
            }

            // Seed a default sample custom rule if empty
            val existingRules = dao.getAllRules()
            if (existingRules.isEmpty()) {
                val sampleRule = CustomRuleEntity(
                    id = "rule_default_1",
                    name = "Bank Alerts Older Than 30 Days",
                    senderFilter = "Bank",
                    keywords = "withdrawn, debit, balance",
                    matchAnyKeyword = true,
                    olderThanDays = 30,
                    startDateEpoch = null,
                    endDateEpoch = null,
                    isEnabled = true,
                    createdAt = System.currentTimeMillis()
                )
                dao.insertRule(sampleRule)
            }
            refreshMessages()
        }
    }

    fun refreshMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            withContext(Dispatchers.IO) {
                val raw = if (_uiState.value.isDemoMode) {
                    repository.getDemoSmsList()
                } else {
                    repository.readDeviceSms()
                }

                // Filter out quarantined messages from raw inbox
                val quarantinedIds = _uiState.value.quarantinedMessages.map { it.id }.toSet()
                val activeRaw = raw.filterNot { quarantinedIds.contains(it.id) }

                val classified = SmsClassifier.classifyMessages(
                    rawMessages = activeRaw,
                    activeRules = _uiState.value.customRules,
                    filterDuplicates = _uiState.value.filterDuplicates,
                    filterOtps = _uiState.value.filterOtps,
                    onlyExpiredOtps = _uiState.value.onlyExpiredOtps,
                    filterPromotions = _uiState.value.filterPromotions,
                    filterSpam = _uiState.value.filterSpam,
                    duplicateKeepStrategy = _uiState.value.duplicateKeepStrategy
                )
                val clusters = SmsClassifier.extractDuplicateThreadClusters(classified)

                _uiState.update {
                    it.copy(
                        allMessages = activeRaw,
                        classifiedMessages = classified,
                        duplicateThreadClusters = clusters,
                        isScanning = false
                    )
                }
            }
        }
    }

    private fun reclassifyCurrentMessages() {
        val raw = _uiState.value.allMessages
        val classified = SmsClassifier.classifyMessages(
            rawMessages = raw,
            activeRules = _uiState.value.customRules,
            filterDuplicates = _uiState.value.filterDuplicates,
            filterOtps = _uiState.value.filterOtps,
            onlyExpiredOtps = _uiState.value.onlyExpiredOtps,
            filterPromotions = _uiState.value.filterPromotions,
            filterSpam = _uiState.value.filterSpam,
            duplicateKeepStrategy = _uiState.value.duplicateKeepStrategy
        )
        val clusters = SmsClassifier.extractDuplicateThreadClusters(classified)
        _uiState.update {
            it.copy(
                classifiedMessages = classified,
                duplicateThreadClusters = clusters
            )
        }
    }

    fun setDuplicateKeepStrategy(strategy: com.example.model.DuplicateKeepStrategy) {
        _uiState.update { it.copy(duplicateKeepStrategy = strategy) }
        reclassifyCurrentMessages()
    }

    fun toggleGroupByThreads(enabled: Boolean) {
        _uiState.update { it.copy(groupByThreadsInDuplicates = enabled) }
    }

    fun fixDuplicateThread(cluster: com.example.model.DuplicateThreadCluster) {
        viewModelScope.launch {
            val duplicateIds = cluster.duplicateCopies.map { it.id }.toSet()
            val duplicates = _uiState.value.classifiedMessages.filter { duplicateIds.contains(it.id) }

            if (duplicates.isEmpty()) return@launch

            if (_uiState.value.autoBackupBeforeClean) {
                cloudManager.createCloudBackup(
                    messages = duplicates,
                    rules = _uiState.value.customRules,
                    provider = _uiState.value.selectedCloudProvider
                )
            }

            // Move duplicate copies to quarantine
            val entities = duplicates.map { QuarantineEntity.fromSmsMessageItem(it) }
            dao.insertQuarantined(entities)

            // Remove only the duplicate copies, preserving master message in existing thread!
            val remaining = _uiState.value.allMessages.filterNot { duplicateIds.contains(it.id) }
            _uiState.update {
                it.copy(
                    allMessages = remaining,
                    snackbarMessage = "Fixed Thread #${cluster.threadId}: ${duplicates.size} duplicate(s) moved to Vault. Master copy kept."
                )
            }
            reclassifyCurrentMessages()
        }
    }

    fun fixAllDuplicateThreads() {
        viewModelScope.launch {
            val allDuplicates = _uiState.value.duplicateThreadClusters.flatMap { it.duplicateCopies }
            if (allDuplicates.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "No duplicate threads to fix") }
                return@launch
            }

            val duplicateIds = allDuplicates.map { it.id }.toSet()
            if (_uiState.value.autoBackupBeforeClean) {
                cloudManager.createCloudBackup(
                    messages = allDuplicates,
                    rules = _uiState.value.customRules,
                    provider = _uiState.value.selectedCloudProvider
                )
            }

            val entities = allDuplicates.map { QuarantineEntity.fromSmsMessageItem(it) }
            dao.insertQuarantined(entities)

            val remaining = _uiState.value.allMessages.filterNot { duplicateIds.contains(it.id) }
            _uiState.update {
                it.copy(
                    allMessages = remaining,
                    snackbarMessage = "Cleaned ${allDuplicates.size} duplicate message(s) across ${_uiState.value.duplicateThreadClusters.size} threads. Master copies preserved."
                )
            }
            reclassifyCurrentMessages()
        }
    }

    // Filter toggles
    fun toggleDuplicateFilter(enabled: Boolean) {
        _uiState.update { it.copy(filterDuplicates = enabled) }
        reclassifyCurrentMessages()
    }

    fun toggleOtpFilter(enabled: Boolean) {
        _uiState.update { it.copy(filterOtps = enabled) }
        reclassifyCurrentMessages()
    }

    fun toggleOnlyExpiredOtps(onlyExpired: Boolean) {
        _uiState.update { it.copy(onlyExpiredOtps = onlyExpired) }
        reclassifyCurrentMessages()
    }

    fun togglePromotionFilter(enabled: Boolean) {
        _uiState.update { it.copy(filterPromotions = enabled) }
        reclassifyCurrentMessages()
    }

    fun toggleSpamFilter(enabled: Boolean) {
        _uiState.update { it.copy(filterSpam = enabled) }
        reclassifyCurrentMessages()
    }

    fun setCategoryTab(category: SmsCategory) {
        _uiState.update { it.copy(selectedCategoryTab = category) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // Message selection
    fun toggleMessageSelection(messageId: String) {
        _uiState.update { state ->
            val updated = state.classifiedMessages.map {
                if (it.id == messageId) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(classifiedMessages = updated)
        }
    }

    fun selectAll(select: Boolean, category: SmsCategory) {
        _uiState.update { state ->
            val updated = state.classifiedMessages.map { msg ->
                val matchesCategory = when (category) {
                    SmsCategory.ALL -> msg.isCleanCandidate
                    else -> msg.category == category && msg.isCleanCandidate
                }
                if (matchesCategory) msg.copy(isSelected = select) else msg
            }
            state.copy(classifiedMessages = updated)
        }
    }

    fun selectFilteredMessages(messageIds: Set<String>, select: Boolean) {
        _uiState.update { state ->
            val updated = state.classifiedMessages.map { msg ->
                if (messageIds.contains(msg.id)) msg.copy(isSelected = select) else msg
            }
            state.copy(classifiedMessages = updated)
        }
    }

    // Quarantine & Cleaning
    fun quarantineSelectedMessages() {
        viewModelScope.launch {
            val selected = _uiState.value.classifiedMessages.filter { it.isSelected && it.isCleanCandidate }
            if (selected.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "No messages selected to clean") }
                return@launch
            }

            // Auto-backup to Cloud if enabled and authorized
            if (_uiState.value.autoBackupBeforeClean) {
                val selectedProvider = _uiState.value.selectedCloudProvider
                val serviceId = if (selectedProvider.contains("Dropbox", ignoreCase = true)) {
                    com.example.cloud.DeviceOAuthManager.SERVICE_DROPBOX
                } else {
                    com.example.cloud.DeviceOAuthManager.SERVICE_GOOGLE_DRIVE
                }
                val isAuth = _uiState.value.authorizedOAuthCredentials.any { it.serviceId == serviceId }
                if (isAuth) {
                    cloudManager.createCloudBackup(
                        messages = selected,
                        rules = _uiState.value.customRules,
                        provider = _uiState.value.selectedCloudProvider
                    )
                }
            }

            // Save to Room Quarantine table
            val entities = selected.map { QuarantineEntity.fromSmsMessageItem(it) }
            dao.insertQuarantined(entities)

            // Remove from active inbox
            val selectedIds = selected.map { it.id }.toSet()
            val remaining = _uiState.value.allMessages.filterNot { selectedIds.contains(it.id) }

            _uiState.update {
                it.copy(
                    allMessages = remaining,
                    snackbarMessage = "${selected.size} messages safely moved to Quarantine Vault"
                )
            }
            reclassifyCurrentMessages()
        }
    }

    // Permanent Deletion
    fun promptPermanentDelete(fromQuarantine: Boolean = false) {
        val targets = if (fromQuarantine) {
            _uiState.value.quarantinedMessages.filter { it.isSelected }
        } else {
            _uiState.value.classifiedMessages.filter { it.isSelected && it.isCleanCandidate }
        }

        if (targets.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Please select messages to delete") }
            return
        }

        _uiState.update {
            it.copy(
                showPermanentDeleteDialog = true,
                deleteTargetMessages = targets,
                isDeletingFromQuarantine = fromQuarantine
            )
        }
    }

    fun confirmPermanentDelete() {
        val targets = _uiState.value.deleteTargetMessages
        val isFromQuarantine = _uiState.value.isDeletingFromQuarantine
        val ids = targets.map { it.id }

        viewModelScope.launch {
            if (isFromQuarantine) {
                dao.deleteQuarantinedByIds(ids)
            } else {
                // If real device mode and is default SMS app, delete from Telephony provider
                if (!_uiState.value.isDemoMode && _uiState.value.isDefaultSmsApp) {
                    repository.deleteFromDeviceSms(ids)
                }
                // Remove from local memory list
                val idSet = ids.toSet()
                val remaining = _uiState.value.allMessages.filterNot { idSet.contains(it.id) }
                _uiState.update { it.copy(allMessages = remaining) }
                reclassifyCurrentMessages()
            }

            _uiState.update {
                it.copy(
                    showPermanentDeleteDialog = false,
                    deleteTargetMessages = emptyList(),
                    snackbarMessage = "Permanently deleted ${targets.size} messages"
                )
            }
        }
    }

    fun dismissPermanentDeleteDialog() {
        _uiState.update { it.copy(showPermanentDeleteDialog = false, deleteTargetMessages = emptyList()) }
    }

    // Restore from Quarantine
    fun restoreSelectedFromQuarantine() {
        viewModelScope.launch {
            val selected = _uiState.value.quarantinedMessages.filter { it.isSelected }
            if (selected.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "No quarantined messages selected to restore") }
                return@launch
            }

            val ids = selected.map { it.id }
            dao.deleteQuarantinedByIds(ids)

            // Add back to active inbox
            val restoredItems = selected.map { it.copy(isQuarantined = false, isSelected = false) }
            val newAll = (_uiState.value.allMessages + restoredItems).distinctBy { it.id }

            _uiState.update {
                it.copy(
                    allMessages = newAll,
                    snackbarMessage = "Restored ${selected.size} messages back to Inbox"
                )
            }
            reclassifyCurrentMessages()
        }
    }

    fun toggleQuarantineSelection(id: String) {
        _uiState.update { state ->
            val updated = state.quarantinedMessages.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(quarantinedMessages = updated)
        }
    }

    fun selectAllQuarantined(select: Boolean) {
        _uiState.update { state ->
            val updated = state.quarantinedMessages.map { it.copy(isSelected = select) }
            state.copy(quarantinedMessages = updated)
        }
    }

    fun emptyQuarantine() {
        viewModelScope.launch {
            val count = _uiState.value.quarantinedMessages.size
            dao.clearAllQuarantine()
            _uiState.update { it.copy(snackbarMessage = "Emptied Quarantine Vault ($count messages removed)") }
        }
    }

    // Custom Rules CRUD
    fun openAddRuleSheet() {
        _uiState.update { it.copy(showAddCustomRuleSheet = true) }
    }

    fun closeAddRuleSheet() {
        _uiState.update { it.copy(showAddCustomRuleSheet = false) }
    }

    fun saveCustomRule(rule: CustomFilterRule) {
        viewModelScope.launch {
            val entity = CustomRuleEntity(
                id = rule.id,
                name = rule.name,
                senderFilter = rule.senderFilter,
                keywords = rule.keywords,
                matchAnyKeyword = rule.matchAnyKeyword,
                olderThanDays = rule.olderThanDays,
                startDateEpoch = rule.startDateEpoch,
                endDateEpoch = rule.endDateEpoch,
                isEnabled = rule.isEnabled,
                createdAt = rule.createdAt
            )
            dao.insertRule(entity)
            _uiState.update {
                it.copy(
                    showAddCustomRuleSheet = false,
                    snackbarMessage = "Saved custom rule '${rule.name}'"
                )
            }
        }
    }

    fun toggleRuleEnabled(rule: CustomFilterRule) {
        viewModelScope.launch {
            val updated = rule.copy(isEnabled = !rule.isEnabled)
            val entity = CustomRuleEntity(
                id = updated.id,
                name = updated.name,
                senderFilter = updated.senderFilter,
                keywords = updated.keywords,
                matchAnyKeyword = updated.matchAnyKeyword,
                olderThanDays = updated.olderThanDays,
                startDateEpoch = updated.startDateEpoch,
                endDateEpoch = updated.endDateEpoch,
                isEnabled = updated.isEnabled,
                createdAt = updated.createdAt
            )
            dao.updateRule(entity)
        }
    }

    fun deleteCustomRule(ruleId: String) {
        viewModelScope.launch {
            dao.deleteRuleById(ruleId)
            _uiState.update { it.copy(snackbarMessage = "Deleted custom rule") }
        }
    }

    // Cloud Backup & Device OAuth Secret Management
    fun setCloudProvider(provider: String) {
        _uiState.update { it.copy(selectedCloudProvider = provider) }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        _uiState.update { it.copy(autoBackupBeforeClean = enabled) }
    }

    fun requestServiceAuthorization(serviceDef: com.example.model.OAuthServiceDefinition) {
        val account = _uiState.value.availableDeviceAccounts.firstOrNull() ?: serviceDef.defaultAccount
        _uiState.update {
            it.copy(
                serviceToAuthorize = serviceDef,
                selectedAccountForAuth = account
            )
        }
    }

    fun setSelectedAccountForAuth(account: String) {
        _uiState.update { it.copy(selectedAccountForAuth = account) }
    }

    fun dismissServiceAuthorization() {
        _uiState.update { it.copy(serviceToAuthorize = null, isAuthorizingService = false) }
    }

    fun confirmServiceAuthorization() {
        val serviceDef = _uiState.value.serviceToAuthorize ?: return
        val account = _uiState.value.selectedAccountForAuth
        _uiState.update { it.copy(isAuthorizingService = true) }

        viewModelScope.launch {
            val result = deviceOAuthManager.pullAndStoreDeviceOAuthSecret(
                serviceId = serviceDef.id,
                userConfirmedAuthorization = true,
                selectedAccount = account
            )

            result.onSuccess { cred ->
                _uiState.update {
                    it.copy(
                        serviceToAuthorize = null,
                        isAuthorizingService = false,
                        selectedCloudProvider = cred.serviceName,
                        snackbarMessage = "Authorized ${cred.serviceName}. On-device OAuth secret saved in local vault."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        serviceToAuthorize = null,
                        isAuthorizingService = false,
                        snackbarMessage = "Authorization failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun revokeServiceAuthorization(serviceId: String) {
        viewModelScope.launch {
            deviceOAuthManager.revokeAuthorization(serviceId)
            val serviceName = if (serviceId == com.example.cloud.DeviceOAuthManager.SERVICE_DROPBOX) "Dropbox" else "Google Drive"
            _uiState.update {
                it.copy(snackbarMessage = "Revoked $serviceName authorization. Secret purged from device.")
            }
        }
    }

    fun purgeAllDeviceSecrets() {
        viewModelScope.launch {
            deviceOAuthManager.purgeAllSecrets()
            _uiState.update {
                it.copy(snackbarMessage = "All local OAuth secrets permanently purged from device.")
            }
        }
    }

    fun performManualCloudBackup() {
        val selectedProvider = _uiState.value.selectedCloudProvider
        val serviceId = if (selectedProvider.contains("Dropbox", ignoreCase = true)) {
            com.example.cloud.DeviceOAuthManager.SERVICE_DROPBOX
        } else {
            com.example.cloud.DeviceOAuthManager.SERVICE_GOOGLE_DRIVE
        }

        val isAuthorized = _uiState.value.authorizedOAuthCredentials.any { it.serviceId == serviceId }
        if (!isAuthorized) {
            val serviceDef = deviceOAuthManager.supportedServices.find { it.id == serviceId }
            if (serviceDef != null) {
                requestServiceAuthorization(serviceDef)
            }
            _uiState.update {
                it.copy(snackbarMessage = "Please authorize $selectedProvider connection first. Secrets are pulled only when authorized.")
            }
            return
        }

        viewModelScope.launch {
            val messagesToBackup = if (_uiState.value.quarantinedMessages.isNotEmpty()) {
                _uiState.value.quarantinedMessages
            } else {
                _uiState.value.classifiedMessages.filter { it.isCleanCandidate }
            }

            if (messagesToBackup.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "No cleanable or quarantined messages to back up") }
                return@launch
            }

            val result = cloudManager.createCloudBackup(
                messages = messagesToBackup,
                rules = _uiState.value.customRules,
                provider = _uiState.value.selectedCloudProvider
            )

            result.onSuccess { record ->
                _uiState.update {
                    it.copy(snackbarMessage = "Backed up ${record.messageCount} messages to ${record.provider}")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(snackbarMessage = "Backup failed: ${err.message}")
                }
            }
        }
    }

    fun inspectBackupForRestore(record: BackupHistoryRecord) {
        val result = cloudManager.parseBackupJson(record.fileContentJson)
        result.onSuccess { payload ->
            _uiState.update {
                it.copy(
                    showRestoreCloudDialog = true,
                    activeRestorePayload = payload
                )
            }
        }.onFailure { err ->
            _uiState.update { it.copy(snackbarMessage = "Cannot parse backup: ${err.message}") }
        }
    }

    fun confirmRestoreFromCloud() {
        val payload = _uiState.value.activeRestorePayload ?: return
        viewModelScope.launch {
            // Restore messages into inbox
            val restoredItems = payload.messages.map { dto ->
                val proto = try {
                    com.example.model.MessageProtocol.valueOf(dto.protocol)
                } catch (_: Exception) {
                    if (dto.isRcsChat) com.example.model.MessageProtocol.RCS_CHAT else com.example.model.MessageProtocol.SMS
                }
                SmsMessageItem(
                    id = dto.id,
                    address = dto.address,
                    body = dto.body,
                    date = dto.date,
                    protocol = proto,
                    threadId = dto.threadId,
                    isRcsChat = dto.isRcsChat || proto == com.example.model.MessageProtocol.RCS_CHAT,
                    matchReason = "Restored from cloud (${payload.provider})",
                    otpCode = dto.otpCode,
                    isSelected = false
                )
            }

            // Restore rules
            for (rule in payload.rules) {
                val entity = CustomRuleEntity(
                    id = rule.id,
                    name = rule.name,
                    senderFilter = rule.senderFilter,
                    keywords = rule.keywords,
                    matchAnyKeyword = rule.matchAnyKeyword,
                    olderThanDays = rule.olderThanDays,
                    startDateEpoch = rule.startDateEpoch,
                    endDateEpoch = rule.endDateEpoch,
                    isEnabled = rule.isEnabled,
                    createdAt = rule.createdAt
                )
                dao.insertRule(entity)
            }

            val newAll = (_uiState.value.allMessages + restoredItems).distinctBy { it.id }
            _uiState.update {
                it.copy(
                    allMessages = newAll,
                    showRestoreCloudDialog = false,
                    activeRestorePayload = null,
                    snackbarMessage = "Successfully restored ${restoredItems.size} messages and ${payload.rules.size} rules!"
                )
            }
            reclassifyCurrentMessages()
        }
    }

    fun dismissRestoreCloudDialog() {
        _uiState.update { it.copy(showRestoreCloudDialog = false, activeRestorePayload = null) }
    }

    fun showMessageDetails(message: SmsMessageItem?) {
        _uiState.update { it.copy(detailMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
