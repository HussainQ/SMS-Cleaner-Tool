package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.screens.DataSourceCard
import com.example.ui.screens.SmsPermissionsBanner
import com.example.ui.screens.TelephonyBanner
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AppIcons
import com.example.ui.theme.TealAccent

/**
 * Compact 1-line status chip that replaces multiple inline permission/source/Default-SMS banners
 * across Messages and Cleanup. Tapping opens [SetupStatusSheet].
 */
@Composable
fun SetupStatusPillButton(
    uiState: CleanSmsUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "setup_status_pill_button"
) {
    val smsGranted = uiState.hasReadSmsPermission && uiState.hasWriteSmsPermission
    val needsSetup = !uiState.isDemoMode && (!smsGranted || !uiState.isDefaultSmsApp)

    val containerColor = when {
        uiState.isDemoMode -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        uiState.isDefaultSmsApp && smsGranted -> TealAccent.copy(alpha = 0.14f)
        else -> AmberAccent.copy(alpha = 0.14f)
    }
    val accentColor = when {
        uiState.isDemoMode -> MaterialTheme.colorScheme.primary
        uiState.isDefaultSmsApp && smsGranted -> TealAccent
        else -> AmberAccent
    }
    val borderColor = when {
        uiState.isDemoMode -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        else -> accentColor.copy(alpha = 0.45f)
    }
    val label = when {
        uiState.isDemoMode -> "Demo Inbox • Tap for Device Setup"
        !smsGranted -> "Action Needed • Grant SMS Access"
        uiState.isDefaultSmsApp -> "Live SMS • Default SMS App"
        else -> "Live SMS • Tap to Set Default"
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 42.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when {
                        uiState.isDemoMode -> AppIcons.Science
                        uiState.isDefaultSmsApp && smsGranted -> AppIcons.Verified
                        else -> AppIcons.AdminPanelSettings
                    },
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (needsSetup) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (needsSetup) "Resolve" else "Manage",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

/**
 * Unified ModalBottomSheet consolidating Inbox Source (Demo vs Live Device SMS),
 * Runtime Permissions, and Default SMS Role handover without cluttering primary screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupStatusSheet(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestSmsPermission: () -> Unit,
    onRequestSendSmsPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    onHandBackDefaultSmsRole: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("setup_status_modal_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Inbox Source & Permissions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Switch between Demo & Live SMS, manage runtime permissions, and handle Default SMS role",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("setup_status_sheet_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close setup sheet")
                }
            }

            DataSourceCard(
                uiState = uiState,
                viewModel = viewModel,
                onRequestSmsPermission = onRequestSmsPermission
            )

            SmsPermissionsBanner(
                hasReadPermission = uiState.hasReadSmsPermission,
                hasWritePermission = uiState.hasWriteSmsPermission,
                hasContactsPermission = uiState.hasContactsPermission,
                onRequestSmsPermission = onRequestSmsPermission,
                onRequestContactsPermission = onRequestContactsPermission
            )

            if (!uiState.isDemoMode && !uiState.hasSendSmsPermission) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AmberAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Send SMS Permission Needed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Allow carrier SMS sending from the Messages tab",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onRequestSendSmsPermission,
                            modifier = Modifier.testTag("sheet_grant_send_sms_btn")
                        ) {
                            Text("Allow", fontWeight = FontWeight.Bold, color = AmberAccent)
                        }
                    }
                }
            }

            TelephonyBanner(
                isDefaultSmsApp = uiState.isDefaultSmsApp,
                previousAppName = uiState.previousDefaultSmsAppName,
                onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                onHandBackDefaultSmsRole = onHandBackDefaultSmsRole
            )
        }
    }
}
