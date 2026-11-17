package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ContactFilterMode
import com.example.model.ContactInfo
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.theme.*

@Composable
fun ScannerScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestSmsPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    onHandBackDefaultSmsRole: () -> Unit,
    onNavigateToPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val duplicateCount = uiState.summary.duplicateCount
    val otpCount = uiState.summary.otpCount
    val promoCount = uiState.summary.promoCount
    val spamCount = uiState.summary.spamCount
    val customCount = uiState.summary.customRuleCount
    val totalCleanable = uiState.summary.allCleanableCount

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isTablet = maxWidth >= 720.dp

        if (isTablet) {
            // Adaptive 2-column layout for tablets and foldables
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left Column: Clutter Analysis & Data Source
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DataSourceCard(
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequestSmsPermission = onRequestSmsPermission
                    )

                    ClutterAnalysisCard(
                        totalCleanable = totalCleanable,
                        allMessagesCount = uiState.allMessages.size,
                        duplicateCount = duplicateCount,
                        otpCount = otpCount,
                        promoCount = promoCount,
                        spamCount = spamCount,
                        isScanning = uiState.isScanning,
                        onRefresh = { viewModel.refreshMessages() },
                        onNavigateToPreview = onNavigateToPreview
                    )
                }

                // Right Column: Default SMS Status, Permissions & Detection Rules
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TelephonyBanner(
                        isDefaultSmsApp = uiState.isDefaultSmsApp,
                        previousAppName = uiState.previousDefaultSmsAppName,
                        onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                        onHandBackDefaultSmsRole = onHandBackDefaultSmsRole
                    )

                    SmsPermissionsBanner(
                        hasReadPermission = uiState.hasReadSmsPermission,
                        hasWritePermission = uiState.hasWriteSmsPermission,
                        hasContactsPermission = uiState.hasContactsPermission,
                        onRequestSmsPermission = onRequestSmsPermission,
                        onRequestContactsPermission = onRequestContactsPermission
                    )

                    DetectionRulesCard(
                        uiState = uiState,
                        viewModel = viewModel,
                        customCount = customCount,
                        onRequestContactsPermission = onRequestContactsPermission
                    )
                }
            }
        } else {
            // Standard single-column phone layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DataSourceCard(
                    uiState = uiState,
                    viewModel = viewModel,
                    onRequestSmsPermission = onRequestSmsPermission
                )

                ClutterAnalysisCard(
                    totalCleanable = totalCleanable,
                    allMessagesCount = uiState.allMessages.size,
                    duplicateCount = duplicateCount,
                    otpCount = otpCount,
                    promoCount = promoCount,
                    spamCount = spamCount,
                    isScanning = uiState.isScanning,
                    onRefresh = { viewModel.refreshMessages() },
                    onNavigateToPreview = onNavigateToPreview
                )

                TelephonyBanner(
                    isDefaultSmsApp = uiState.isDefaultSmsApp,
                    previousAppName = uiState.previousDefaultSmsAppName,
                    onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                    onHandBackDefaultSmsRole = onHandBackDefaultSmsRole
                )

                SmsPermissionsBanner(
                    hasReadPermission = uiState.hasReadSmsPermission,
                    hasWritePermission = uiState.hasWriteSmsPermission,
                    hasContactsPermission = uiState.hasContactsPermission,
                    onRequestSmsPermission = onRequestSmsPermission,
                    onRequestContactsPermission = onRequestContactsPermission
                )

                DetectionRulesCard(
                    uiState = uiState,
                    viewModel = viewModel,
                    customCount = customCount,
                    onRequestContactsPermission = onRequestContactsPermission
                )
            }
        }
    }
}

@Composable
fun DataSourceCard(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestSmsPermission: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isDemoMode) CyanDark.copy(alpha = 0.2f) else TealAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isDemoMode) AppIcons.Science else AppIcons.Smartphone,
                        contentDescription = null,
                        tint = if (uiState.isDemoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = if (uiState.isDemoMode) "Demo Test Inbox" else "Live Device SMS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (uiState.isDemoMode) "30+ simulated messages & contacts" else "${uiState.allMessages.size} real messages",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (uiState.isDemoMode) "Demo" else "Device",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Switch(
                    checked = !uiState.isDemoMode,
                    onCheckedChange = { isDevice ->
                        if (isDevice && (!uiState.hasReadSmsPermission || !uiState.hasWriteSmsPermission)) {
                            onRequestSmsPermission()
                        } else {
                            viewModel.setDemoMode(!isDevice)
                        }
                    },
                    modifier = Modifier.testTag("demo_mode_switch")
                )
            }
        }
    }
}

