package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SmsCategory
import com.example.telephony.TelephonyManagerHelper
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.theme.*

@Composable
fun ScannerScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestSmsPermission: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    onNavigateToPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val duplicateCount = uiState.classifiedMessages.count { it.category == SmsCategory.DUPLICATE && it.isDuplicateExtra }
    val otpCount = uiState.classifiedMessages.count { it.category == SmsCategory.OTP }
    val promoCount = uiState.classifiedMessages.count { it.category == SmsCategory.PROMOTION }
    val spamCount = uiState.classifiedMessages.count { it.category == SmsCategory.SPAM }
    val customCount = uiState.classifiedMessages.count { it.category == SmsCategory.CUSTOM_RULE }
    val totalCleanable = duplicateCount + otpCount + promoCount + spamCount + customCount

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
                        onRefresh = { viewModel.refreshMessages() },
                        onNavigateToPreview = onNavigateToPreview
                    )
                }

                // Right Column: Default SMS Status & Detection Rules
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TelephonyBanner(
                        isDefaultSmsApp = uiState.isDefaultSmsApp,
                        onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                        context = context
                    )

                    DetectionRulesCard(
                        uiState = uiState,
                        viewModel = viewModel,
                        customCount = customCount
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
                    onRefresh = { viewModel.refreshMessages() },
                    onNavigateToPreview = onNavigateToPreview
                )

                TelephonyBanner(
                    isDefaultSmsApp = uiState.isDefaultSmsApp,
                    onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                    context = context
                )

                DetectionRulesCard(
                    uiState = uiState,
                    viewModel = viewModel,
                    customCount = customCount
                )
            }
        }
    }
}

@Composable
private fun DataSourceCard(
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isDemoMode) CyanDark.copy(alpha = 0.2f) else TealAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isDemoMode) Icons.Default.Science else Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = if (uiState.isDemoMode) CyanAccent else TealAccent,
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
                        text = if (uiState.isDemoMode) "30+ simulated messages & duplicates" else "${uiState.allMessages.size} real messages",
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
                        if (isDevice && !uiState.hasSmsPermission) {
                            onRequestSmsPermission()
                        }
                        viewModel.setDemoMode(!isDevice)
                    },
                    modifier = Modifier.testTag("demo_mode_switch")
                )
            }
        }
    }
}

@Composable
private fun ClutterAnalysisCard(
    totalCleanable: Int,
    allMessagesCount: Int,
    duplicateCount: Int,
    otpCount: Int,
    promoCount: Int,
    spamCount: Int,
    onRefresh: () -> Unit,
    onNavigateToPreview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Navy800, Navy700.copy(alpha = 0.8f))
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
                            text = "Inbox Analysis",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "$totalCleanable Messages to Clean",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CardBorder)
                            .testTag("refresh_scan_button")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Rescan",
                            tint = CyanAccent
                        )
                    }
                }

                Text(
                    text = "Out of $allMessagesCount total messages in your inbox, we found redundant copies, expired verification codes, ads, and spam.",
                    fontSize = 13.sp,
                    color = TextSecondary,
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
                        icon = Icons.Default.ContentCopy,
                        modifier = Modifier.weight(1f)
                    )
                    StatBadge(
                        count = otpCount,
                        label = "OTPs",
                        color = PurpleAccent,
                        icon = Icons.Default.Password,
                        modifier = Modifier.weight(1f)
                    )
                    StatBadge(
                        count = promoCount,
                        label = "Promos",
                        color = RoseAccent,
                        icon = Icons.Default.LocalOffer,
                        modifier = Modifier.weight(1f)
                    )
                    StatBadge(
                        count = spamCount,
                        label = "Spam",
                        color = RedDestructive,
                        icon = Icons.Default.Report,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = onNavigateToPreview,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("preview_clean_button")
                ) {
                    Text(
                        text = "Review & Clean Messages ($totalCleanable)",
                        color = Navy900,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Navy900,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelephonyBanner(
    isDefaultSmsApp: Boolean,
    onRequestDefaultSmsRole: () -> Unit,
    context: android.content.Context
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefaultSmsApp) TealAccent.copy(alpha = 0.12f) else AmberAccent.copy(alpha = 0.12f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isDefaultSmsApp) TealAccent.copy(alpha = 0.4f) else AmberAccent.copy(alpha = 0.4f)
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isDefaultSmsApp) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isDefaultSmsApp) TealAccent else AmberAccent,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = if (isDefaultSmsApp) "Default SMS App Active" else "Default SMS Role Required for Deletion",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isDefaultSmsApp)
                            "CleanSMS can delete directly from Google Messages"
                        else
                            "Android requires temporary Default SMS role to permanently delete messages from Google Messages",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isDefaultSmsApp) {
                Button(
                    onClick = onRequestDefaultSmsRole,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_default_sms_button")
                ) {
                    Text("Set as Default SMS App", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val defaultPkg = "com.google.android.apps.messaging"
                        val restoreIntent = TelephonyManagerHelper.createRestoreDefaultSmsIntent(defaultPkg)
                        try {
                            context.startActivity(restoreIntent)
                        } catch (_: Exception) {
                            onRequestDefaultSmsRole()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Switch Back to Google Messages")
                }
            }
        }
    }
}

@Composable
private fun DetectionRulesCard(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    customCount: Int
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
                icon = Icons.Default.ContentCopy,
                iconColor = AmberAccent,
                checked = uiState.filterDuplicates,
                onCheckedChange = { viewModel.toggleDuplicateFilter(it) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            FilterToggleRow(
                title = "OTPs & 2FA Codes",
                subtitle = "Finds one-time security passwords and codes",
                icon = Icons.Default.Password,
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
                icon = Icons.Default.LocalOffer,
                iconColor = RoseAccent,
                checked = uiState.filterPromotions,
                onCheckedChange = { viewModel.togglePromotionFilter(it) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            FilterToggleRow(
                title = "Spam & Phishing Warnings",
                subtitle = "Detects urgent account suspension alerts and suspicious links",
                icon = Icons.Default.Report,
                iconColor = RedDestructive,
                checked = uiState.filterSpam,
                onCheckedChange = { viewModel.toggleSpamFilter(it) }
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
                    Icon(Icons.Default.Tune, contentDescription = null, tint = TealAccent)
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
        color = CardDark,
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.3f))
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
                color = TextPrimary
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FilterToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
