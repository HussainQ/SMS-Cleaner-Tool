package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.SmsCategory
import com.example.telephony.TelephonyManagerHelper
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.components.AddCustomRuleSheet
import com.example.ui.components.MessageDetailSheet
import com.example.ui.components.PermanentDeleteConfirmDialog
import com.example.ui.components.RestoreCloudDialog
import com.example.ui.screens.PreviewCleanScreen
import com.example.ui.screens.RulesAndCloudScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.VaultRestoreScreen
import com.example.ui.theme.CleanSMSTheme
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.Navy900
import com.example.ui.theme.TealAccent

enum class NavigationTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SCANNER("Scanner", Icons.Default.Dashboard),
    PREVIEW("Review", Icons.Default.CleaningServices),
    VAULT("Vault", Icons.Default.Restore),
    RULES_CLOUD("Rules & Cloud", Icons.Default.Tune)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CleanSMSTheme {
                CleanSmsApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanSmsApp(
    viewModel: CleanSmsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by remember { mutableStateOf(NavigationTab.SCANNER) }

    // Check initial permission
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setSmsPermissionGranted(granted)
        viewModel.checkTelephonyState()
    }

    // Permission launcher for READ_SMS
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setSmsPermissionGranted(isGranted)
    }

    // Activity launcher for default SMS app role request
    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkTelephonyState()
    }

    // Handle snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Badge counts
    val cleanableCount = remember(uiState.classifiedMessages) {
        uiState.classifiedMessages.count { it.isCleanCandidate }
    }
    val quarantinedCount = remember(uiState.quarantinedMessages) {
        uiState.quarantinedMessages.size
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            // Adaptive Tablet & Foldable Layout with Side NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.statusBars),
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape),
                            color = CyanAccent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = "Logo",
                                    tint = Navy900,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        val badgeCount = when (tab) {
                            NavigationTab.PREVIEW -> cleanableCount
                            NavigationTab.VAULT -> quarantinedCount
                            else -> 0
                        }

                        NavigationRailItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (badgeCount > 0) {
                                            Badge(
                                                containerColor = if (tab == NavigationTab.VAULT) TealAccent else CyanAccent,
                                                contentColor = Color.Black
                                            ) {
                                                Text(badgeCount.toString(), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            },
                            label = { Text(tab.title, fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { viewModel.refreshMessages() },
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .testTag("rail_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = CyanAccent)
                    }
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Scaffold(
                    modifier = Modifier.weight(1f),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "CleanSMS • ${currentTab.title}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = "Google Messages Cleaner & Duplication Vault",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AppScreenContent(
                            currentTab = currentTab,
                            uiState = uiState,
                            viewModel = viewModel,
                            onRequestSmsPermission = { smsPermissionLauncher.launch(Manifest.permission.READ_SMS) },
                            onRequestDefaultSmsRole = {
                                val intent = TelephonyManagerHelper.createRequestDefaultSmsIntent(context)
                                defaultSmsLauncher.launch(intent)
                            },
                            onNavigateToPreview = { currentTab = NavigationTab.PREVIEW }
                        )
                    }
                }
            }
        } else {
            // Standard Phone Compact Layout with Bottom NavigationBar
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape),
                                    color = CyanAccent
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CleaningServices,
                                            contentDescription = "Logo",
                                            tint = Navy900,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "CleanSMS",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = "Google Messages Cleaner",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            if (currentTab != NavigationTab.SCANNER) {
                                IconButton(onClick = { viewModel.refreshMessages() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationTab.entries.forEach { tab ->
                            val isSelected = currentTab == tab
                            val badgeCount = when (tab) {
                                NavigationTab.PREVIEW -> cleanableCount
                                NavigationTab.VAULT -> quarantinedCount
                                else -> 0
                            }

                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentTab = tab },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge(
                                                    containerColor = if (tab == NavigationTab.VAULT) TealAccent else CyanAccent,
                                                    contentColor = Color.Black
                                                ) {
                                                    Text(badgeCount.toString(), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(tab.icon, contentDescription = tab.title)
                                    }
                                },
                                label = { Text(tab.title, fontSize = 12.sp) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AppScreenContent(
                        currentTab = currentTab,
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequestSmsPermission = { smsPermissionLauncher.launch(Manifest.permission.READ_SMS) },
                        onRequestDefaultSmsRole = {
                            val intent = TelephonyManagerHelper.createRequestDefaultSmsIntent(context)
                            defaultSmsLauncher.launch(intent)
                        },
                        onNavigateToPreview = { currentTab = NavigationTab.PREVIEW }
                    )
                }
            }
        }
    }

    // Confirmation Step Dialog before Permanent Deletions
    if (uiState.showPermanentDeleteDialog) {
        PermanentDeleteConfirmDialog(
            messages = uiState.deleteTargetMessages,
            isFromQuarantine = uiState.isDeletingFromQuarantine,
            onConfirm = { viewModel.confirmPermanentDelete() },
            onDismiss = { viewModel.dismissPermanentDeleteDialog() }
        )
    }

    // Add Custom Rule BottomSheet
    if (uiState.showAddCustomRuleSheet) {
        AddCustomRuleSheet(
            existingMessages = uiState.allMessages,
            onSaveRule = { rule -> viewModel.saveCustomRule(rule) },
            onDismiss = { viewModel.closeAddRuleSheet() }
        )
    }

    // Cloud Restore Dialog
    if (uiState.showRestoreCloudDialog && uiState.activeRestorePayload != null) {
        RestoreCloudDialog(
            payload = uiState.activeRestorePayload!!,
            onConfirmRestore = { viewModel.confirmRestoreFromCloud() },
            onDismiss = { viewModel.dismissRestoreCloudDialog() }
        )
    }

    // Message Inspection Details Sheet
    if (uiState.detailMessage != null) {
        MessageDetailSheet(
            message = uiState.detailMessage!!,
            onDismiss = { viewModel.showMessageDetails(null) }
        )
    }

    // Explicit Device OAuth Authorization Dialog
    if (uiState.serviceToAuthorize != null) {
        com.example.ui.components.AuthorizeServiceDialog(
            serviceDef = uiState.serviceToAuthorize!!,
            availableAccounts = uiState.availableDeviceAccounts,
            selectedAccount = uiState.selectedAccountForAuth,
            isAuthorizing = uiState.isAuthorizingService,
            onSelectAccount = { viewModel.setSelectedAccountForAuth(it) },
            onConfirmAuthorization = { viewModel.confirmServiceAuthorization() },
            onDismiss = { viewModel.dismissServiceAuthorization() }
        )
    }
}

@Composable
private fun AppScreenContent(
    currentTab: NavigationTab,
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestSmsPermission: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    onNavigateToPreview: () -> Unit
) {
    when (currentTab) {
        NavigationTab.SCANNER -> {
            ScannerScreen(
                uiState = uiState,
                viewModel = viewModel,
                onRequestSmsPermission = onRequestSmsPermission,
                onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                onNavigateToPreview = onNavigateToPreview
            )
        }

        NavigationTab.PREVIEW -> {
            PreviewCleanScreen(
                uiState = uiState,
                viewModel = viewModel
            )
        }

        NavigationTab.VAULT -> {
            VaultRestoreScreen(
                uiState = uiState,
                viewModel = viewModel
            )
        }

        NavigationTab.RULES_CLOUD -> {
            RulesAndCloudScreen(
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}
