package com.example.ui.screens

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
import com.example.model.BackupHistoryRecord
import com.example.model.CustomFilterRule
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RulesAndCloudScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 680.dp

        if (isWideScreen) {
            // Adaptive Two-Column Layout for Tablets & Foldables
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left Column: Custom Rules Pane
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    )
                ) {
                    CustomRulesPane(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.padding(20.dp)
                    )
                }

                // Right Column: Cloud Backup Pane
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    )
                ) {
                    CloudBackupPane(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            // Compact Handheld Phone Layout with uncrowded Segmented Controls
            var selectedTab by remember { mutableIntStateOf(0) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Generous Segmented Pill Switcher
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { selectedTab = 0 },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Rules (${uiState.customRules.size})",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            onClick = { selectedTab = 1 },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedTab == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cloud Backup",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (selectedTab == 0) {
                    CustomRulesPane(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CloudBackupPane(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomRulesPane(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pane Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Custom Rules",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Filter messages by sender, keyword, or age",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { viewModel.openAddRuleSheet() },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("add_rule_fab_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Navy900, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Rule", color = Navy900, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (uiState.customRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = TealAccent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Text("No Custom Rules Added", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "Add custom rules to automatically target messages from specific senders or containing certain keywords.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.customRules, key = { it.id }) { rule ->
                    CustomRuleItemCard(
                        rule = rule,
                        onToggle = { viewModel.toggleRuleEnabled(rule) },
                        onDelete = { viewModel.deleteCustomRule(rule.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomRuleItemCard(
    rule: CustomFilterRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (rule.isEnabled) TealAccent.copy(alpha = 0.4f) else CardBorder.copy(alpha = 0.4f)
            )
        ),
        modifier = Modifier.fillMaxWidth().testTag("custom_rule_card_${rule.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (rule.isEnabled) TealAccent.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (rule.isEnabled) "ACTIVE" else "OFF",
                            color = if (rule.isEnabled) TealAccent else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = rule.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = RedDestructive.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Criteria badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (rule.senderFilter.isNotBlank()) {
                    Surface(
                        color = Slate600.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Sender: ${rule.senderFilter}",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (rule.keywords.isNotBlank()) {
                    Surface(
                        color = Slate600.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Keywords: ${rule.keywords.take(18)}...",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (rule.olderThanDays != null) {
                    Surface(
                        color = Slate600.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "> ${rule.olderThanDays}d",
                            fontSize = 11.sp,
                            color = AmberAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudBackupPane(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pane Header
        Column {
            Text(
                text = "Cloud Backup & Restore",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Securely backup cleaned SMS snapshots to Google Drive or Dropbox",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Secrets & Security Status Banner with Local Vault Policy
        Surface(
            color = CardDark,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = AmberAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "On-Device OAuth Vault: Secrets pulled if and only if authorized. Stored strictly on this device.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Cloud Storage Providers with OAuth Authorization Controls
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val googleDriveCred = uiState.authorizedOAuthCredentials.find { it.serviceId == com.example.cloud.DeviceOAuthManager.SERVICE_GOOGLE_DRIVE }
                val isGoogleDriveAuth = googleDriveCred != null

                val dropboxCred = uiState.authorizedOAuthCredentials.find { it.serviceId == com.example.cloud.DeviceOAuthManager.SERVICE_DROPBOX }
                val isDropboxAuth = dropboxCred != null

                // Google Drive Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = CyanAccent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text("Google Drive", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                if (isGoogleDriveAuth) {
                                    Text(
                                        text = "Authorized • ${googleDriveCred!!.accountName}",
                                        fontSize = 11.sp,
                                        color = TealAccent
                                    )
                                    Text(
                                        text = "Local Key: ${googleDriveCred.maskedToken} (Private Storage)",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                } else {
                                    Text(
                                        text = "Not Authorized • Device secret not pulled",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }

                        if (isGoogleDriveAuth) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { viewModel.revokeServiceAuthorization(com.example.cloud.DeviceOAuthManager.SERVICE_GOOGLE_DRIVE) },
                                    modifier = Modifier.testTag("revoke_google_drive_btn")
                                ) {
                                    Text("Revoke", fontSize = 11.sp, color = Color(0xFFEF5350))
                                }
                                RadioButton(
                                    selected = uiState.selectedCloudProvider == "Google Drive",
                                    onClick = { viewModel.setCloudProvider("Google Drive") },
                                    modifier = Modifier.testTag("radio_google_drive")
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    val def = viewModel.deviceOAuthManager.supportedServices.find { it.id == com.example.cloud.DeviceOAuthManager.SERVICE_GOOGLE_DRIVE }
                                    if (def != null) viewModel.requestServiceAuthorization(def)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("auth_google_drive_btn")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = Navy900)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Authorize", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Navy900)
                            }
                        }
                    }
                }

                HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

                // Dropbox Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = PurpleAccent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Cloud, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text("Dropbox", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                if (isDropboxAuth) {
                                    Text(
                                        text = "Authorized • ${dropboxCred!!.accountName}",
                                        fontSize = 11.sp,
                                        color = TealAccent
                                    )
                                    Text(
                                        text = "Local Key: ${dropboxCred.maskedToken} (Private Storage)",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                } else {
                                    Text(
                                        text = "Not Authorized • Device secret not pulled",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }

                        if (isDropboxAuth) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { viewModel.revokeServiceAuthorization(com.example.cloud.DeviceOAuthManager.SERVICE_DROPBOX) },
                                    modifier = Modifier.testTag("revoke_dropbox_btn")
                                ) {
                                    Text("Revoke", fontSize = 11.sp, color = Color(0xFFEF5350))
                                }
                                RadioButton(
                                    selected = uiState.selectedCloudProvider == "Dropbox",
                                    onClick = { viewModel.setCloudProvider("Dropbox") },
                                    modifier = Modifier.testTag("radio_dropbox")
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    val def = viewModel.deviceOAuthManager.supportedServices.find { it.id == com.example.cloud.DeviceOAuthManager.SERVICE_DROPBOX }
                                    if (def != null) viewModel.requestServiceAuthorization(def)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("auth_dropbox_btn")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Authorize", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // On-Device Vault Summary & Purge Card
        if (uiState.authorizedOAuthCredentials.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder.copy(alpha = 0.4f)),
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = TealAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${uiState.authorizedOAuthCredentials.size} OAuth secret(s) stored locally",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(
                        onClick = { viewModel.purgeAllDeviceSecrets() },
                        modifier = Modifier.testTag("purge_all_secrets_btn")
                    ) {
                        Text(
                            text = "Purge All",
                            fontSize = 11.sp,
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Settings & Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-backup before clean", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Creates snapshot prior to permanent removals", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = uiState.autoBackupBeforeClean,
                    onCheckedChange = { viewModel.toggleAutoBackup(it) },
                    modifier = Modifier.testTag("auto_backup_switch")
                )
            }
        }

        Button(
            onClick = { viewModel.performManualCloudBackup() },
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("manual_backup_button")
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Navy900, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back Up to ${uiState.selectedCloudProvider}", color = Navy900, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // Snapshot History List
        Text(
            text = "Saved Cloud Snapshots (${uiState.backupHistory.size})",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (uiState.backupHistory.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No cloud backups saved yet. Create your first snapshot above.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.backupHistory, key = { it.id }) { record ->
                    val dateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(record.backupTimestamp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${record.provider} • $dateFormatted • ${record.messageCount} msgs",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Button(
                                onClick = { viewModel.inspectBackupForRestore(record) },
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("restore_cloud_backup_btn_${record.id}")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
