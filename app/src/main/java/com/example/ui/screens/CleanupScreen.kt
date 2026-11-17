package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ContactFilterMode
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.components.CustomRulesSection
import com.example.ui.components.SetupStatusPillButton
import com.example.ui.theme.*

@Composable
fun CleanupScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestContactsPermission: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    onHandBackDefaultSmsRole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subTab = uiState.cleanupSubTab
    val totalCleanable = uiState.summary.allCleanableCount

    // Step back from Review (1) or Rules (2) -> Scan (0) before popping Cleanup tab
    BackHandler(enabled = !uiState.hasOpenOverlay && subTab != 0) {
        viewModel.handleBackPress()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Segmented Sub-Menu Switcher (Scan | Review | Rules)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CleanupSubTabChip(
                        label = "Scan",
                        icon = AppIcons.Dashboard,
                        selected = subTab == 0,
                        onClick = { viewModel.setCleanupSubTab(0) },
                        testTag = "cleanup_tab_scan",
                        modifier = Modifier.weight(1f)
                    )
                    CleanupSubTabChip(
                        label = if (totalCleanable > 0) "Review ($totalCleanable)" else "Review",
                        icon = AppIcons.CleaningServices,
                        selected = subTab == 1,
                        onClick = { viewModel.setCleanupSubTab(1) },
                        testTag = "cleanup_tab_review",
                        modifier = Modifier.weight(1f)
                    )
                    CleanupSubTabChip(
                        label = "Rules (${uiState.customRules.size})",
                        icon = AppIcons.Tune,
                        selected = subTab == 2,
                        onClick = { viewModel.setCleanupSubTab(2) },
                        testTag = "cleanup_tab_rules",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        when (subTab) {
            0 -> CleanupScanPane(
                uiState = uiState,
                viewModel = viewModel,
                onOpenSetupSheet = { viewModel.openSetupStatusSheet() },
                onReviewClick = { viewModel.setCleanupSubTab(1) },
                onRulesClick = { viewModel.setCleanupSubTab(2) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            1 -> PreviewCleanScreen(
                uiState = uiState,
                viewModel = viewModel,
                onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                onHandBackDefaultSmsRole = onHandBackDefaultSmsRole,
                onOpenSetupSheet = { viewModel.openSetupStatusSheet() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            2 -> CleanupRulesPane(
                uiState = uiState,
                viewModel = viewModel,
                onRequestContactsPermission = onRequestContactsPermission,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CleanupSubTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = modifier
            .heightIn(min = 42.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CleanupScanPane(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onOpenSetupSheet: () -> Unit,
    onReviewClick: () -> Unit,
    onRulesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = uiState.summary
    val scrollState = rememberScrollState()

    val builtInActiveCount = listOf(
        uiState.filterDuplicates,
        uiState.filterOtps,
        uiState.filterPromotions,
        uiState.filterSpam
    ).count { it }
    val customActiveCount = uiState.customRules.count { it.isEnabled }

    BoxWithConstraints(modifier = modifier) {
        val isWide = maxWidth >= 720.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = if (isWide) 24.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Single compact pill replaces stacked source / permission / default-SMS banners
            SetupStatusPillButton(
                uiState = uiState,
                onClick = onOpenSetupSheet,
                testTag = "cleanup_setup_status_pill"
            )

            ClutterAnalysisCard(
                totalCleanable = summary.allCleanableCount,
                allMessagesCount = uiState.allMessages.size,
                duplicateCount = summary.duplicateCount,
                otpCount = summary.otpCount,
                promoCount = summary.promoCount,
                spamCount = summary.spamCount,
                isScanning = uiState.isScanning,
                onRefresh = { viewModel.refreshMessages() },
                onNavigateToPreview = onReviewClick
            )

            // Calm entry card directing users cleanly into Cleanup > Rules without crowding Scan
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRulesClick)
                    .testTag("cleanup_scan_rules_shortcut_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Detection & Custom Rules",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val contactNote = if (uiState.filterByContactEnabled && uiState.selectedContacts.isNotEmpty()) {
                                " • ${uiState.selectedContacts.size} contact filter(s)"
                            } else {
                                ""
                            }
                            Text(
                                text = "$builtInActiveCount built-in filters • $customActiveCount custom rules$contactNote",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Rules",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanupRulesPane(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestContactsPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var builtInExpanded by remember { mutableStateOf(true) }

    BoxWithConstraints(modifier = modifier) {
        val isWide = maxWidth >= 720.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                BuiltInFiltersAndContactCard(
                    uiState = uiState,
                    viewModel = viewModel,
                    expanded = builtInExpanded,
                    onToggleExpanded = { builtInExpanded = !builtInExpanded },
                    onRequestContactsPermission = onRequestContactsPermission,
                    modifier = Modifier.weight(1f)
                )
                CustomRulesSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BuiltInFiltersAndContactCard(
                    uiState = uiState,
                    viewModel = viewModel,
                    expanded = builtInExpanded,
                    onToggleExpanded = { builtInExpanded = !builtInExpanded },
                    onRequestContactsPermission = onRequestContactsPermission,
                    modifier = Modifier.fillMaxWidth()
                )

                CustomRulesSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BuiltInFiltersAndContactCard(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFilterCount = listOf(
        uiState.filterDuplicates,
        uiState.filterOtps,
        uiState.filterPromotions,
        uiState.filterSpam
    ).count { it }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .testTag("rules_builtin_accordion_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Built-in Detection Filters",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$activeFilterCount of 4 enabled • Per-contact thread scoping",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = if (expanded) "Collapse built-in filters" else "Expand built-in filters",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    FilterToggleRow(
                        title = "De-duplicate Text Messages",
                        subtitle = "Flags redundant duplicate copies while keeping original",
                        icon = AppIcons.ContentCopy,
                        iconColor = AmberAccent,
                        checked = uiState.filterDuplicates,
                        onCheckedChange = { viewModel.toggleDuplicateFilter(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    FilterToggleRow(
                        title = "OTPs & 2FA Codes",
                        subtitle = "Finds one-time verification passwords and codes",
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
                        subtitle = "Detects suspicious links and urgent alerts",
                        icon = AppIcons.Report,
                        iconColor = RedDestructive,
                        checked = uiState.filterSpam,
                        onCheckedChange = { viewModel.toggleSpamFilter(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

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
                }
            }
        }
    }
}
