package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ContactThreadHelper
import com.example.model.SmsMessageItem
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.components.ContactFilterChecklist
import com.example.ui.components.MessageCard
import com.example.ui.theme.*
import kotlin.math.max

@Composable
fun VaultRestoreScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestContactsPermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedVaultTab = uiState.vaultSubTab // 0 = Quarantine, 1 = Recently Deleted
    var showContactFilterPanel by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.vaultFilterByContactEnabled) {
        if (uiState.vaultFilterByContactEnabled) {
            showContactFilterPanel = true
        }
    }

    BackHandler(enabled = !uiState.hasOpenOverlay && (showContactFilterPanel || selectedVaultTab != 0)) {
        if (showContactFilterPanel) {
            showContactFilterPanel = false
        } else {
            viewModel.setVaultSubTab(0)
        }
    }

    val quarantined = uiState.quarantinedMessages
    val deletedTest = uiState.deletedTestMessages

    // Driven directly by Room @Query + Coroutine Flow on background IO dispatcher
    val activeVaultFilter = uiState.vaultFilterByContactEnabled && uiState.vaultSelectedContacts.isNotEmpty()
    val visibleQuarantined = if (activeVaultFilter) uiState.filteredQuarantinedMessages else quarantined
    val visibleDeletedTest = if (activeVaultFilter) uiState.filteredDeletedTestMessages else deletedTest

    val visibleQuarantinedSelectedCount = remember(visibleQuarantined) { visibleQuarantined.count { it.isSelected } }
    val allVisibleQuarantinedSelected = visibleQuarantined.isNotEmpty() && visibleQuarantinedSelectedCount == visibleQuarantined.size
    val totalQuarantinedSelectedCount = remember(quarantined) { quarantined.count { it.isSelected } }

    val visibleDeletedSelectedCount = remember(visibleDeletedTest) { visibleDeletedTest.count { it.isSelected } }
    val allVisibleDeletedSelected = visibleDeletedTest.isNotEmpty() && visibleDeletedSelectedCount == visibleDeletedTest.size
    val totalDeletedSelectedCount = remember(deletedTest) { deletedTest.count { it.isSelected } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header + Mode Switcher + Contact Group Review Filter + Recently Deleted Recovery Toggle
        Card(
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedVaultTab == 0) {
                                "Quarantine & Restore Vault"
                            } else {
                                "Recently Deleted Folder"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedVaultTab == 0) {
                                "${visibleQuarantined.size} of ${quarantined.size} quarantined • ${deletedTest.size} recently deleted"
                            } else {
                                "${visibleDeletedTest.size} of ${deletedTest.size} recoverable • 30-day retention"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (selectedVaultTab == 0 && quarantined.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.emptyQuarantine() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = RedDestructive)
                        ) {
                            Text("Empty Vault", fontSize = 12.sp)
                        }
                    } else if (selectedVaultTab == 1 && deletedTest.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearDeletedTestSnapshots() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = RedDestructive),
                            modifier = Modifier.testTag("clear_deleted_test_snapshots_btn")
                        ) {
                            Text("Empty Trash", fontSize = 12.sp)
                        }
                    }
                }

                // Segmented 2-Tab Switcher
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = selectedVaultTab == 0,
                        onClick = { viewModel.setVaultSubTab(0) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("vault_tab_quarantine")
                    ) {
                        Text("Quarantine (${quarantined.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    SegmentedButton(
                        selected = selectedVaultTab == 1,
                        onClick = { viewModel.setVaultSubTab(1) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("vault_tab_deleted_test")
                    ) {
                        Text("Recently Deleted (${deletedTest.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }

                // Vault Contact Group Review Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activeVaultFilter || showContactFilterPanel,
                        onClick = { showContactFilterPanel = !showContactFilterPanel },
                        label = {
                            Text(
                                text = if (uiState.vaultSelectedContacts.isNotEmpty()) {
                                    "Contacts (${uiState.vaultSelectedContacts.size})"
                                } else {
                                    "Filter by Contact"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = if (showContactFilterPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("vault_contact_filter_toggle")
                    )

                    FilterChip(
                        selected = uiState.vaultGroupByContact,
                        onClick = { viewModel.toggleVaultGroupByContact(!uiState.vaultGroupByContact) },
                        label = {
                            Text(
                                text = "Group by Contact",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = if (uiState.vaultGroupByContact) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else {
                            { Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        },
                        modifier = Modifier.testTag("vault_group_by_contact_chip")
                    )
                }

                if (showContactFilterPanel) {
                    val selectedVaultContactIds = remember(uiState.vaultSelectedContacts) {
                        uiState.vaultSelectedContacts.map { it.id }.toSet()
                    }
                    ContactFilterChecklist(
                        title = "Filter Vault by Contact",
                        subtitle = "Check contacts to review, group, or batch-restore by contact thread",
                        entries = uiState.vaultContactEntries,
                        selectedContactIds = selectedVaultContactIds,
                        onToggleContact = { contact -> viewModel.toggleVaultSelectedContact(contact) },
                        onSelectAll = { viewModel.selectAllVaultContacts() },
                        onClearAll = { viewModel.clearVaultSelectedContacts() },
                        hasContactsPermission = uiState.hasContactsPermission,
                        onRequestContactsPermission = onRequestContactsPermission,
                        filterEnabled = uiState.vaultFilterByContactEnabled,
                        onToggleFilterEnabled = { enabled -> viewModel.toggleVaultContactFilter(enabled) },
                        scopeSummaryText = if (activeVaultFilter) {
                            "Reviewing ${uiState.vaultSelectedContacts.size} selected contact group(s) via Room Query"
                        } else {
                            null
                        },
                        emptyMessage = "No contacts in Quarantine or Recently Deleted yet",
                        maxListHeightDp = 120,
                        testTagPrefix = "vault_contact_filter",
                        switchTestTag = "vault_contact_filter_switch",
                        searchQuery = uiState.vaultContactSearchQuery,
                        onSearchQueryChange = { viewModel.setVaultContactSearchQuery(it) }
                    )
                }

                // Recently Deleted Recovery Toggle (Compact)
                Surface(
                    color = if (uiState.testingRestoreDeletedEnabled) {
                        AmberAccent.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.testingRestoreDeletedEnabled) AmberAccent.copy(alpha = 0.4f) else CardBorder.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = null,
                                tint = if (uiState.testingRestoreDeletedEnabled) AmberAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "Recently Deleted Folder Recovery",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (uiState.testingRestoreDeletedEnabled) {
                                        "ON • 30-day retention & batch inbox restore"
                                    } else {
                                        "OFF • Deletions bypass Recently Deleted"
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Switch(
                            checked = uiState.testingRestoreDeletedEnabled,
                            onCheckedChange = { viewModel.toggleTestingRestoreDeleted(it) },
                            modifier = Modifier.testTag("vault_testing_restore_switch")
                        )
                    }
                }

                if (selectedVaultTab == 0 && visibleQuarantined.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeVaultFilter) {
                                "$visibleQuarantinedSelectedCount of ${visibleQuarantined.size} shown selected"
                            } else {
                                "$totalQuarantinedSelectedCount selected"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = {
                                if (activeVaultFilter) {
                                    viewModel.selectVaultMessagesByIds(
                                        messageIds = visibleQuarantined.mapTo(HashSet()) { it.id },
                                        select = !allVisibleQuarantinedSelected,
                                        isDeletedTest = false
                                    )
                                } else {
                                    viewModel.selectAllQuarantined(!allVisibleQuarantinedSelected)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("vault_select_all")
                        ) {
                            Text(if (allVisibleQuarantinedSelected) "Deselect All" else "Select All", fontSize = 12.sp)
                        }
                    }
                } else if (selectedVaultTab == 1 && visibleDeletedTest.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeVaultFilter) {
                                "$visibleDeletedSelectedCount of ${visibleDeletedTest.size} shown selected"
                            } else {
                                "$totalDeletedSelectedCount selected to restore"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AmberAccent
                        )
                        TextButton(
                            onClick = {
                                if (activeVaultFilter) {
                                    viewModel.selectVaultMessagesByIds(
                                        messageIds = visibleDeletedTest.mapTo(HashSet()) { it.id },
                                        select = !allVisibleDeletedSelected,
                                        isDeletedTest = true
                                    )
                                } else {
                                    viewModel.selectAllDeletedTest(!allVisibleDeletedSelected)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("vault_test_select_all")
                        ) {
                            Text(if (allVisibleDeletedSelected) "Deselect All" else "Select All", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (selectedVaultTab == 0) {
            // Quarantine Tab Content
            if (visibleQuarantined.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = if (activeVaultFilter) Icons.Default.FilterListOff else Icons.Default.Shield,
                            contentDescription = null,
                            tint = TealAccent,
                            modifier = Modifier.size(60.dp)
                        )
                        Text(
                            text = if (activeVaultFilter) {
                                "No Quarantined Messages for Selected Contacts"
                            } else {
                                "Quarantine Vault is Empty"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (activeVaultFilter) {
                                "Try checking another contact in Filter by Contact or tap Clear to show all quarantined items."
                            } else {
                                "Cleaned messages can be moved here first so you can safely preview and restore them back to your inbox whenever needed."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (activeVaultFilter) {
                            OutlinedButton(onClick = { viewModel.clearVaultSelectedContacts() }) {
                                Text("Show All Contacts")
                            }
                        }
                    }
                }
            } else {
                VaultMessageList(
                    messages = visibleQuarantined,
                    groupByContact = uiState.vaultGroupByContact,
                    isDeletedTest = false,
                    onToggleMessage = { viewModel.toggleQuarantineSelection(it) },
                    onToggleGroupSelect = { ids, select ->
                        viewModel.selectVaultMessagesByIds(ids, select = select, isDeletedTest = false)
                    },
                    onClickDetail = { viewModel.showMessageDetails(it) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            if (quarantined.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.restoreSelectedFromQuarantine() },
                            enabled = totalQuarantinedSelectedCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("restore_selected_button")
                        ) {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Restore ($totalQuarantinedSelectedCount)",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.promptPermanentDelete(fromQuarantine = true) },
                            enabled = totalQuarantinedSelectedCount > 0,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedDestructive),
                            border = ButtonDefaults.outlinedButtonBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(RedDestructive.copy(alpha = 0.6f))
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("permanent_delete_vault_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = RedDestructive,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Delete ($totalQuarantinedSelectedCount)",
                                color = RedDestructive,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Recently Deleted Folder Content (Batch Selection & Restoration)
            if (visibleDeletedTest.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = if (activeVaultFilter) Icons.Default.FilterListOff else Icons.Default.RestoreFromTrash,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(60.dp)
                        )
                        Text(
                            text = if (activeVaultFilter) {
                                "No Recently Deleted SMS for Selected Contacts"
                            } else {
                                "Recently Deleted Folder is Empty"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                activeVaultFilter -> "Clear the contact filter to view recently deleted messages from other senders."
                                uiState.testingRestoreDeletedEnabled ->
                                    "Deleted SMS messages are held here for 30 days. Select one or more messages to batch restore them back to your Primary Inbox."
                                else ->
                                    "Turn on 'Recently Deleted Folder Recovery' above so deleted SMS are kept here for batch restoration."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (activeVaultFilter) {
                            OutlinedButton(onClick = { viewModel.clearVaultSelectedContacts() }) {
                                Text("Show All Contacts")
                            }
                        }
                    }
                }
            } else {
                VaultMessageList(
                    messages = visibleDeletedTest,
                    groupByContact = uiState.vaultGroupByContact,
                    isDeletedTest = true,
                    onToggleMessage = { viewModel.toggleDeletedTestSelection(it) },
                    onToggleGroupSelect = { ids, select ->
                        viewModel.selectVaultMessagesByIds(ids, select = select, isDeletedTest = true)
                    },
                    onClickDetail = { viewModel.showMessageDetails(it) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            if (deletedTest.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isBatchRestoring) {
                            val total = max(1, uiState.batchRestoreProgressTotal)
                            val progress = (uiState.batchRestoreProgressCurrent.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            Text(
                                text = "Restoring ${uiState.batchRestoreProgressCurrent} of ${uiState.batchRestoreProgressTotal} messages to Primary Inbox...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AmberAccent
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                color = AmberAccent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("batch_restore_progress_bar")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.batchRestoreRecentlyDeleted() },
                                enabled = totalDeletedSelectedCount > 0 && !uiState.isBatchRestoring,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.35f)
                                    .height(48.dp)
                                    .testTag("restore_deleted_test_button")
                            ) {
                                Icon(
                                    Icons.Default.RestoreFromTrash,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Restore ($totalDeletedSelectedCount) to Inbox",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.permanentlyDeleteSelectedRecentlyDeleted() },
                                enabled = totalDeletedSelectedCount > 0 && !uiState.isBatchRestoring,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedDestructive),
                                border = ButtonDefaults.outlinedButtonBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(RedDestructive.copy(alpha = 0.6f))
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("purge_selected_deleted_button")
                            ) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = RedDestructive,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Purge ($totalDeletedSelectedCount)",
                                    color = RedDestructive,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultMessageList(
    messages: List<SmsMessageItem>,
    groupByContact: Boolean,
    isDeletedTest: Boolean,
    onToggleMessage: (String) -> Unit,
    onToggleGroupSelect: (Set<String>, Boolean) -> Unit,
    onClickDetail: (SmsMessageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = remember(messages, groupByContact) {
        if (!groupByContact) {
            emptyMap()
        } else {
            messages.groupBy { ContactThreadHelper.vaultGroupKey(it) }
        }
    }

    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(if (isDeletedTest) "vault_deleted_list" else "vault_quarantine_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!groupByContact) {
            items(messages, key = { it.id }) { msg ->
                MessageCard(
                    message = msg,
                    isSelected = msg.isSelected,
                    onToggleSelect = { onToggleMessage(msg.id) },
                    onClickDetail = { onClickDetail(msg) }
                )
            }
        } else {
            grouped.forEach { (groupKey, groupMessages) ->
                val senderTitle = groupMessages.firstOrNull()?.displaySender ?: "Unknown"
                val selectedInGroup = groupMessages.count { it.isSelected }
                val allInGroupSelected = selectedInGroup == groupMessages.size
                val groupIds = groupMessages.mapTo(HashSet()) { it.id }

                item(key = "header_${if (isDeletedTest) "del" else "quar"}_$groupKey") {
                    Surface(
                        color = CyanAccent.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_group_header_$groupKey")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = CyanAccent.copy(alpha = 0.22f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = senderTitle.firstOrNull()?.uppercase() ?: "?",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = CyanAccent
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = senderTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${groupMessages.size} message(s) • $selectedInGroup selected",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            TextButton(
                                onClick = { onToggleGroupSelect(groupIds, !allInGroupSelected) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("vault_group_select_$groupKey")
                            ) {
                                Text(
                                    text = if (allInGroupSelected) "Unselect" else "Select Group",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent
                                )
                            }
                        }
                    }
                }

                items(groupMessages, key = { it.id }) { msg ->
                    MessageCard(
                        message = msg,
                        isSelected = msg.isSelected,
                        onToggleSelect = { onToggleMessage(msg.id) },
                        onClickDetail = { onClickDetail(msg) }
                    )
                }
            }
        }
    }
}
