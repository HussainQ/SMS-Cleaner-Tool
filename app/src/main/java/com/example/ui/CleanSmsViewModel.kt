package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.NavigationTab
import com.example.cloud.CloudBackupManager
import com.example.cloud.DeviceOAuthManager
import com.example.data.ClassificationSummary
import com.example.data.SmsClassifier
import com.example.data.SmsRepository
import com.example.data.local.AppDatabase
import com.example.data.local.CustomRuleEntity
import com.example.data.local.DeletedTestSnapshotEntity
import com.example.data.local.QuarantineEntity
import com.example.model.BackupHistoryRecord
import com.example.model.CloudBackupPayload
import com.example.model.ContactFilterMode
import com.example.model.ContactInfo
import com.example.model.ContactThreadEntry
import com.example.model.ContactThreadFilterScope
import com.example.model.ContactThreadHelper
import com.example.model.CustomFilterRule
import com.example.model.DuplicateKeepStrategy
import com.example.model.DuplicateThreadCluster
import com.example.model.LocalOAuthCredential
import com.example.model.MessageProtocol
import com.example.model.OAuthServiceDefinition
import com.example.model.PhoneNormalizer
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import com.example.telephony.TelephonyManagerHelper
import com.example.update.AppUpgradeManager
import com.example.update.GitHubUpdateRepository
import com.example.update.InAppApkInstaller
import com.example.update.InAppDownloadState
import com.example.update.InAppUpdateInstallPhase
import com.example.update.UpdateCheckResult
import com.example.update.UpdateCheckStatus
import com.example.update.UpgradeMigrationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CleanSmsUiState(
    val isDemoMode: Boolean = true,
    val hasReadSmsPermission: Boolean = false,
    val hasWriteSmsPermission: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val isDefaultSmsApp: Boolean = false,
    val previousDefaultSmsAppName: String = "Google Messages",
    val previousDefaultSmsPackage: String = "com.google.android.apps.messaging",
    val showPermissionRationaleDialog: Boolean = false,
    val showPermissionPermanentlyDeniedDialog: Boolean = false,
    val allMessages: List<SmsMessageItem> = emptyList(),
    val classifiedMessages: List<SmsMessageItem> = emptyList(),
    val summary: ClassificationSummary = ClassificationSummary(),
    val quarantinedMessages: List<SmsMessageItem> = emptyList(),
    val customRules: List<CustomFilterRule> = emptyList(),
    val backupHistory: List<BackupHistoryRecord> = emptyList(),
    // Testing delete recovery toggle & snapshots
    val testingRestoreDeletedEnabled: Boolean = true,
    val deletedTestMessages: List<SmsMessageItem> = emptyList(),
    // Contacts & Per-contact filter (thread-scoped for deduplication & cleanup)
    val availableContacts: List<ContactInfo> = emptyList(),
    val scannerContactEntries: List<ContactThreadEntry> = emptyList(),
    val filterByContactEnabled: Boolean = false,
    val selectedContacts: List<ContactInfo> = emptyList(),
    val contactFilterMode: ContactFilterMode = ContactFilterMode.TARGET_CONTACTS,
    val contactFilterScope: ContactThreadFilterScope = ContactThreadFilterScope(),
    val showMainContactPickerDialog: Boolean = false,
    // Vault contact group review filter
    val vaultContactEntries: List<ContactThreadEntry> = emptyList(),
    val vaultFilterByContactEnabled: Boolean = false,
    val vaultSelectedContacts: List<ContactInfo> = emptyList(),
    val vaultGroupByContact: Boolean = true,
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
    val duplicateKeepStrategy: DuplicateKeepStrategy = DuplicateKeepStrategy.KEEP_OLDEST,
    val groupByThreadsInDuplicates: Boolean = true,
    val duplicateThreadClusters: List<DuplicateThreadCluster> = emptyList(),
    // Cloud settings
    val selectedCloudProvider: String = "Google Drive",
    val isGoogleDriveConnected: Boolean = false,
    val isDropboxConnected: Boolean = false,
    val autoBackupBeforeClean: Boolean = true,
    // On-device OAuth credentials vault
    val authorizedOAuthCredentials: List<LocalOAuthCredential> = emptyList(),
    val availableDeviceAccounts: List<String> = listOf("shqsid@gmail.com"),
    val serviceToAuthorize: OAuthServiceDefinition? = null,
    val selectedAccountForAuth: String = "shqsid@gmail.com",
    val isAuthorizingService: Boolean = false,
    // Active dialogs
    val showPermanentDeleteDialog: Boolean = false,
    val deleteTargetMessages: List<SmsMessageItem> = emptyList(),
    val isDeletingFromQuarantine: Boolean = false,
    val showAddCustomRuleSheet: Boolean = false,
    val editingRule: CustomFilterRule? = null,
    val showRestoreCloudDialog: Boolean = false,
    val activeRestorePayload: CloudBackupPayload? = null,
    val detailMessage: SmsMessageItem? = null,
    val snackbarMessage: String? = null,
    // GitHub release update check, direct in-app APK download/install & multi-version upgrade state
    val gitHubRepoSlug: String = AppUpgradeManager.DEFAULT_GITHUB_REPO,
    val includePrereleases: Boolean = true,
    val updateCheckStatus: UpdateCheckStatus = UpdateCheckStatus.IDLE,
    val updateCheckResult: UpdateCheckResult? = null,
    val upgradeMigrationReport: UpgradeMigrationReport? = null,
    val showUpdatesDialog: Boolean = false,
    val inAppDownloadState: InAppDownloadState = InAppDownloadState(),
    // Hierarchical back-navigation state (top-level tab order of clicks + in-screen sub-tabs)
    val currentTab: NavigationTab = NavigationTab.SCANNER,
    val tabBackStack: List<NavigationTab> = listOf(NavigationTab.SCANNER),
    val vaultSubTab: Int = 0, // 0 = Quarantine, 1 = Deleted Test
    val rulesCloudSubTab: Int = 0 // 0 = Rules, 1 = Cloud Backup (compact phone mode)
) {
    val hasOpenOverlay: Boolean
        get() = showMainContactPickerDialog ||
            showPermanentDeleteDialog ||
            showAddCustomRuleSheet ||
            showRestoreCloudDialog ||
            detailMessage != null ||
            serviceToAuthorize != null ||
            showPermissionRationaleDialog ||
            showPermissionPermanentlyDeniedDialog ||
            showUpdatesDialog
}

class CleanSmsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: SmsRepository = SmsRepository(application),
    private val cloudManager: CloudBackupManager = CloudBackupManager(application),
    private val db: AppDatabase = AppDatabase.getInstance(application),
    val deviceOAuthManager: DeviceOAuthManager = DeviceOAuthManager(application, db.cleanSmsDao()),
    private val appUpgradeManager: AppUpgradeManager = AppUpgradeManager(application),
    private val updateRepository: GitHubUpdateRepository = GitHubUpdateRepository()
) : AndroidViewModel(application) {

    private val dao = db.cleanSmsDao()

    private val _uiState = MutableStateFlow(CleanSmsUiState())
    val uiState: StateFlow<CleanSmsUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var reclassifyJob: Job? = null
    private var updateCheckJob: Job? = null
    private var apkDownloadJob: Job? = null
    private var cachedDeviceContacts: List<ContactInfo> = emptyList()

    init {
        initializeUpgradeState()
        checkTelephonyState()
        observeLocalDatabase()
        loadInitialData()
        checkForGitHubUpdates(userInitiated = false)
    }

    private fun initializeUpgradeState() {
        val migrationReport = appUpgradeManager.ensureStateMigrated()
        val repoSlug = appUpgradeManager.getGitHubRepoSlug()
        val includePre = appUpgradeManager.getIncludePrereleases()
        _uiState.update {
            it.copy(
                gitHubRepoSlug = repoSlug,
                includePrereleases = includePre,
                upgradeMigrationReport = migrationReport
            )
        }
    }

    fun checkTelephonyState(): Boolean {
        val context = getApplication<Application>()
        val status = TelephonyManagerHelper.inspectDefaultSmsRoleStatus(context)
        _uiState.update {
            it.copy(
                isDefaultSmsApp = status.isDefaultSmsApp,
                previousDefaultSmsPackage = status.previousAppInfo.packageName,
                previousDefaultSmsAppName = status.previousAppInfo.appName
            )
        }
        return status.isDefaultSmsApp
    }

    fun setSmsPermissions(readGranted: Boolean, writeGranted: Boolean) {
        val prevRead = _uiState.value.hasReadSmsPermission
        val prevWrite = _uiState.value.hasWriteSmsPermission
        _uiState.update {
            it.copy(
                hasReadSmsPermission = readGranted,
                hasWriteSmsPermission = writeGranted,
                hasSmsPermission = readGranted,
                isDemoMode = if (readGranted) false else it.isDemoMode,
                showPermissionRationaleDialog = false
            )
        }
        if (readGranted && (!prevRead || !prevWrite)) {
            refreshMessages()
        }
    }

    fun setContactsPermission(granted: Boolean) {
        val prevGranted = _uiState.value.hasContactsPermission
        _uiState.update { it.copy(hasContactsPermission = granted) }
        if (granted && (!prevGranted || cachedDeviceContacts.isEmpty())) {
            refreshContactsAndRelink()
        }
    }

    fun setSmsPermissionGranted(granted: Boolean) {
        setSmsPermissions(readGranted = granted, writeGranted = granted)
    }

    fun openPermissionRationaleDialog() {
        _uiState.update { it.copy(showPermissionRationaleDialog = true) }
    }

    fun dismissPermissionRationaleDialog() {
        _uiState.update { it.copy(showPermissionRationaleDialog = false) }
    }

    fun openPermissionPermanentlyDeniedDialog() {
        _uiState.update { it.copy(showPermissionPermanentlyDeniedDialog = true) }
    }

    fun dismissPermissionPermanentlyDeniedDialog() {
        _uiState.update { it.copy(showPermissionPermanentlyDeniedDialog = false) }
    }

    fun setDemoMode(isDemo: Boolean) {
        if (_uiState.value.isDemoMode == isDemo) return
        _uiState.update { it.copy(isDemoMode = isDemo) }
        refreshMessages()
    }

    private fun observeLocalDatabase() {
        viewModelScope.launch {
            dao.getAllQuarantinedFlow().collect { entities ->
                val items = withContext(Dispatchers.Default) {
                    entities.map { it.toSmsMessageItem() }
                }
                _uiState.update { state ->
                    val combined = items + state.deletedTestMessages
                    val vaultEntries = ContactThreadHelper.buildVaultContactEntries(combined, state.availableContacts)
                    val validSelected = state.vaultSelectedContacts.filter { sel ->
                        vaultEntries.any { it.contact.id == sel.id }
                    }
                    state.copy(
                        quarantinedMessages = items,
                        vaultContactEntries = vaultEntries,
                        vaultSelectedContacts = validSelected
                    )
                }
            }
        }

        viewModelScope.launch {
            dao.getAllDeletedTestSnapshotsFlow().collect { entities ->
                val items = withContext(Dispatchers.Default) {
                    entities.map { it.toSmsMessageItem() }
                }
                _uiState.update { state ->
                    val combined = state.quarantinedMessages + items
                    val vaultEntries = ContactThreadHelper.buildVaultContactEntries(combined, state.availableContacts)
                    val validSelected = state.vaultSelectedContacts.filter { sel ->
                        vaultEntries.any { it.contact.id == sel.id }
                    }
                    state.copy(
                        deletedTestMessages = items,
                        vaultContactEntries = vaultEntries,
                        vaultSelectedContacts = validSelected
                    )
                }
            }
        }

        viewModelScope.launch {
            dao.getAllRulesFlow().collect { entities ->
                val rules = entities.map {
                    CustomFilterRule(
                        id = it.id,
                        name = it.name,
                        senderFilter = it.senderFilter,
                        contactFilter = it.contactFilter,
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
                reclassifyCurrentMessagesAsync()
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
                    val hasDrive = creds.any { it.serviceId == DeviceOAuthManager.SERVICE_GOOGLE_DRIVE }
                    val hasDropbox = creds.any { it.serviceId == DeviceOAuthManager.SERVICE_DROPBOX }
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
            val accounts = withContext(Dispatchers.IO) {
                deviceOAuthManager.getDetectedDeviceAccounts()
            }
            _uiState.update {
                it.copy(
                    availableDeviceAccounts = accounts,
                    selectedAccountForAuth = accounts.firstOrNull() ?: "shqsid@gmail.com"
                )
            }

            // Seed a default sample custom rule if empty
            val existingRules = withContext(Dispatchers.IO) { dao.getAllRules() }
            if (existingRules.isEmpty()) {
                val sampleRule = CustomRuleEntity(
                    id = "rule_default_1",
                    name = "Bank Alerts Older Than 30 Days",
                    senderFilter = "Bank",
                    contactFilter = "",
                    keywords = "withdrawn, debit, balance",
                    matchAnyKeyword = true,
                    olderThanDays = 30,
                    startDateEpoch = null,
                    endDateEpoch = null,
                    isEnabled = true,
                    createdAt = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) { dao.insertRule(sampleRule) }
            }
            refreshMessages()
        }
    }

    private fun refreshContactsAndRelink() {
        viewModelScope.launch {
            val (contacts, relinkedMessages, merged) = withContext(Dispatchers.IO) {
                val loadedContacts = if (_uiState.value.hasContactsPermission) {
                    repository.readDeviceContacts()
                } else {
                    emptyList()
                }
                cachedDeviceContacts = loadedContacts
                val lookup = repository.buildContactLookupIndex(loadedContacts)
                val currentRaw = _uiState.value.allMessages
                val relinked = repository.linkMessagesToContacts(currentRaw, lookup)
                val mergedContacts = repository.getMergedContactsForMessages(relinked, loadedContacts)
                Triple(loadedContacts, relinked, mergedContacts)
            }
            _uiState.update {
                it.copy(
                    allMessages = relinkedMessages,
                    availableContacts = merged
                )
            }
            if (contacts.isNotEmpty()) {
                reclassifyCurrentMessagesAsync()
            }
        }
    }

    fun refreshMessages() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val result = withContext(Dispatchers.IO) {
                val stateSnapshot = _uiState.value
                val deviceContacts = if (stateSnapshot.hasContactsPermission) {
                    repository.readDeviceContacts().also { cachedDeviceContacts = it }
                } else {
                    cachedDeviceContacts
                }
                val contactIndex = repository.buildContactLookupIndex(deviceContacts)

                val raw = if (stateSnapshot.isDemoMode) {
                    val demo = repository.getDemoSmsList()
                    if (contactIndex.isNotEmpty()) repository.linkMessagesToContacts(demo, contactIndex) else demo
                } else {
                    repository.readDeviceSms(contactIndex)
                }

                // Filter out quarantined messages from raw inbox
                val quarantinedIds = stateSnapshot.quarantinedMessages.mapTo(HashSet()) { it.id }
                val activeRaw = if (quarantinedIds.isEmpty()) raw else raw.filterNot { it.id in quarantinedIds }
                val mergedContacts = repository.getMergedContactsForMessages(activeRaw, deviceContacts)

                val contactScope = ContactThreadHelper.buildScope(
                    enabled = stateSnapshot.filterByContactEnabled,
                    mode = stateSnapshot.contactFilterMode,
                    selectedContacts = stateSnapshot.selectedContacts,
                    messages = activeRaw
                )
                val scannerEntries = ContactThreadHelper.buildEntriesForInbox(mergedContacts, activeRaw)
                val vaultEntries = ContactThreadHelper.buildVaultContactEntries(
                    stateSnapshot.quarantinedMessages + stateSnapshot.deletedTestMessages,
                    mergedContacts
                )
                val classified = SmsClassifier.classifyMessages(
                    rawMessages = activeRaw,
                    activeRules = stateSnapshot.customRules,
                    filterDuplicates = stateSnapshot.filterDuplicates,
                    filterOtps = stateSnapshot.filterOtps,
                    onlyExpiredOtps = stateSnapshot.onlyExpiredOtps,
                    filterPromotions = stateSnapshot.filterPromotions,
                    filterSpam = stateSnapshot.filterSpam,
                    duplicateKeepStrategy = stateSnapshot.duplicateKeepStrategy,
                    filterByContactEnabled = stateSnapshot.filterByContactEnabled,
                    selectedContactTokens = contactScope.matchTokens,
                    contactFilterMode = stateSnapshot.contactFilterMode,
                    selectedContactThreadIds = contactScope.selectedThreadIds
                )
                val summary = SmsClassifier.computeSummary(classified)
                val clusters = SmsClassifier.extractDuplicateThreadClusters(classified)

                RefreshResult(
                    activeRaw = activeRaw,
                    classified = classified,
                    summary = summary,
                    clusters = clusters,
                    mergedContacts = mergedContacts,
                    contactScope = contactScope,
                    scannerEntries = scannerEntries,
                    vaultEntries = vaultEntries
                )
            }

            _uiState.update {
                it.copy(
                    allMessages = result.activeRaw,
                    classifiedMessages = result.classified,
                    summary = result.summary,
                    duplicateThreadClusters = result.clusters,
                    availableContacts = result.mergedContacts,
                    contactFilterScope = result.contactScope,
                    scannerContactEntries = result.scannerEntries,
                    vaultContactEntries = result.vaultEntries,
                    isScanning = false
                )
            }
        }
    }

    private fun reclassifyCurrentMessagesAsync(debounceMs: Long = 0L) {
        reclassifyJob?.cancel()
        reclassifyJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val snapshot = _uiState.value
            val result = withContext(Dispatchers.Default) {
                val contactScope = ContactThreadHelper.buildScope(
                    enabled = snapshot.filterByContactEnabled,
                    mode = snapshot.contactFilterMode,
                    selectedContacts = snapshot.selectedContacts,
                    messages = snapshot.allMessages
                )
                val classified = SmsClassifier.classifyMessages(
                    rawMessages = snapshot.allMessages,
                    activeRules = snapshot.customRules,
                    filterDuplicates = snapshot.filterDuplicates,
                    filterOtps = snapshot.filterOtps,
                    onlyExpiredOtps = snapshot.onlyExpiredOtps,
                    filterPromotions = snapshot.filterPromotions,
                    filterSpam = snapshot.filterSpam,
                    duplicateKeepStrategy = snapshot.duplicateKeepStrategy,
                    filterByContactEnabled = snapshot.filterByContactEnabled,
                    selectedContactTokens = contactScope.matchTokens,
                    contactFilterMode = snapshot.contactFilterMode,
                    selectedContactThreadIds = contactScope.selectedThreadIds
                )
                val summary = SmsClassifier.computeSummary(classified)
                val clusters = SmsClassifier.extractDuplicateThreadClusters(classified)
                val scannerEntries = ContactThreadHelper.buildEntriesForInbox(snapshot.availableContacts, snapshot.allMessages)
                QuadResult(classified, summary, clusters, contactScope, scannerEntries)
            }
            _uiState.update {
                it.copy(
                    classifiedMessages = result.classified,
                    summary = result.summary,
                    duplicateThreadClusters = result.clusters,
                    contactFilterScope = result.contactScope,
                    scannerContactEntries = result.scannerEntries
                )
            }
        }
    }

    private fun buildSelectedContactTokens(contacts: List<ContactInfo>): Set<String> {
        return ContactThreadHelper.collectTokensForContacts(contacts)
    }

    // Per-Contact Filter Controls (Main Detection Rules & Thread-Scoped Deduplication)
    fun toggleContactFilter(enabled: Boolean) {
        _uiState.update { state ->
            val nextScope = ContactThreadHelper.buildScope(
                enabled = enabled,
                mode = state.contactFilterMode,
                selectedContacts = state.selectedContacts,
                messages = state.allMessages
            )
            state.copy(
                filterByContactEnabled = enabled,
                contactFilterScope = nextScope
            )
        }
        reclassifyCurrentMessagesAsync()
    }

    fun setContactFilterMode(mode: ContactFilterMode) {
        _uiState.update { state ->
            val nextScope = ContactThreadHelper.buildScope(
                enabled = state.filterByContactEnabled,
                mode = mode,
                selectedContacts = state.selectedContacts,
                messages = state.allMessages
            )
            state.copy(
                contactFilterMode = mode,
                contactFilterScope = nextScope
            )
        }
        reclassifyCurrentMessagesAsync()
    }

    fun toggleSelectedContact(contact: ContactInfo) {
        _uiState.update { state ->
            val exists = state.selectedContacts.any { it.id == contact.id }
            val updated = if (exists) {
                state.selectedContacts.filterNot { it.id == contact.id }
            } else {
                state.selectedContacts + contact
            }
            val nextEnabled = if (updated.isNotEmpty()) true else state.filterByContactEnabled
            val nextScope = ContactThreadHelper.buildScope(
                enabled = nextEnabled,
                mode = state.contactFilterMode,
                selectedContacts = updated,
                messages = state.allMessages
            )
            state.copy(
                selectedContacts = updated,
                filterByContactEnabled = nextEnabled,
                contactFilterScope = nextScope
            )
        }
        reclassifyCurrentMessagesAsync()
    }

    fun setSelectedContacts(contacts: List<ContactInfo>) {
        _uiState.update { state ->
            val nextEnabled = contacts.isNotEmpty()
            val nextScope = ContactThreadHelper.buildScope(
                enabled = nextEnabled,
                mode = state.contactFilterMode,
                selectedContacts = contacts,
                messages = state.allMessages
            )
            state.copy(
                selectedContacts = contacts,
                filterByContactEnabled = nextEnabled,
                contactFilterScope = nextScope
            )
        }
        reclassifyCurrentMessagesAsync()
    }

    fun selectAllFilterContacts() {
        _uiState.update { state ->
            val all = state.scannerContactEntries.map { it.contact }.ifEmpty { state.availableContacts }
            val nextScope = ContactThreadHelper.buildScope(
                enabled = all.isNotEmpty(),
                mode = state.contactFilterMode,
                selectedContacts = all,
                messages = state.allMessages
            )
            state.copy(
                selectedContacts = all,
                filterByContactEnabled = if (all.isNotEmpty()) true else state.filterByContactEnabled,
                contactFilterScope = nextScope
            )
        }
        reclassifyCurrentMessagesAsync()
    }

    fun clearSelectedContacts() {
        _uiState.update { state ->
            state.copy(
                selectedContacts = emptyList(),
                contactFilterScope = ContactThreadHelper.buildScope(
                    enabled = state.filterByContactEnabled,
                    mode = state.contactFilterMode,
                    selectedContacts = emptyList(),
                    messages = state.allMessages
                )
            )
        }
        reclassifyCurrentMessagesAsync()
    }

    // Vault Contact Group Review Filter Controls
    fun toggleVaultContactFilter(enabled: Boolean) {
        _uiState.update { it.copy(vaultFilterByContactEnabled = enabled) }
    }

    fun toggleVaultSelectedContact(contact: ContactInfo) {
        _uiState.update { state ->
            val exists = state.vaultSelectedContacts.any { it.id == contact.id }
            val updated = if (exists) {
                state.vaultSelectedContacts.filterNot { it.id == contact.id }
            } else {
                state.vaultSelectedContacts + contact
            }
            state.copy(
                vaultSelectedContacts = updated,
                vaultFilterByContactEnabled = updated.isNotEmpty()
            )
        }
    }

    fun selectAllVaultContacts() {
        _uiState.update { state ->
            val all = state.vaultContactEntries.map { it.contact }
            state.copy(
                vaultSelectedContacts = all,
                vaultFilterByContactEnabled = all.isNotEmpty()
            )
        }
    }

    fun clearVaultSelectedContacts() {
        _uiState.update {
            it.copy(
                vaultSelectedContacts = emptyList(),
                vaultFilterByContactEnabled = false
            )
        }
    }

    fun toggleVaultGroupByContact(grouped: Boolean) {
        _uiState.update { it.copy(vaultGroupByContact = grouped) }
    }

    fun selectVaultMessagesByIds(messageIds: Set<String>, select: Boolean, isDeletedTest: Boolean) {
        if (messageIds.isEmpty()) return
        _uiState.update { state ->
            if (isDeletedTest) {
                val updated = state.deletedTestMessages.map { msg ->
                    if (msg.id in messageIds) msg.copy(isSelected = select) else msg
                }
                state.copy(deletedTestMessages = updated)
            } else {
                val updated = state.quarantinedMessages.map { msg ->
                    if (msg.id in messageIds) msg.copy(isSelected = select) else msg
                }
                state.copy(quarantinedMessages = updated)
            }
        }
    }

    fun openMainContactPicker() {
        _uiState.update { it.copy(showMainContactPickerDialog = true) }
    }

    fun closeMainContactPicker() {
        _uiState.update { it.copy(showMainContactPickerDialog = false) }
    }

    // Testing Feature: Deleted Messages Recovery Toggle
    fun toggleTestingRestoreDeleted(enabled: Boolean) {
        _uiState.update {
            it.copy(
                testingRestoreDeletedEnabled = enabled,
                snackbarMessage = if (enabled) {
                    "Testing Delete Recovery ON: Deleted messages can be restored from Vault"
                } else {
                    "Testing Delete Recovery OFF: Deletions are strictly permanent"
                }
            )
        }
    }

    fun toggleDeletedTestSelection(id: String) {
        _uiState.update { state ->
            val updated = state.deletedTestMessages.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(deletedTestMessages = updated)
        }
    }

    fun selectAllDeletedTest(select: Boolean) {
        _uiState.update { state ->
            val updated = state.deletedTestMessages.map { it.copy(isSelected = select) }
            state.copy(deletedTestMessages = updated)
        }
    }

    fun restoreSelectedDeletedTestMessages() {
        viewModelScope.launch {
            val selected = _uiState.value.deletedTestMessages.filter { it.isSelected }
            if (selected.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "Select deleted test messages to restore") }
                return@launch
            }

            val ids = selected.map { it.id }
            withContext(Dispatchers.IO) {
                if (!_uiState.value.isDemoMode && _uiState.value.isDefaultSmsApp) {
                    repository.restoreMessagesToDeviceSms(selected)
                }
                dao.deleteTestSnapshotsByIds(ids)
            }

            val restoredItems = selected.map {
                it.copy(
                    isQuarantined = false,
                    isSelected = false,
                    matchReason = if (it.matchReason.isBlank()) "Restored (Testing Recovery)" else it.matchReason
                )
            }
            val newAll = (_uiState.value.allMessages + restoredItems).distinctBy { it.id }
            _uiState.update {
                it.copy(
                    allMessages = newAll,
                    snackbarMessage = "Restored ${selected.size} permanently deleted message(s) back to Inbox for testing"
                )
            }
            reclassifyCurrentMessagesAsync()
        }
    }

    fun clearDeletedTestSnapshots() {
        viewModelScope.launch {
            val count = _uiState.value.deletedTestMessages.size
            withContext(Dispatchers.IO) {
                dao.clearAllDeletedTestSnapshots()
            }
            _uiState.update { it.copy(snackbarMessage = "Cleared $count deleted test snapshot(s)") }
        }
    }

    fun setDuplicateKeepStrategy(strategy: DuplicateKeepStrategy) {
        _uiState.update { it.copy(duplicateKeepStrategy = strategy) }
        reclassifyCurrentMessagesAsync()
    }

    fun toggleGroupByThreads(enabled: Boolean) {
        _uiState.update { it.copy(groupByThreadsInDuplicates = enabled) }
    }

    fun fixDuplicateThread(cluster: DuplicateThreadCluster) {
        viewModelScope.launch {
            val duplicateIds = cluster.duplicateCopies.mapTo(HashSet()) { it.id }
            val duplicates = _uiState.value.classifiedMessages.filter { it.id in duplicateIds }

            if (duplicates.isEmpty()) return@launch

            withContext(Dispatchers.IO) {
                if (_uiState.value.autoBackupBeforeClean) {
                    cloudManager.createCloudBackup(
                        messages = duplicates,
                        rules = _uiState.value.customRules,
                        provider = _uiState.value.selectedCloudProvider
                    )
                }
                val entities = duplicates.map { QuarantineEntity.fromSmsMessageItem(it) }
                dao.insertQuarantined(entities)
            }

            val remaining = _uiState.value.allMessages.filterNot { it.id in duplicateIds }
            _uiState.update {
                it.copy(
                    allMessages = remaining,
                    snackbarMessage = "Fixed Thread #${cluster.threadId}: ${duplicates.size} duplicate(s) moved to Vault. Master copy kept."
                )
            }
            reclassifyCurrentMessagesAsync()
        }
    }

    fun fixAllDuplicateThreads() {
        viewModelScope.launch {
            val allDuplicates = _uiState.value.duplicateThreadClusters.flatMap { it.duplicateCopies }
            if (allDuplicates.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "No duplicate threads to fix") }
                return@launch
            }

            val duplicateIds = allDuplicates.mapTo(HashSet()) { it.id }
            val clusterCount = _uiState.value.duplicateThreadClusters.size
            withContext(Dispatchers.IO) {
                if (_uiState.value.autoBackupBeforeClean) {
                    cloudManager.createCloudBackup(
                        messages = allDuplicates,
                        rules = _uiState.value.customRules,
                        provider = _uiState.value.selectedCloudProvider
                    )
                }
                val entities = allDuplicates.map { QuarantineEntity.fromSmsMessageItem(it) }
                dao.insertQuarantined(entities)
            }

            val remaining = _uiState.value.allMessages.filterNot { it.id in duplicateIds }
            _uiState.update {
                it.copy(
                    allMessages = remaining,
                    snackbarMessage = "Cleaned ${allDuplicates.size} duplicate message(s) across $clusterCount threads. Master copies preserved."
                )
            }
            reclassifyCurrentMessagesAsync()
        }
    }

    // Filter toggles
    fun toggleDuplicateFilter(enabled: Boolean) {
        _uiState.update { it.copy(filterDuplicates = enabled) }
        reclassifyCurrentMessagesAsync()
    }

    fun toggleOtpFilter(enabled: Boolean) {
        _uiState.update { it.copy(filterOtps = enabled) }
        reclassifyCurrentMessagesAsync()
    }

    fun toggleOnlyExpiredOtps(onlyExpired: Boolean) {
        _uiState.update { it.copy(onlyExpiredOtps = onlyExpired) }
        reclassifyCurrentMessagesAsync()
    }

    fun togglePromotionFilter(enabled: Boolean) {
        _uiState.update { it.copy(filterPromotions = enabled) }
        reclassifyCurrentMessagesAsync()
    }

    fun toggleSpamFilter(enabled: Boolean) {
        _uiState.update { it.copy(filterSpam = enabled) }
        reclassifyCurrentMessagesAsync()
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
                if (msg.id in messageIds) msg.copy(isSelected = select) else msg
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

            withContext(Dispatchers.IO) {
                if (_uiState.value.autoBackupBeforeClean) {
                    val selectedProvider = _uiState.value.selectedCloudProvider
                    val serviceId = if (selectedProvider.contains("Dropbox", ignoreCase = true)) {
                        DeviceOAuthManager.SERVICE_DROPBOX
                    } else {
                        DeviceOAuthManager.SERVICE_GOOGLE_DRIVE
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

                val entities = selected.map { QuarantineEntity.fromSmsMessageItem(it) }
                dao.insertQuarantined(entities)
            }

            val selectedIds = selected.mapTo(HashSet()) { it.id }
            val remaining = _uiState.value.allMessages.filterNot { it.id in selectedIds }

            _uiState.update {
                it.copy(
                    allMessages = remaining,
                    snackbarMessage = "${selected.size} messages safely moved to Quarantine Vault"
                )
            }
            reclassifyCurrentMessagesAsync()
        }
    }

    // Permanent Deletion (with optional Testing Recovery Snapshot)
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
        val saveTestingSnapshot = _uiState.value.testingRestoreDeletedEnabled
        val ids = targets.map { it.id }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // If Testing Delete Recovery toggle is ON, save a local recovery snapshot first
                if (saveTestingSnapshot && targets.isNotEmpty()) {
                    val snapshots = targets.map { DeletedTestSnapshotEntity.fromSmsMessageItem(it) }
                    dao.insertDeletedTestSnapshots(snapshots)
                }

                if (isFromQuarantine) {
                    dao.deleteQuarantinedByIds(ids)
                    // Also delete from device Telephony if in real device mode & default SMS app
                    if (!_uiState.value.isDemoMode && _uiState.value.isDefaultSmsApp) {
                        repository.deleteFromDeviceSms(ids)
                    }
                } else {
                    if (!_uiState.value.isDemoMode && _uiState.value.isDefaultSmsApp) {
                        repository.deleteFromDeviceSms(ids)
                    }
                }
            }

            if (!isFromQuarantine) {
                val idSet = ids.toHashSet()
                val remaining = _uiState.value.allMessages.filterNot { it.id in idSet }
                _uiState.update { it.copy(allMessages = remaining) }
                reclassifyCurrentMessagesAsync()
            }

            val suffix = if (saveTestingSnapshot) " (Recoverable via Vault Testing Toggle)" else ""
            _uiState.update {
                it.copy(
                    showPermanentDeleteDialog = false,
                    deleteTargetMessages = emptyList(),
                    snackbarMessage = "Permanently deleted ${targets.size} message(s)$suffix"
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
            withContext(Dispatchers.IO) {
                dao.deleteQuarantinedByIds(ids)
            }

            val restoredItems = selected.map { it.copy(isQuarantined = false, isSelected = false) }
            val newAll = (_uiState.value.allMessages + restoredItems).distinctBy { it.id }

            _uiState.update {
                it.copy(
                    allMessages = newAll,
                    snackbarMessage = "Restored ${selected.size} messages back to Inbox"
                )
            }
            reclassifyCurrentMessagesAsync()
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
            val currentQuarantined = _uiState.value.quarantinedMessages
            val count = currentQuarantined.size
            withContext(Dispatchers.IO) {
                if (_uiState.value.testingRestoreDeletedEnabled && currentQuarantined.isNotEmpty()) {
                    dao.insertDeletedTestSnapshots(
                        currentQuarantined.map { DeletedTestSnapshotEntity.fromSmsMessageItem(it) }
                    )
                }
                dao.clearAllQuarantine()
            }
            _uiState.update { it.copy(snackbarMessage = "Emptied Quarantine Vault ($count messages removed)") }
        }
    }

    // Custom Rules CRUD (Create + Edit + Toggle + Delete)
    fun openAddRuleSheet() {
        _uiState.update { it.copy(showAddCustomRuleSheet = true, editingRule = null) }
    }

    fun openEditRuleSheet(rule: CustomFilterRule) {
        _uiState.update { it.copy(showAddCustomRuleSheet = true, editingRule = rule) }
    }

    fun closeAddRuleSheet() {
        _uiState.update { it.copy(showAddCustomRuleSheet = false, editingRule = null) }
    }

    fun saveCustomRule(rule: CustomFilterRule) {
        val isEditing = _uiState.value.editingRule != null
        viewModelScope.launch {
            val entity = CustomRuleEntity(
                id = rule.id,
                name = rule.name,
                senderFilter = rule.senderFilter,
                contactFilter = rule.contactFilter,
                keywords = rule.keywords,
                matchAnyKeyword = rule.matchAnyKeyword,
                olderThanDays = rule.olderThanDays,
                startDateEpoch = rule.startDateEpoch,
                endDateEpoch = rule.endDateEpoch,
                isEnabled = rule.isEnabled,
                createdAt = rule.createdAt
            )
            withContext(Dispatchers.IO) {
                dao.insertRule(entity)
            }
            _uiState.update {
                it.copy(
                    showAddCustomRuleSheet = false,
                    editingRule = null,
                    snackbarMessage = if (isEditing) "Updated rule '${rule.name}'" else "Saved custom rule '${rule.name}'"
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
                contactFilter = updated.contactFilter,
                keywords = updated.keywords,
                matchAnyKeyword = updated.matchAnyKeyword,
                olderThanDays = updated.olderThanDays,
                startDateEpoch = updated.startDateEpoch,
                endDateEpoch = updated.endDateEpoch,
                isEnabled = updated.isEnabled,
                createdAt = updated.createdAt
            )
            withContext(Dispatchers.IO) {
                dao.updateRule(entity)
            }
        }
    }

    fun deleteCustomRule(ruleId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteRuleById(ruleId)
            }
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

    fun requestServiceAuthorization(serviceDef: OAuthServiceDefinition) {
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
            val serviceName = if (serviceId == DeviceOAuthManager.SERVICE_DROPBOX) "Dropbox" else "Google Drive"
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
            DeviceOAuthManager.SERVICE_DROPBOX
        } else {
            DeviceOAuthManager.SERVICE_GOOGLE_DRIVE
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
            val restoredItems = payload.messages.map { dto ->
                val proto = try {
                    MessageProtocol.valueOf(dto.protocol)
                } catch (_: Exception) {
                    if (dto.isRcsChat) MessageProtocol.RCS_CHAT else MessageProtocol.SMS
                }
                SmsMessageItem(
                    id = dto.id,
                    address = dto.address,
                    body = dto.body,
                    date = dto.date,
                    protocol = proto,
                    threadId = dto.threadId,
                    isRcsChat = dto.isRcsChat || proto == MessageProtocol.RCS_CHAT,
                    matchReason = "Restored from cloud (${payload.provider})",
                    otpCode = dto.otpCode,
                    isSelected = false
                )
            }

            withContext(Dispatchers.IO) {
                for (rule in payload.rules) {
                    val entity = CustomRuleEntity(
                        id = rule.id,
                        name = rule.name,
                        senderFilter = rule.senderFilter,
                        contactFilter = rule.contactFilter,
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
            reclassifyCurrentMessagesAsync()
        }
    }

    fun dismissRestoreCloudDialog() {
        _uiState.update { it.copy(showRestoreCloudDialog = false, activeRestorePayload = null) }
    }

    fun showMessageDetails(message: SmsMessageItem?) {
        _uiState.update { it.copy(detailMessage = message) }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // GitHub Release Update Checker & Multi-Version Upgrade Path Actions
    fun openUpdatesDialog() {
        _uiState.update { it.copy(showUpdatesDialog = true) }
        if (_uiState.value.updateCheckStatus == UpdateCheckStatus.IDLE ||
            _uiState.value.updateCheckStatus == UpdateCheckStatus.ERROR
        ) {
            checkForGitHubUpdates(userInitiated = false)
        }
    }

    fun dismissUpdatesDialog() {
        _uiState.update { it.copy(showUpdatesDialog = false) }
    }

    fun updateGitHubRepoSlug(rawSlug: String) {
        val normalized = com.example.update.SemanticVersion.normalizeRepoSlug(rawSlug)
        if (normalized == null) {
            _uiState.update {
                it.copy(snackbarMessage = "Enter a valid GitHub repository as owner/repo")
            }
            return
        }
        val slug = "${normalized.first}/${normalized.second}"
        appUpgradeManager.setGitHubRepoSlug(slug)
        _uiState.update { it.copy(gitHubRepoSlug = slug) }
        checkForGitHubUpdates(userInitiated = true)
    }

    fun toggleIncludePrereleases(include: Boolean) {
        appUpgradeManager.setIncludePrereleases(include)
        _uiState.update { it.copy(includePrereleases = include) }
        checkForGitHubUpdates(userInitiated = true)
    }

    fun checkForGitHubUpdates(userInitiated: Boolean = true) {
        updateCheckJob?.cancel()
        val repoSlug = _uiState.value.gitHubRepoSlug
        val includePre = _uiState.value.includePrereleases

        _uiState.update {
            it.copy(updateCheckStatus = UpdateCheckStatus.CHECKING)
        }

        updateCheckJob = viewModelScope.launch {
            val result = updateRepository.checkForUpdates(
                repoSlug = repoSlug,
                installedVersionName = com.example.BuildConfig.VERSION_NAME,
                installedVersionCode = com.example.BuildConfig.VERSION_CODE,
                includePrereleases = includePre
            )

            result.onSuccess { checkResult ->
                val newStatus = if (checkResult.isUpdateAvailable) {
                    UpdateCheckStatus.UPDATE_AVAILABLE
                } else {
                    UpdateCheckStatus.UP_TO_DATE
                }
                val snackbar = if (userInitiated) {
                    if (checkResult.isUpdateAvailable) {
                        val targetTag = checkResult.latestRelease?.tagName ?: "latest"
                        val steps = checkResult.versionsBehindCount
                        if (steps > 1) {
                            "Update available: $targetTag ($steps versions ahead of v${checkResult.installedVersionName})"
                        } else {
                            "Update available: $targetTag"
                        }
                    } else {
                        "CleanSMS v${checkResult.installedVersionName} is already the latest release."
                    }
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        updateCheckStatus = newStatus,
                        updateCheckResult = checkResult,
                        snackbarMessage = snackbar ?: it.snackbarMessage
                    )
                }
            }.onFailure { err ->
                val msg = err.message ?: "Unable to reach GitHub Releases."
                    _uiState.update {
                        it.copy(
                            updateCheckStatus = if (userInitiated) UpdateCheckStatus.ERROR else UpdateCheckStatus.IDLE,
                            updateCheckResult = UpdateCheckResult(
                                repoSlug = repoSlug,
                                installedVersionName = com.example.BuildConfig.VERSION_NAME,
                                installedVersionCode = com.example.BuildConfig.VERSION_CODE,
                                includePrereleases = includePre,
                                latestRelease = it.updateCheckResult?.latestRelease,
                                upgradeChain = emptyList(),
                                isUpdateAvailable = false,
                                errorMessage = msg
                            ),
                            snackbarMessage = if (userInitiated) msg else it.snackbarMessage
                        )
                    }
                }
            }
        }

    /**
     * Downloads the release APK directly into app-private cache storage, verifies its SHA-256 digest
     * if available, and transitions cleanly to install (or prompts for unknown-app-sources permission).
     */
    fun downloadAndInstallUpdate() {
        val result = _uiState.value.updateCheckResult
        val apkAsset = result?.latestRelease?.apkAsset
        if (apkAsset == null || apkAsset.downloadUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    inAppDownloadState = InAppDownloadState(
                        phase = InAppUpdateInstallPhase.FAILED,
                        errorMessage = "No downloadable APK asset found on this release."
                    ),
                    snackbarMessage = "No APK asset attached to this release."
                )
            }
            return
        }

        val appContext = getApplication<Application>()
        val destFile = InAppApkInstaller.resolveApkDestination(appContext, apkAsset.name)
        val expectedSha = result.verifiedSha256 ?: apkAsset.sha256Checksum

        apkDownloadJob?.cancel()
        _uiState.update {
            it.copy(
                inAppDownloadState = InAppDownloadState(
                    phase = InAppUpdateInstallPhase.DOWNLOADING,
                    downloadedBytes = 0L,
                    totalBytes = apkAsset.sizeBytes,
                    progressFraction = 0f,
                    apkFilePath = destFile.absolutePath,
                    apkFileName = apkAsset.name
                )
            )
        }

        apkDownloadJob = viewModelScope.launch {
            InAppApkInstaller.cleanupStaleApks(appContext, keepFileName = destFile.name)

            val downloadResult = updateRepository.downloadApkUpdate(
                downloadUrl = apkAsset.downloadUrl,
                destinationFile = destFile,
                expectedSha256 = expectedSha,
                onProgress = { downloaded, total ->
                    val effectiveTotal = if (total > 0L) total else apkAsset.sizeBytes
                    val fraction = if (effectiveTotal > 0L) {
                        (downloaded.toFloat() / effectiveTotal.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    _uiState.update { state ->
                        if (state.inAppDownloadState.phase == InAppUpdateInstallPhase.DOWNLOADING) {
                            state.copy(
                                inAppDownloadState = state.inAppDownloadState.copy(
                                    downloadedBytes = downloaded,
                                    totalBytes = effectiveTotal,
                                    progressFraction = fraction
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
            )

            downloadResult.onSuccess { downloaded ->
                val canInstall = InAppApkInstaller.canInstallUnknownApps(appContext)
                val nextPhase = if (canInstall) {
                    InAppUpdateInstallPhase.READY_TO_INSTALL
                } else {
                    InAppUpdateInstallPhase.PERMISSION_REQUIRED
                }
                _uiState.update {
                    it.copy(
                        inAppDownloadState = InAppDownloadState(
                            phase = nextPhase,
                            downloadedBytes = downloaded.bytesDownloaded,
                            totalBytes = downloaded.bytesDownloaded,
                            progressFraction = 1f,
                            apkFilePath = downloaded.filePath,
                            apkFileName = downloaded.fileName,
                            verifiedSha256 = downloaded.computedSha256,
                            sha256Matched = downloaded.sha256Verified,
                            errorMessage = null
                        ),
                        snackbarMessage = if (canInstall) {
                            "Update APK verified. Launching system installer..."
                        } else {
                            "Download verified. Allow 'Install unknown apps' to finish updating."
                        }
                    )
                }
            }.onFailure { err ->
                val msg = err.message ?: "Unable to download update APK."
                _uiState.update {
                    it.copy(
                        inAppDownloadState = InAppDownloadState(
                            phase = InAppUpdateInstallPhase.FAILED,
                            errorMessage = msg
                        ),
                        snackbarMessage = msg
                    )
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        apkDownloadJob?.cancel()
        apkDownloadJob = null
        _uiState.update {
            it.copy(
                inAppDownloadState = InAppDownloadState(phase = InAppUpdateInstallPhase.IDLE),
                snackbarMessage = "Update download cancelled."
            )
        }
    }

    fun onInstallPermissionReturned() {
        val current = _uiState.value.inAppDownloadState
        val apkPath = current.apkFilePath
        if (apkPath.isNullOrBlank()) return
        val canInstall = InAppApkInstaller.canInstallUnknownApps(getApplication())
        if (canInstall) {
            _uiState.update {
                it.copy(
                    inAppDownloadState = it.inAppDownloadState.copy(
                        phase = InAppUpdateInstallPhase.READY_TO_INSTALL,
                        errorMessage = null
                    )
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    inAppDownloadState = it.inAppDownloadState.copy(
                        phase = InAppUpdateInstallPhase.PERMISSION_REQUIRED,
                        errorMessage = "Permission to install unknown apps is still disabled in Android Settings."
                    )
                )
            }
        }
    }

    fun triggerPendingApkInstall() {
        val current = _uiState.value.inAppDownloadState
        if (current.apkFilePath.isNullOrBlank()) return
        val canInstall = InAppApkInstaller.canInstallUnknownApps(getApplication())
        _uiState.update {
            it.copy(
                inAppDownloadState = it.inAppDownloadState.copy(
                    phase = if (canInstall) InAppUpdateInstallPhase.READY_TO_INSTALL else InAppUpdateInstallPhase.PERMISSION_REQUIRED
                )
            )
        }
    }

    fun markApkInstallLaunched() {
        _uiState.update { state ->
            state.copy(
                inAppDownloadState = state.inAppDownloadState.copy(
                    phase = InAppUpdateInstallPhase.INSTALL_LAUNCHED,
                    errorMessage = null
                )
            )
        }
    }

    fun markApkInstallFailed(message: String) {
        _uiState.update { state ->
            state.copy(
                inAppDownloadState = state.inAppDownloadState.copy(
                    phase = InAppUpdateInstallPhase.FAILED,
                    errorMessage = message
                ),
                snackbarMessage = message
            )
        }
    }

    // Hierarchical Navigation, Tab History (Order of Clicks), and Consistent Back Handling
    fun navigateToTab(tab: NavigationTab) {
        _uiState.update { state ->
            if (state.currentTab == tab) return@update state
            val nextStack = if (tab == NavigationTab.SCANNER) {
                listOf(NavigationTab.SCANNER)
            } else {
                val withoutTab = state.tabBackStack.filterNot { it == tab }
                val anchored = if (withoutTab.firstOrNull() == NavigationTab.SCANNER) {
                    withoutTab
                } else {
                    listOf(NavigationTab.SCANNER) + withoutTab
                }
                anchored + tab
            }
            state.copy(
                currentTab = tab,
                tabBackStack = nextStack
            )
        }
    }

    fun setVaultSubTab(tabIndex: Int) {
        val normalized = tabIndex.coerceIn(0, 1)
        _uiState.update { it.copy(vaultSubTab = normalized) }
    }

    fun setRulesCloudSubTab(tabIndex: Int) {
        val normalized = tabIndex.coerceIn(0, 1)
        _uiState.update { it.copy(rulesCloudSubTab = normalized) }
    }

    fun dismissTopOverlay(): Boolean {
        val state = _uiState.value
        return when {
            state.showMainContactPickerDialog -> {
                closeMainContactPicker()
                true
            }
            state.showPermanentDeleteDialog -> {
                dismissPermanentDeleteDialog()
                true
            }
            state.showAddCustomRuleSheet -> {
                closeAddRuleSheet()
                true
            }
            state.showRestoreCloudDialog -> {
                dismissRestoreCloudDialog()
                true
            }
            state.detailMessage != null -> {
                showMessageDetails(null)
                true
            }
            state.serviceToAuthorize != null -> {
                if (!state.isAuthorizingService) {
                    dismissServiceAuthorization()
                }
                true
            }
            state.showPermissionRationaleDialog -> {
                dismissPermissionRationaleDialog()
                true
            }
            state.showPermissionPermanentlyDeniedDialog -> {
                dismissPermissionPermanentlyDeniedDialog()
                true
            }
            state.showUpdatesDialog -> {
                dismissUpdatesDialog()
                true
            }
            else -> false
        }
    }

    fun navigateBackTab(): Boolean {
        var navigated = false
        _uiState.update { state ->
            when {
                state.tabBackStack.size > 1 -> {
                    val remaining = state.tabBackStack.dropLast(1)
                    navigated = true
                    state.copy(
                        currentTab = remaining.last(),
                        tabBackStack = remaining
                    )
                }
                state.currentTab != NavigationTab.SCANNER -> {
                    navigated = true
                    state.copy(
                        currentTab = NavigationTab.SCANNER,
                        tabBackStack = listOf(NavigationTab.SCANNER)
                    )
                }
                else -> state
            }
        }
        return navigated
    }

    /**
     * Unified back-navigation handler following hierarchy:
     * Window Overlays -> In-screen Search/Category/Sub-Tab -> Tab Click-Order Stack -> Exit (returns false).
     */
    fun handleBackPress(isRulesCloudCompact: Boolean = true): Boolean {
        if (dismissTopOverlay()) return true

        val state = _uiState.value
        when (state.currentTab) {
            NavigationTab.PREVIEW -> {
                if (state.searchQuery.isNotEmpty()) {
                    _uiState.update { it.copy(searchQuery = "") }
                    return true
                }
                if (state.selectedCategoryTab != SmsCategory.ALL) {
                    _uiState.update { it.copy(selectedCategoryTab = SmsCategory.ALL) }
                    return true
                }
            }
            NavigationTab.VAULT -> {
                if (state.vaultSubTab != 0) {
                    _uiState.update { it.copy(vaultSubTab = 0) }
                    return true
                }
            }
            NavigationTab.RULES_CLOUD -> {
                if (isRulesCloudCompact && state.rulesCloudSubTab != 0) {
                    _uiState.update { it.copy(rulesCloudSubTab = 0) }
                    return true
                }
            }
            NavigationTab.SCANNER -> Unit
        }

        return navigateBackTab()
    }

    private data class RefreshResult(
        val activeRaw: List<SmsMessageItem>,
        val classified: List<SmsMessageItem>,
        val summary: ClassificationSummary,
        val clusters: List<DuplicateThreadCluster>,
        val mergedContacts: List<ContactInfo>,
        val contactScope: ContactThreadFilterScope,
        val scannerEntries: List<ContactThreadEntry>,
        val vaultEntries: List<ContactThreadEntry>
    )

    private data class QuadResult(
        val classified: List<SmsMessageItem>,
        val summary: ClassificationSummary,
        val clusters: List<DuplicateThreadCluster>,
        val contactScope: ContactThreadFilterScope,
        val scannerEntries: List<ContactThreadEntry>
    )

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CleanSmsViewModel(application) as T
                }
            }
    }
}
