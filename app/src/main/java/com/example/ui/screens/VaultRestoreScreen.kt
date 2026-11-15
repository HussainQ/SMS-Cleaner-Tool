package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.components.MessageCard
import com.example.ui.theme.*

@Composable
fun VaultRestoreScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    modifier: Modifier = Modifier
) {
    var selectedVaultTab by remember { mutableIntStateOf(0) } // 0 = Quarantine, 1 = Deleted (Test)

    val quarantined = uiState.quarantinedMessages
    val quarantinedSelectedCount = remember(quarantined) { quarantined.count { it.isSelected } }
    val allQuarantinedSelected = quarantined.isNotEmpty() && quarantinedSelectedCount == quarantined.size

    val deletedTest = uiState.deletedTestMessages
    val deletedSelectedCount = remember(deletedTest) { deletedTest.count { it.isSelected } }
    val allDeletedSelected = deletedTest.isNotEmpty() && deletedSelectedCount == deletedTest.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header + Mode Switcher + Testing Toggle
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
                                "${quarantined.size} quarantined • ${deletedTest.size} test snapshots"
                            } else {
                                "${deletedTest.size} permanently deleted message(s) captured for testing"
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
                        onClick = { selectedVaultTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("vault_tab_quarantine")
                    ) {
                        Text("Quarantine (${quarantined.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    SegmentedButton(
                        selected = selectedVaultTab == 1,
                        onClick = { selectedVaultTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("vault_tab_deleted_test")
                    ) {
                        Text("Deleted Test (${deletedTest.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
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

                if (selectedVaultTab == 0 && quarantined.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$quarantinedSelectedCount selected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { viewModel.selectAllQuarantined(!allQuarantinedSelected) },
                            modifier = Modifier.testTag("vault_select_all")
                        ) {
                            Text(if (allQuarantinedSelected) "Deselect All" else "Select All")
                        }
                    }
                } else if (selectedVaultTab == 1 && deletedTest.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$deletedSelectedCount selected to restore",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AmberAccent
                        )
                        TextButton(
                            onClick = { viewModel.selectAllDeletedTest(!allDeletedSelected) },
                            modifier = Modifier.testTag("vault_test_select_all")
                        ) {
                            Text(if (allDeletedSelected) "Deselect All" else "Select All")
                        }
                    }
                }
            }
        }

        if (selectedVaultTab == 0) {
            // Quarantine Tab Content
            if (quarantined.isEmpty()) {
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
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = TealAccent,
                            modifier = Modifier.size(60.dp)
                        )
                        Text(
                            text = "Quarantine Vault is Empty",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Cleaned messages can be moved here first so you can safely preview and restore them back to your inbox whenever needed.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(quarantined, key = { it.id }) { msg ->
                        MessageCard(
                            message = msg,
                            isSelected = msg.isSelected,
                            onToggleSelect = { viewModel.toggleQuarantineSelection(msg.id) },
                            onClickDetail = { viewModel.showMessageDetails(msg) }
                        )
                    }
                }
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
                            enabled = quarantinedSelectedCount > 0,
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
                                text = "Restore ($quarantinedSelectedCount)",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.promptPermanentDelete(fromQuarantine = true) },
                            enabled = quarantinedSelectedCount > 0,
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
                                text = "Delete ($quarantinedSelectedCount)",
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
            if (deletedTest.isEmpty()) {
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
                            imageVector = Icons.Default.RestoreFromTrash,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(60.dp)
                        )
                        Text(
                            text = "No Deleted Test Snapshots",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.testingRestoreDeletedEnabled) {
                                "When you permanently delete messages while testing is ON, snapshots appear here so you can restore them back to your inbox and re-verify rules."
                            } else {
                                "Turn on 'Testing Feature: Recover Deleted Messages' above to capture snapshots of deleted messages for test restoration."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(deletedTest, key = { it.id }) { msg ->
                        MessageCard(
                            message = msg,
                            isSelected = msg.isSelected,
                            onToggleSelect = { viewModel.toggleDeletedTestSelection(msg.id) },
                            onClickDetail = { viewModel.showMessageDetails(msg) }
                        )
                    }
                }
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
                            enabled = deletedSelectedCount > 0,
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
                                text = "Restore Deleted ($deletedSelectedCount) for Testing",
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