@Composable
fun ClutterAnalysisCard(
    totalCleanable: Int,
    allMessagesCount: Int,
    duplicateCount: Int,
    otpCount: Int,
    promoCount: Int,
    spamCount: Int,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    onNavigateToPreview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isScanning) "Scanning Inbox..." else "Inbox Analysis",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "$totalCleanable Messages to Clean",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .testTag("refresh_scan_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Rescan",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isScanning) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .testTag("scanner_indeterminate_progress"),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                        )
                        Text(
                            text = "Reading messages & evaluating rules...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                }

                Text(
                    text = "Out of $allMessagesCount total messages in your inbox, we found redundant copies, expired verification codes, ads, and spam.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )

                // Stat Pills Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBadge(
                        count = duplicateCount,
                        label = "Duplicates",
                        color = AmberAccent,
                        icon = AppIcons.ContentCopy,
                        modifier = Modifier.weight(1f)
                    )
                    StatBadge(
                        count = otpCount,
                        label = "OTPs",
                        color = PurpleAccent,
                        icon = AppIcons.Password,
                        modifier = Modifier.weight(1f)
                    )
                    StatBadge(
                        count = promoCount,
                        label = "Promos",
                        color = RoseAccent,
                        icon = AppIcons.LocalOffer,
                        modifier = Modifier.weight(1f)
                    )
                    StatBadge(
                        count = spamCount,
                        label = "Spam",
                        color = RedDestructive,
                        icon = AppIcons.Report,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = onNavigateToPreview,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("preview_clean_button")
                ) {
                    Text(
                        text = "Review & Clean Messages ($totalCleanable)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TelephonyBanner(
    isDefaultSmsApp: Boolean,
    previousAppName: String,
    onRequestDefaultSmsRole: () -> Unit,
    onHandBackDefaultSmsRole: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = isDefaultSmsApp,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "telephony_role_banner",
        modifier = modifier
    ) { activeDefault ->
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (activeDefault) TealAccent.copy(alpha = 0.14f) else AmberAccent.copy(alpha = 0.12f)
            ),
            border = BorderStroke(
                1.dp,
                if (activeDefault) TealAccent.copy(alpha = 0.45f) else AmberAccent.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(if (activeDefault) "default_sms_active_banner" else "default_sms_needed_banner")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = if (activeDefault) TealAccent.copy(alpha = 0.22f) else AmberAccent.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (activeDefault) AppIcons.Verified else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (activeDefault) TealAccent else AmberAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeDefault) {
                                "Default SMS App Active"
                            } else {
                                "Default SMS Role Needed for Permanent Delete"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (activeDefault) {
                                "Taken from $previousAppName. Ready to delete directly from inbox, or hand the role back when done."
                            } else {
                                "Android requires temporarily holding the Default SMS role (from $previousAppName) to delete directly from device inbox."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                if (!activeDefault) {
                    Button(
                        onClick = onRequestDefaultSmsRole,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("set_default_sms_button")
                    ) {
                        Icon(AppIcons.AdminPanelSettings, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Set as Default SMS App",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("default_sms_app_status_indicator")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Default SMS App",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Default SMS App",
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onHandBackDefaultSmsRole,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("hand_back_default_sms_button")
                    ) {
                        Icon(
                            AppIcons.Undo,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hand Back Default SMS Role to $previousAppName",
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetectionRulesCard(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    customCount: Int,
    onRequestContactsPermission: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Active Detection Rules",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            FilterToggleRow(
                title = "De-duplicate Text Messages",
                subtitle = "Identifies identical SMS; keeps original copy and flags redundant duplicates",
                icon = AppIcons.ContentCopy,
                iconColor = AmberAccent,
                checked = uiState.filterDuplicates,
                onCheckedChange = { viewModel.toggleDuplicateFilter(it) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            FilterToggleRow(
                title = "OTPs & 2FA Codes",
                subtitle = "Finds one-time security passwords and codes",
                icon = AppIcons.Password,
                iconColor = PurpleAccent,
                checked = uiState.filterOtps,
                onCheckedChange = { viewModel.toggleOtpFilter(it) }
            )

            if (uiState.filterOtps) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Only Expired OTPs (> 15 minutes)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.onlyExpiredOtps,
                        onCheckedChange = { viewModel.toggleOnlyExpiredOtps(it) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            FilterToggleRow(
                title = "Promotions & Offers",
                subtitle = "Flags commercial marketing and discount coupons",
                icon = AppIcons.LocalOffer,
                iconColor = RoseAccent,
                checked = uiState.filterPromotions,
                onCheckedChange = { viewModel.togglePromotionFilter(it) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            FilterToggleRow(
                title = "Spam & Phishing Warnings",
                subtitle = "Detects urgent account suspension alerts and suspicious links",
                icon = AppIcons.Report,
                iconColor = RedDestructive,
                checked = uiState.filterSpam,
                onCheckedChange = { viewModel.toggleSpamFilter(it) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Lightweight Per-Contact Thread-Scoped Filter Section (only shows selected contacts; opens popup picker on demand)
            val scope = uiState.contactFilterScope
            val scopeSummary = if (scope.isActive) {
                if (scope.mode == ContactFilterMode.PROTECT_CONTACTS) {
                    "Protecting ${scope.selectedContactIds.size} contact(s) across ${scope.selectedThreadIds.size} thread(s)"
                } else {
                    "Scoped to ${scope.selectedContactIds.size} contact(s) (${scope.selectedThreadIds.size} thread(s)): deduplication & rules run only in these threads"
                }
            } else {
                null
            }
            ScannerContactFilterSection(
                selectedContacts = uiState.selectedContacts,
                hasContactsPermission = uiState.hasContactsPermission,
                filterEnabled = uiState.filterByContactEnabled,
                filterMode = uiState.contactFilterMode,
                scopeSummaryText = scopeSummary,
                onToggleFilterEnabled = { viewModel.toggleContactFilter(it) },
                onSelectFilterMode = { viewModel.setContactFilterMode(it) },
                onOpenContactPicker = { viewModel.openMainContactPicker() },
                onRemoveSelectedContact = { contact -> viewModel.toggleSelectedContact(contact) },
                onClearAll = { viewModel.clearSelectedContacts() },
                onRequestContactsPermission = onRequestContactsPermission
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(AppIcons.Tune, contentDescription = null, tint = TealAccent)
                    Column {
                        Text("Custom Cleaning Rules", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${uiState.customRules.count { it.isEnabled }} active rules configured", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    text = "$customCount matches",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealAccent
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Recently Deleted Folder Recovery Toggle
            FilterToggleRow(
                title = "Recently Deleted Folder Recovery",
                subtitle = "Keep deleted SMS for 30 days for batch inbox restoration (${uiState.deletedTestMessages.size} saved)",
                icon = AppIcons.RestoreFromTrash,
                iconColor = AmberAccent,
                checked = uiState.testingRestoreDeletedEnabled,
                onCheckedChange = { viewModel.toggleTestingRestoreDeleted(it) },
                switchTestTag = "testing_restore_deleted_switch"
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerContactFilterSection(
    selectedContacts: List<ContactInfo>,
    hasContactsPermission: Boolean,
    filterEnabled: Boolean,
    filterMode: ContactFilterMode,
    scopeSummaryText: String?,
    onToggleFilterEnabled: (Boolean) -> Unit,
    onSelectFilterMode: (ContactFilterMode) -> Unit,
    onOpenContactPicker: () -> Unit,
    onRemoveSelectedContact: (ContactInfo) -> Unit,
    onClearAll: () -> Unit,
    onRequestContactsPermission: () -> Unit
) {
    val selectedCount = selectedContacts.size
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(
            1.dp,
            if (filterEnabled && selectedCount > 0) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scanner_contact_filter_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.Contacts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Filter by Contact",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (selectedCount > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "$selectedCount selected",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Select contacts in a popup to scope de-duplication & cleaning to their threads",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }

                Switch(
                    checked = filterEnabled,
                    onCheckedChange = onToggleFilterEnabled,
                    modifier = Modifier.testTag("main_contact_filter_switch")
                )
            }

            // Mode Chips (Target Selected Threads vs Protect VIP Contacts)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filterMode == ContactFilterMode.TARGET_CONTACTS,
                    onClick = { onSelectFilterMode(ContactFilterMode.TARGET_CONTACTS) },
                    label = { Text("Target Selected", fontSize = 11.sp) },
                    leadingIcon = if (filterMode == ContactFilterMode.TARGET_CONTACTS) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    modifier = Modifier.testTag("contact_mode_target")
                )
                FilterChip(
                    selected = filterMode == ContactFilterMode.PROTECT_CONTACTS,
                    onClick = { onSelectFilterMode(ContactFilterMode.PROTECT_CONTACTS) },
                    label = { Text("Protect (VIP)", fontSize = 11.sp) },
                    leadingIcon = if (filterMode == ContactFilterMode.PROTECT_CONTACTS) {
                        { Icon(AppIcons.Shield, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    modifier = Modifier.testTag("contact_mode_protect")
                )
            }

            if (filterEnabled && selectedCount > 0 && scopeSummaryText != null) {
                val scopeAccent = if (filterMode == ContactFilterMode.PROTECT_CONTACTS) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Surface(
                    color = scopeAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scanner_contact_filter_scope_banner")
                ) {
                    Text(
                        text = scopeSummaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = scopeAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (!hasContactsPermission) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Grant Contacts permission to show address book names alongside numbers.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onRequestContactsPermission,
                            modifier = Modifier.testTag("scanner_contact_filter_grant_contacts_btn")
                        ) {
                            Text("Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Selected Contacts Only (no heavy inline list on ScannerScreen)
            if (selectedContacts.isEmpty()) {
                Surface(
                    onClick = onOpenContactPicker,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scanner_no_contacts_selected_hint")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.PersonSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "No contacts selected yet. Tap Select Contacts to pick from your list.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scanner_selected_contacts_container"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedContacts.forEach { contact ->
                        InputChip(
                            selected = true,
                            onClick = onOpenContactPicker,
                            label = {
                                Text(
                                    text = contact.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            avatar = {
                                Surface(
                                    shape = CircleShape,
                                    color = if (contact.isFromDeviceContacts) {
                                        TealAccent.copy(alpha = 0.22f)
                                    } else {
                                        PurpleAccent.copy(alpha = 0.22f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = contact.displayName.firstOrNull()?.uppercase() ?: "?",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (contact.isFromDeviceContacts) TealAccent else PurpleAccent
                                        )
                                    }
                                }
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveSelectedContact(contact) },
                                    modifier = Modifier
                                        .size(22.dp)
                                        .testTag("scanner_selected_contact_remove_${contact.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove ${contact.displayName}",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            },
                            modifier = Modifier.testTag("scanner_selected_contact_chip_${contact.id}")
                        )
                    }
                }
            }

            // Action Row: Select Contacts Popup Trigger + Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onOpenContactPicker,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("main_choose_contacts_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Contacts,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedCount > 0) "Select Contacts ($selectedCount)" else "Select Contacts",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                if (selectedCount > 0) {
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("scanner_contact_filter_clear_btn")
                    ) {
                        Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    count: Int,
    label: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.35f))
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = count.toString(),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun FilterToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier
        )
    }
}

@Composable
fun SmsPermissionsBanner(
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    hasContactsPermission: Boolean = false,
    onRequestSmsPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allGranted = hasReadPermission && hasWritePermission && hasContactsPermission
    val smsGranted = hasReadPermission && hasWritePermission
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allGranted) TealAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (allGranted) TealAccent.copy(alpha = 0.4f) else AmberAccent.copy(alpha = 0.4f)
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = if (allGranted) TealAccent.copy(alpha = 0.2f) else AmberAccent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (allGranted) AppIcons.VerifiedUser else AppIcons.Security,
                            contentDescription = null,
                            tint = if (allGranted) TealAccent else AmberAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (allGranted) "SMS & Contacts Permissions Active" else "Runtime Permissions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (allGranted) {
                            "App can scan, link contacts, and clean inbox"
                        } else {
                            "Grant SMS & Contacts access to scan and link Google Messages threads"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status badges for READ_SMS, WRITE_SMS, and READ_CONTACTS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PermissionChipBadge(
                    label = "READ_SMS",
                    granted = hasReadPermission,
                    modifier = Modifier.weight(1f)
                )
                PermissionChipBadge(
                    label = "WRITE_SMS",
                    granted = hasWritePermission,
                    modifier = Modifier.weight(1f)
                )
                PermissionChipBadge(
                    label = "CONTACTS",
                    granted = hasContactsPermission,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!smsGranted || !hasContactsPermission) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!smsGranted) {
                        Button(
                            onClick = onRequestSmsPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("request_sms_permissions_banner_btn")
                        ) {
                            Icon(AppIcons.LockOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Grant SMS", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    if (!hasContactsPermission) {
                        FilledTonalButton(
                            onClick = onRequestContactsPermission,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("request_contacts_permission_banner_btn")
                        ) {
                            Icon(AppIcons.Contacts, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Grant Contacts", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionChipBadge(
    label: String,
    granted: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (granted) TealAccent.copy(alpha = 0.15f) else AmberAccent.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, if (granted) TealAccent.copy(alpha = 0.4f) else AmberAccent.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (granted) TealAccent else AmberAccent,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (granted) TealAccent else AmberAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
