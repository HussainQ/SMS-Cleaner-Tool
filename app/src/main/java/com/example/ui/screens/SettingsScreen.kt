package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
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
import com.example.BuildConfig
import com.example.cloud.DeviceOAuthManager
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.theme.*
import com.example.ui.util.DateFormatterUtil
import com.example.update.UpdateCheckStatus

@Composable
fun SettingsScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onOpenSetupSheet: () -> Unit = { viewModel.openSetupStatusSheet() },
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppearanceSettingsCard(uiState = uiState, viewModel = viewModel)
                    SystemAndSafetyCard(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenSetupSheet = onOpenSetupSheet
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppUpdatesSettingsCard(uiState = uiState, viewModel = viewModel)
                    CloudBackupSettingsCard(uiState = uiState, viewModel = viewModel)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AppearanceSettingsCard(uiState = uiState, viewModel = viewModel)
                SystemAndSafetyCard(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenSetupSheet = onOpenSetupSheet
                )
                CloudBackupSettingsCard(uiState = uiState, viewModel = viewModel)
                AppUpdatesSettingsCard(uiState = uiState, viewModel = viewModel)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AppearanceSettingsCard(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel
) {
    val effectiveDarkMode = uiState.resolveIsDarkTheme(isSystemInDarkTheme())

    SettingsSectionCard(title = "Appearance & Display") {
        SettingsSwitchRow(
            title = "Material 3 Dark Mode",
            subtitle = when (uiState.darkModeOverride) {
                true -> "Dark theme enabled across entire app (saved)"
                false -> "Light theme enabled across entire app (saved)"
                null -> if (effectiveDarkMode) {
                    "Dark theme active (following system setting)"
                } else {
                    "Light theme active (following system setting)"
                }
            },
            checked = effectiveDarkMode,
            onCheckedChange = { viewModel.setDarkModeEnabled(it) },
            testTag = "settings_dark_mode_switch"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

        SettingsSwitchRow(
            title = "Use Device Material UI",
            subtitle = if (uiState.useDeviceMaterialTheme) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Follows Material You wallpaper colors"
                } else {
                    "Follows system Material theme"
                }
            } else {
                "Classic CleanSMS Navy & Cyan palette"
            },
            checked = uiState.useDeviceMaterialTheme,
            onCheckedChange = { viewModel.setUseDeviceMaterialTheme(it) },
            testTag = "settings_device_material_theme_switch"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

        SettingsSwitchRow(
            title = "Compact Messages UI",
            subtitle = if (uiState.compactMessagesUi) {
                "Compact density (more threads & slimmer bubbles)"
            } else {
                "Comfortable density (larger avatars & 2-line previews)"
            },
            checked = uiState.compactMessagesUi,
            onCheckedChange = { viewModel.setCompactMessagesUi(it) },
            testTag = "settings_compact_messages_switch"
        )
    }
}

@Composable
private fun SystemAndSafetyCard(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onOpenSetupSheet: () -> Unit
) {
    SettingsSectionCard(title = "Inbox & Cleanup Safety") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSetupSheet)
                .padding(vertical = 4.dp)
                .testTag("settings_open_setup_sheet_btn"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inbox Source & Permissions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val statusSummary = when {
                    uiState.isDemoMode -> "Demo Test Inbox • Tap to switch to Device SMS"
                    uiState.isDefaultSmsApp -> "Live SMS (${uiState.allMessages.size} msgs) • Default SMS Active"
                    else -> "Live SMS (${uiState.allMessages.size} msgs) • Manage permissions & role"
                }
                Text(
                    text = statusSummary,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Manage inbox setup",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

        SettingsSwitchRow(
            title = "Auto-backup before clean",
            subtitle = "Creates local snapshot prior to permanent removals",
            checked = uiState.autoBackupBeforeClean,
            onCheckedChange = { viewModel.toggleAutoBackup(it) },
            testTag = "auto_backup_switch"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

        SettingsSwitchRow(
            title = "Recently Deleted Folder Recovery",
            subtitle = "Keep deleted SMS for 30 days in Vault (${uiState.deletedTestMessages.size} saved)",
            checked = uiState.testingRestoreDeletedEnabled,
            onCheckedChange = { viewModel.toggleTestingRestoreDeleted(it) },
            testTag = "testing_restore_deleted_switch"
        )
    }
}

@Composable
private fun CloudBackupSettingsCard(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel
) {
    var accountsExpanded by remember { mutableStateOf(false) }
    var snapshotsExpanded by remember { mutableStateOf(false) }

    val authorizedCount = uiState.authorizedOAuthCredentials.size
    val snapshotCount = uiState.backupHistory.size

    SettingsSectionCard(title = "Cloud Backup") {
        Button(
            onClick = { viewModel.performManualCloudBackup() },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("manual_backup_button")
        ) {
            Icon(AppIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back Up to ${uiState.selectedCloudProvider}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

        // Collapsible Cloud Accounts & OAuth Vault
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { accountsExpanded = !accountsExpanded }
                .padding(vertical = 4.dp)
                .testTag("settings_cloud_accounts_toggle"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Cloud Accounts & OAuth Vault", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    text = if (authorizedCount > 0) {
                        "$authorizedCount authorized (${uiState.selectedCloudProvider} selected)"
                    } else {
                        "Connect Google Drive or Dropbox securely on-device"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (accountsExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                contentDescription = if (accountsExpanded) "Collapse accounts" else "Expand accounts",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = accountsExpanded) {
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(AppIcons.Security, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
                        Text(
                            text = "On-Device OAuth Vault: Secrets pulled only if authorized and stored locally.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                CloudProviderRow(
                    serviceId = DeviceOAuthManager.SERVICE_GOOGLE_DRIVE,
                    title = "Google Drive",
                    icon = AppIcons.CloudQueue,
                    accentColor = MaterialTheme.colorScheme.primary,
                    uiState = uiState,
                    viewModel = viewModel,
                    authBtnTag = "auth_google_drive_btn",
                    revokeBtnTag = "revoke_google_drive_btn",
                    radioBtnTag = "radio_google_drive"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

                CloudProviderRow(
                    serviceId = DeviceOAuthManager.SERVICE_DROPBOX,
                    title = "Dropbox",
                    icon = AppIcons.Cloud,
                    accentColor = PurpleAccent,
                    uiState = uiState,
                    viewModel = viewModel,
                    authBtnTag = "auth_dropbox_btn",
                    revokeBtnTag = "revoke_dropbox_btn",
                    radioBtnTag = "radio_dropbox"
                )

                if (authorizedCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(AppIcons.VpnKey, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                            Text(
                                text = "$authorizedCount local token(s)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { viewModel.purgeAllDeviceSecrets() },
                            modifier = Modifier.testTag("purge_all_secrets_btn")
                        ) {
                            Text("Purge All", fontSize = 11.sp, color = RedDestructive, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

        // Collapsible Saved Cloud Snapshots Accordion
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { snapshotsExpanded = !snapshotsExpanded }
                .padding(vertical = 4.dp)
                .testTag("settings_cloud_snapshots_toggle"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Saved Cloud Snapshots ($snapshotCount)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    text = if (snapshotCount == 0) "No snapshots saved yet" else "Tap to inspect and restore backups",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (snapshotsExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                contentDescription = if (snapshotsExpanded) "Collapse snapshots" else "Expand snapshots",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = snapshotsExpanded) {
            if (uiState.backupHistory.isEmpty()) {
                Text(
                    text = "No cloud backups saved yet. Create a snapshot above anytime.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.backupHistory.forEach { record ->
                        val dateFormatted = remember(record.backupTimestamp) {
                            DateFormatterUtil.formatBackupDate(record.backupTimestamp)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.fileName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${record.provider} • $dateFormatted • ${record.messageCount} msgs",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { viewModel.inspectBackupForRestore(record) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("restore_cloud_backup_btn_${record.id}")
                                ) {
                                    Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdatesSettingsCard(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel
) {
    val updateResult = uiState.updateCheckResult
    val updateAvailable = updateResult?.isUpdateAvailable == true
    val updateAccent = if (updateAvailable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    SettingsSectionCard(title = "App Updates") {
        Surface(
            color = updateAccent.copy(alpha = 0.1f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (updateAvailable) updateAccent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.openUpdatesDialog() }
                .testTag("cloud_pane_updates_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = AppIcons.SystemUpdate,
                        contentDescription = null,
                        tint = updateAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = if (updateAvailable) {
                                "Update Available: ${updateResult?.latestRelease?.tagName}"
                            } else {
                                "CleanSMS v${BuildConfig.VERSION_NAME}"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (updateAvailable) updateAccent else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                updateAvailable -> "${updateResult?.versionsBehindCount ?: 1} version(s) behind • Ready to install"
                                uiState.updateCheckStatus == UpdateCheckStatus.CHECKING -> "Checking ${uiState.gitHubRepoSlug}..."
                                else -> "GitHub Releases • ${uiState.gitHubRepoSlug}"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(
                    onClick = { viewModel.openUpdatesDialog() },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cloud_pane_check_updates_btn")
                ) {
                    Text(
                        text = if (updateAvailable) "Pull Update" else "Check",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = updateAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudProviderRow(
    serviceId: String,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    authBtnTag: String,
    revokeBtnTag: String,
    radioBtnTag: String
) {
    val cred = uiState.authorizedOAuthCredentials.find { it.serviceId == serviceId }
    val isAuth = cred != null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    text = if (isAuth) "Authorized • ${cred!!.accountName}" else "Not authorized",
                    fontSize = 11.sp,
                    color = if (isAuth) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isAuth) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { viewModel.revokeServiceAuthorization(serviceId) },
                    modifier = Modifier.testTag(revokeBtnTag)
                ) {
                    Text("Revoke", fontSize = 11.sp, color = RedDestructive)
                }
                RadioButton(
                    selected = uiState.selectedCloudProvider == title,
                    onClick = { viewModel.setCloudProvider(title) },
                    modifier = Modifier.testTag(radioBtnTag)
                )
            }
        } else {
            Button(
                onClick = {
                    val def = viewModel.deviceOAuthManager.supportedServices.find { it.id == serviceId }
                    if (def != null) viewModel.requestServiceAuthorization(def)
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag(authBtnTag)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Authorize", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
