package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.ui.CleanSmsUiState
import com.example.ui.theme.*
import com.example.update.UpdateCheckStatus

@Composable
fun AppUpdatesDialog(
    uiState: CleanSmsUiState,
    onCheckNow: () -> Unit,
    onTogglePrereleases: (Boolean) -> Unit,
    onSaveRepoSlug: (String) -> Unit,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editingRepo by remember { mutableStateOf(false) }
    var repoInput by remember(uiState.gitHubRepoSlug) { mutableStateOf(uiState.gitHubRepoSlug) }

    val result = uiState.updateCheckResult
    val latest = result?.latestRelease
    val isUpdateAvailable = result?.isUpdateAvailable == true

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 580.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
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
                            color = (if (isUpdateAvailable) TealAccent else CyanAccent).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isUpdateAvailable) Icons.Default.SystemUpdate else Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = if (isUpdateAvailable) TealAccent else CyanAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "GitHub Updates & Upgrade Path",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Installed: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_updates_dialog_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Updates Dialog")
                    }
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Repository Source + Pre-release Channel Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "GitHub Repository",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = uiState.gitHubRepoSlug,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CyanAccent
                                    )
                                }
                                TextButton(
                                    onClick = { editingRepo = !editingRepo },
                                    modifier = Modifier.testTag("edit_github_repo_btn")
                                ) {
                                    Text(if (editingRepo) "Cancel" else "Change", fontSize = 12.sp)
                                }
                            }

                            AnimatedVisibility(visible = editingRepo) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = repoInput,
                                        onValueChange = { repoInput = it },
                                        label = { Text("owner/repo or GitHub URL") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("github_repo_input")
                                    )
                                    Button(
                                        onClick = {
                                            editingRepo = false
                                            onSaveRepoSlug(repoInput)
                                        },
                                        modifier = Modifier.testTag("save_github_repo_btn")
                                    ) {
                                        Text("Save")
                                    }
                                }
                            }

                            HorizontalDivider(color = CardBorder.copy(alpha = 0.4f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Include Pre-Releases",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Tracks QA & local test releases alongside stable builds",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = uiState.includePrereleases,
                                    onCheckedChange = onTogglePrereleases,
                                    modifier = Modifier.testTag("include_prereleases_switch")
                                )
                            }
                        }
                    }

                    // Check Status & Release Card
                    when (uiState.updateCheckStatus) {
                        UpdateCheckStatus.CHECKING -> {
                            Surface(
                                color = CyanAccent.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                        color = CyanAccent
                                    )
                                    Text(
                                        text = "Querying GitHub Releases for ${uiState.gitHubRepoSlug}...",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        UpdateCheckStatus.UPDATE_AVAILABLE -> {
                            val upgradeChain = result?.upgradeChain.orEmpty()
                            val versionsBehind = result?.versionsBehindCount ?: 1
                            Surface(
                                color = TealAccent.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_available_card")
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
                                        Text(
                                            text = "New Release: ${latest?.tagName ?: "Available"}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TealAccent
                                        )
                                        Surface(
                                            color = TealAccent.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (versionsBehind > 1) {
                                                    "$versionsBehind versions behind"
                                                } else {
                                                    "1 version behind"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TealAccent,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    // Multi-version Upgrade Path Guarantee
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.VerifiedUser,
                                                    contentDescription = null,
                                                    tint = TealAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Direct Multi-Version Upgrade Path Ready",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TealAccent
                                                )
                                            }
                                            Text(
                                                text = result?.upgradePathSummary.orEmpty(),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Room schema migrations (v1→v4) & app preferences migrate directly in one jump—no intermediate APK installs required. Note: if upgrading once from a build with a different package name or signing certificate, uninstall the previous version first before installing com.aistudio.cleansms.jrvxpq.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // APK Asset + SHA-256 Checksum
                                    latest?.apkAsset?.let { asset ->
                                        val sizeMb = String.format(java.util.Locale.US, "%.1f MB", asset.sizeBytes / (1024.0 * 1024.0))
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "APK Asset: ${asset.name} ($sizeMb)",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val sha = result?.verifiedSha256 ?: asset.sha256Checksum
                                                if (!sha.isNullOrBlank()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "SHA-256: ${sha.take(16)}…${sha.takeLast(8)}",
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        TextButton(
                                                            onClick = {
                                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                cm.setPrimaryClip(ClipData.newPlainText("CleanSMS APK SHA-256", sha))
                                                                onNotify("SHA-256 checksum copied to clipboard.")
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.testTag("copy_update_sha_btn")
                                                        ) {
                                                            Text("Copy SHA-256", fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Aggregated Changelogs across all skipped versions
                                    if (upgradeChain.isNotEmpty()) {
                                        Text(
                                            text = "Combined Changes (${upgradeChain.size} release${if (upgradeChain.size > 1) "s" else ""})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        upgradeChain.asReversed().forEach { rel ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "${rel.tagName} • ${if (rel.isPrerelease) "Pre-Release" else "Stable"}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CyanAccent
                                                    )
                                                    val previewBody = rel.releaseNotes
                                                        .lineSequence()
                                                        .filter { it.isNotBlank() }
                                                        .take(5)
                                                        .joinToString("\n")
                                                        .ifBlank { "Includes bug fixes and improvements." }
                                                    Text(
                                                        text = previewBody,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 5,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        UpdateCheckStatus.UP_TO_DATE -> {
                            Surface(
                                color = CyanAccent.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("up_to_date_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealAccent)
                                    Column {
                                        Text(
                                            text = "You're running the latest release",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = latest?.let { "Latest on GitHub: ${it.tagName}" }
                                                ?: "Version v${BuildConfig.VERSION_NAME} matches the latest release.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        UpdateCheckStatus.ERROR -> {
                            Surface(
                                color = Color(0xFFEF5350).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_error_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Update Check Failed",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFFEF5350)
                                    )
                                    Text(
                                        text = result?.errorMessage ?: "Unable to connect to GitHub Releases.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        UpdateCheckStatus.IDLE -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Tap 'Check Now' to query GitHub Releases and verify if a newer release is available.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    // Local Upgrade Migration Integrity Report
                    uiState.upgradeMigrationReport?.let { report ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Local Upgrade & Schema Integrity",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Room Schema v${report.databaseSchemaVersion} • Non-destructive v1→v4 migrations active",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (report.previousVersionName != null) {
                                    Text(
                                        text = "Last upgraded from v${report.previousVersionName} (${report.previousVersionCode}) → v${report.currentVersionName}",
                                        fontSize = 11.sp,
                                        color = TealAccent
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCheckNow,
                        enabled = uiState.updateCheckStatus != UpdateCheckStatus.CHECKING,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("check_updates_now_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    val targetDownloadUrl = latest?.apkAsset?.downloadUrl ?: latest?.htmlUrl
                        ?: "https://github.com/${uiState.gitHubRepoSlug}/releases"

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetDownloadUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                onNotify("Unable to open browser for $targetDownloadUrl")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isUpdateAvailable) TealAccent else CyanAccent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("pull_latest_release_btn")
                    ) {
                        Icon(
                            imageVector = if (isUpdateAvailable && latest?.apkAsset != null) {
                                Icons.Default.Download
                            } else {
                                Icons.AutoMirrored.Filled.Launch
                            },
                            contentDescription = null,
                            tint = Navy900,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isUpdateAvailable && latest?.apkAsset != null -> "Pull Latest APK"
                                isUpdateAvailable -> "Open Release"
                                else -> "GitHub Releases"
                            },
                            color = Navy900,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
