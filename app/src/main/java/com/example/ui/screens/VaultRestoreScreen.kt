package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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

@Composable
fun VaultRestoreScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestContactsPermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedVaultTab = uiState.vaultSubTab // 0 = Quarantine, 1 = Deleted (Test)
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

    val activeVaultFilter = uiState.vaultFilterByContactEnabled && uiState.vaultSelectedContacts.isNotEmpty()
    val visibleQuarantined = remember(quarantined, activeVaultFilter, uiState.vaultSelectedContacts) {
        if (activeVaultFilter) {
            ContactThreadHelper.filterMessagesByContacts(quarantined, uiState.vaultSelectedContacts)
        } else {
            quarantined
        }
    }
    val visibleDeletedTest = remember(deletedTest, activeVaultFilter, uiState.vaultSelectedContacts) {
        if (activeVaultFilter) {
            ContactThreadHelper.filterMessagesByContacts(deletedTest, uiState.vaultSelectedContacts)
        } else {
            deletedTest
        }
    }

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
        // Top Header + Mode Switcher + Contact Group Review Filter + Testing Toggle
        Card(
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quarantine & Restore Vault",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedVaultTab == 0) {
                                "${visibleQuarantined.size} of ${quarantined.size} quarantined • ${deletedTest.size} test snapshots"
                            } else {
                                "${visibleDeletedTest.size} of ${deletedTest.size} deleted snapshot(s)"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (selectedVaultTab == 0 && quarantined.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.emptyQuarantine() },
                            colors = ButtonDefaults.textButtonColors(contentColor = RedDestructive)
                        ) {
                            Text("Empty Vault", fontSize = 12.sp)
                        }
                    } else if (selectedVaultTab == 1 && deletedTest.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearDeletedTestSnapshots() },
                            colors = ButtonDefaults.textButtonColors(contentColor = RedDestructive),
                            modifier = Modifier.testTag("clear_deleted_test_snapshots_btn")
                        ) {
                            Text("Clear Snapshots", fontSize = 12.sp)
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
                        Text("Quarantine (${quarantined.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    SegmentedButton(
                        selected = selectedVaultTab == 1,
                        onClick = { viewModel.setVaultSubTab(1) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("vault_tab_deleted_test")
                    ) {
                        Text("Deleted Test (${deletedTest.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

                AnimatedVisibility(visible = showContactFilterPanel) {
                    val selectedVaultContactIds = remember(uiState.vaultSelectedContacts) {
                        uiState.vaultSelectedContacts.map { it.id }.toSet()
                    }
                    ContactFilterChecklist(
                        title = "Filter Vault by Contact",
                        subtitle = "Check contacts to review, group, restore, or delete by contact thread",
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
                            "Reviewing ${uiState.vaultSelectedContacts.size} selected contact group(s) in Vault"
                        } else {
                            null
                        },
                        emptyMessage = "No contacts in Quarantine or Deleted snapshots yet",
                        maxListHeightDp = 120,
                        testTagPrefix = "vault_contact_filter",
                        switchTestTag = "vault_contact_filter_switch"
                    )
                }

                // Testing Deleted Message Recovery Toggle
                Surface(
                    color = if (uiState.testingRestoreDeletedEnabled) {
                        AmberAccent.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.testingRestoreDeletedEnabled) AmberAccent.copy(alpha = 0.4f) else CardBorder.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = if (uiState.testingRestoreDeletedEnabled) AmberAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Testing Feature: Recover Deleted Messages",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (uiState.testingRestoreDeletedEnabled) {
                                        "ON — Snapshots saved on permanent delete so you can restore & re-test"
                                    } else {
                                        "OFF — Permanent deletions are not recorded for test recovery"
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            fontSize = 13.sp,
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
                            modifier = Modifier.testTag("vault_select_all")
                        ) {
                            Text(if (allVisibleQuarantinedSelected) "Deselect All" else "Select All")
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
                            fontSize = 13.sp,
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
                            modifier = Modifier.testTag("vault_test_select_all")
                        ) {
                            Text(if (allVisibleDeletedSelected) "Deselect All" else "Select All")
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
            // Deleted (Test Recovery) Tab Content
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
                                "No Deleted Snapshots for Selected Contacts"
                            } else {
                                "No Deleted Test Snapshots"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                activeVaultFilter -> "Clear the contact filter to view snapshots from other senders."
                                uiState.testingRestoreDeletedEnabled ->
                                    "When you permanently delete messages while testing is ON, snapshots appear here so you can restore them back to your inbox and re-verify rules."
                                else ->
                                    "Turn on 'Testing Feature: Recover Deleted Messages' above to capture snapshots of deleted messages for test restoration."
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.restoreSelectedDeletedTestMessages() },
                            enabled = totalDeletedSelectedCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("restore_deleted_test_button")
                        ) {
                            Icon(
                                Icons.Default.RestoreFromTrash,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Restore Deleted ($totalDeletedSelectedCount) for Testing",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
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
