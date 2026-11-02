package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    val quarantined = uiState.quarantinedMessages
    val selectedCount = quarantined.count { it.isSelected }
    val allSelected = quarantined.isNotEmpty() && quarantined.all { it.isSelected }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Card(
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Quarantine & Restore Vault",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${quarantined.size} messages safely preserved",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (quarantined.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.emptyQuarantine() },
                            colors = ButtonDefaults.textButtonColors(contentColor = RedDestructive)
                        ) {
                            Text("Empty Vault", fontSize = 12.sp)
                        }
                    }
                }

                if (quarantined.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$selectedCount selected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { viewModel.selectAllQuarantined(!allSelected) },
                            modifier = Modifier.testTag("vault_select_all")
                        ) {
                            Text(if (allSelected) "Deselect All" else "Select All")
                        }
                    }
                }
            }
        }

        // Vault list or empty state
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

        // Bottom Action Bar for Restore & Permanent Delete
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
                    // Restore to Inbox Button
                    Button(
                        onClick = { viewModel.restoreSelectedFromQuarantine() },
                        enabled = selectedCount > 0,
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
                            text = "Restore ($selectedCount)",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Permanent Delete button (Confirmation step!)
                    OutlinedButton(
                        onClick = { viewModel.promptPermanentDelete(fromQuarantine = true) },
                        enabled = selectedCount > 0,
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
                            text = "Delete ($selectedCount)",
                            color = RedDestructive,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
