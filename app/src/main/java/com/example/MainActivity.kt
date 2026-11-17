package com.example

import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ComposeAttachment
import com.example.model.MessagingHelper
import com.example.model.PhoneNormalizer
import com.example.telephony.TelephonyManagerHelper
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.components.AddCustomRuleSheet
import com.example.ui.components.AppUpdatesDialog
import com.example.ui.components.ContactPickerDialog
import com.example.ui.components.MessageDetailSheet
import com.example.ui.components.PermanentDeleteConfirmDialog
import com.example.ui.components.RestoreCloudDialog
import com.example.update.InAppApkInstaller
import com.example.update.InAppUpdateInstallPhase
import com.example.update.UpdateCheckStatus
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.PreviewCleanScreen
import com.example.ui.screens.RulesAndCloudScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.VaultRestoreScreen
import com.example.ui.theme.CleanSMSTheme
import com.example.ui.theme.LocalCleanSmsThemeManaged
import java.io.File

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.example.ui.theme.AppIcons

enum class NavigationTab(val title: String, @param:DrawableRes val iconRes: Int) {
    MESSAGES("Messages", R.drawable.ic_m_forum),
    SCANNER("Scanner", R.drawable.ic_m_dashboard),
    PREVIEW("Review", R.drawable.ic_m_cleaning_services),
    VAULT("Vault", R.drawable.ic_m_restore),
    RULES_CLOUD("Rules & Cloud", R.drawable.ic_m_tune)
}

const val PERMISSION_WRITE_SMS = "android.permission.WRITE_SMS"

class MainActivity : ComponentActivity() {

    private var windowFocusSyncTrigger by mutableIntStateOf(0)
    private var pendingSendIntentPayload by mutableStateOf<Pair<String, String?>?>(null)
    private var sendIntentSequence by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureIncomingSendIntent(intent)

        setContent {
            val mainViewModel: CleanSmsViewModel = viewModel(
                factory = CleanSmsViewModel.Factory(application)
            )
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val effectiveDark = if (uiState.useDeviceMaterialTheme) systemDark else true

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (effectiveDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (effectiveDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            CleanSMSTheme(
                useDeviceMaterialTheme = uiState.useDeviceMaterialTheme,
                darkTheme = effectiveDark,
                dynamicColor = uiState.useDeviceMaterialTheme,
                themeManaged = true
            ) {
                CleanSmsApp(
                    windowFocusSyncTrigger = windowFocusSyncTrigger,
                    externalSendIntent = pendingSendIntentPayload,
                    externalSendIntentSequence = sendIntentSequence,
                    viewModel = mainViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureIncomingSendIntent(intent)
    }

    private fun captureIncomingSendIntent(intent: Intent?) {
        val parsed = MessagingHelper.parseSendIntent(intent)
        if (parsed != null) {
            pendingSendIntentPayload = parsed
            sendIntentSequence++
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            windowFocusSyncTrigger++
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanSmsApp(
    windowFocusSyncTrigger: Int = 0,
    externalSendIntent: Pair<String, String?>? = null,
    externalSendIntentSequence: Int = 0,
    viewModel: CleanSmsViewModel = viewModel(
        factory = CleanSmsViewModel.Factory(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentTab = uiState.currentTab

    // Base Navigation Tab BackHandler (pops visited tabs in reverse click order down to Messages root)
    BackHandler(
        enabled = !uiState.hasOpenOverlay &&
            (uiState.tabBackStack.size > 1 || currentTab != NavigationTab.MESSAGES)
    ) {
        viewModel.navigateBackTab()
    }

    LaunchedEffect(externalSendIntentSequence) {
        if (externalSendIntentSequence > 0 && externalSendIntent != null) {
            viewModel.handleExternalSendIntent(
                recipient = externalSendIntent.first,
                body = externalSendIntent.second
            )
        }
    }

    // Check initial permissions (SMS read/write/send + Contacts) and re-check on lifecycle resume
    val checkPermissions = remember(context) {
        {
            val readGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
            val writeGranted = ContextCompat.checkSelfPermission(
                context,
                PERMISSION_WRITE_SMS
            ) == PackageManager.PERMISSION_GRANTED
            val sendGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            val contactsGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.setSmsPermissions(
                readGranted = readGranted,
                writeGranted = writeGranted,
                sendGranted = sendGranted
            )
            viewModel.setContactsPermission(contactsGranted)
            viewModel.checkTelephonyState()
        }
    }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    LaunchedEffect(windowFocusSyncTrigger, currentTab) {
        viewModel.checkTelephonyState()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START ||
                event == androidx.lifecycle.Lifecycle.Event.ON_RESUME
            ) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission launcher requesting READ_SMS, WRITE_SMS, and SEND_SMS
    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val readGranted = permissionsMap[Manifest.permission.READ_SMS] == true
        val writeGranted = permissionsMap[PERMISSION_WRITE_SMS] == true
        val sendGranted = permissionsMap[Manifest.permission.SEND_SMS] == true
        val bothGranted = readGranted && writeGranted
        val activity = context as? androidx.activity.ComponentActivity

        viewModel.setSmsPermissions(
            readGranted = readGranted,
            writeGranted = writeGranted,
            sendGranted = sendGranted
        )

        if (bothGranted) {
            viewModel.showSnackbar("SMS permissions granted. Device messaging & inbox active.")
        } else if (activity != null) {
            val shouldShowRead = androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)
            val shouldShowWrite = androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION_WRITE_SMS)
            if (!shouldShowRead && !shouldShowWrite && !readGranted) {
                viewModel.openPermissionPermanentlyDeniedDialog()
            } else {
                viewModel.openPermissionRationaleDialog()
            }
        }
    }

    val sendSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val currentState = viewModel.uiState.value
        viewModel.setSmsPermissions(
            readGranted = currentState.hasReadSmsPermission,
            writeGranted = currentState.hasWriteSmsPermission,
            sendGranted = granted
        )
        if (granted) {
            viewModel.showSnackbar("SEND_SMS permission granted. Ready to send messages.")
        } else {
            viewModel.showSnackbar("SEND_SMS permission denied. Carrier messaging unavailable.")
        }
    }

    // Zero-permission Photo Picker for MMS media attachments (Play Policy compliant)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            var displayName = "attachment.jpg"
            var sizeBytes = 0L
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                        if (sizeIdx >= 0) {
                            sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }
            }
            viewModel.setComposerAttachment(
                ComposeAttachment(
                    uriString = uri.toString(),
                    mimeType = mimeType,
                    displayName = displayName,
                    sizeBytes = sizeBytes
                )
            )
        }
    }

    val pickMediaAction: () -> Unit = remember(mediaPickerLauncher) {
        {
            mediaPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    // Permission launcher for READ_CONTACTS (links device contacts to Google Messages threads)
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setContactsPermission(granted)
        if (granted) {
            viewModel.showSnackbar("Contacts permission granted. Linking messages to device contacts.")
        } else {
            viewModel.showSnackbar("Contacts access denied. Using message thread senders only.")
        }
    }

    val requestSendSmsPermissionAction: () -> Unit = remember(sendSmsPermissionLauncher, context, viewModel) {
        {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                val cur = viewModel.uiState.value
                viewModel.setSmsPermissions(cur.hasReadSmsPermission, cur.hasWriteSmsPermission, true)
            } else {
                sendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            }
        }
    }

    val requestSmsPermissionsAction: () -> Unit = remember(smsPermissionsLauncher) {
        {
            val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            val writeGranted = ContextCompat.checkSelfPermission(context, PERMISSION_WRITE_SMS) == PackageManager.PERMISSION_GRANTED
            val sendGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            if (readGranted && writeGranted && sendGranted) {
                viewModel.setSmsPermissions(readGranted = true, writeGranted = true, sendGranted = true)
            } else {
                smsPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_SMS,
                        PERMISSION_WRITE_SMS,
                        Manifest.permission.SEND_SMS
                    )
                )
            }
        }
    }

    val requestContactsPermissionAction: () -> Unit = remember(contactsPermissionLauncher) {
        {
            val contactsGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (contactsGranted) {
                viewModel.setContactsPermission(true)
            } else {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    // Activity launcher for default SMS app role request / hand-back
    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val prevState = viewModel.uiState.value
        val wasDefault = prevState.isDefaultSmsApp
        val isNowDefault = viewModel.checkTelephonyState()
        val updatedState = viewModel.uiState.value
        if (!wasDefault && isNowDefault) {
            viewModel.showSnackbar("Default SMS App active (taken from ${updatedState.previousDefaultSmsAppName}). Ready to delete permanently.")
        } else if (wasDefault && !isNowDefault) {
            viewModel.showSnackbar("Default SMS Role handed back to ${updatedState.previousDefaultSmsAppName}.")
        }
    }

    val requestDefaultSmsRoleAction: () -> Unit = remember(defaultSmsLauncher, context, viewModel) {
        {
            if (TelephonyManagerHelper.isDefaultSmsApp(context)) {
                viewModel.checkTelephonyState()
                viewModel.showSnackbar("CleanSMS is already set as the Default SMS App.")
            } else {
                try {
                    val intent = TelephonyManagerHelper.createRequestDefaultSmsIntent(context)
                    defaultSmsLauncher.launch(intent)
                } catch (_: Exception) {
                    try {
                        defaultSmsLauncher.launch(TelephonyManagerHelper.createManageDefaultAppsIntent())
                    } catch (_: Exception) {
                        viewModel.showSnackbar("Open System Settings > Default Apps > SMS app to select CleanSMS.")
                    }
                }
            }
        }
    }

    val handBackDefaultSmsRoleAction: () -> Unit = remember(defaultSmsLauncher, context, viewModel) {
        {
            val state = viewModel.uiState.value
            try {
                val restoreIntent = TelephonyManagerHelper.createRestoreDefaultSmsIntent(
                    context = context,
                    targetPackage = state.previousDefaultSmsPackage
                )
                defaultSmsLauncher.launch(restoreIntent)
            } catch (_: Exception) {
                viewModel.showSnackbar("Open System Settings > Default Apps > SMS app to select ${state.previousDefaultSmsAppName}.")
            }
        }
    }

    // Activity launcher for unknown-app-sources permission screen (Android 8+)
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onInstallPermissionReturned()
    }

    val requestUnknownAppInstallPermissionAction: () -> Unit = remember(installPermissionLauncher, context, viewModel) {
        {
            try {
                installPermissionLauncher.launch(
                    InAppApkInstaller.createUnknownAppSourcesSettingsIntent(context)
                )
            } catch (_: Exception) {
                viewModel.markApkInstallFailed(
                    "Open Android Settings > Apps > CleanSMS > Install unknown apps and enable permission."
                )
            }
        }
    }

    val launchDownloadedApkInstallAction: () -> Unit = remember(context, viewModel, requestUnknownAppInstallPermissionAction) {
        {
            val apkPath = viewModel.uiState.value.inAppDownloadState.apkFilePath
            if (apkPath.isNullOrBlank()) {
                viewModel.markApkInstallFailed("No downloaded APK file found.")
            } else {
                val apkFile = File(apkPath)
                if (!apkFile.exists() || apkFile.length() == 0L) {
                    viewModel.markApkInstallFailed("Downloaded APK file is missing or empty. Tap Retry to download again.")
                } else if (!InAppApkInstaller.canInstallUnknownApps(context)) {
                    requestUnknownAppInstallPermissionAction()
                } else {
                    try {
                        val installIntent = InAppApkInstaller.createInstallApkIntent(context, apkFile)
                        context.startActivity(installIntent)
                        viewModel.markApkInstallLaunched()
                    } catch (e: Exception) {
                        viewModel.markApkInstallFailed(
                            "Unable to launch system package installer: ${e.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        }
    }

    // Automatically launch system package installer as soon as an in-app downloaded APK is ready
    LaunchedEffect(uiState.inAppDownloadState.phase, uiState.inAppDownloadState.apkFilePath) {
        val dl = uiState.inAppDownloadState
        val path = dl.apkFilePath
        if (dl.phase == InAppUpdateInstallPhase.READY_TO_INSTALL &&
            !path.isNullOrBlank() &&
            File(path).exists() &&
            File(path).length() > 0L
        ) {
            launchDownloadedApkInstallAction()
        }
    }

    // Handle snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Badge counts (single-pass summary)
    val unreadMessagesCount = uiState.totalUnreadMessagesCount
    val cleanableCount = uiState.summary.allCleanableCount
    val quarantinedCount = uiState.quarantinedMessages.size

    AdaptiveCleanSmsThemeIfUnmanaged(useDeviceMaterialTheme = uiState.useDeviceMaterialTheme) {
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
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.CleaningServices,
                                    contentDescription = "Logo",
                                    tint = MaterialTheme.colorScheme.onPrimary,
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
                            NavigationTab.MESSAGES -> unreadMessagesCount
                            NavigationTab.PREVIEW -> cleanableCount
                            NavigationTab.VAULT -> quarantinedCount
                            else -> 0
                        }

                        NavigationRailItem(
                            selected = isSelected,
                            onClick = { viewModel.navigateToTab(tab) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (badgeCount > 0) {
                                            Badge(
                                                containerColor = if (tab == NavigationTab.VAULT) {
                                                    MaterialTheme.colorScheme.secondary
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                                contentColor = if (tab == NavigationTab.VAULT) {
                                                    MaterialTheme.colorScheme.onSecondary
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimary
                                                }
                                            ) {
                                                Text(badgeCount.toString(), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(ImageVector.vectorResource(tab.iconRes), contentDescription = tab.title)
                                }
                            },
                            label = { Text(tab.title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Scaffold(
                    modifier = Modifier.weight(1f),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        val isPreReleaseBuild = BuildConfig.VERSION_NAME.contains("-")
                        TopAppBar(
                            title = {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "CleanSMS • ${currentTab.title}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                        Surface(
                                            color = if (isPreReleaseBuild) {
                                                Color(0xFFFFB300).copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            },
                                            shape = CircleShape,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isPreReleaseBuild) {
                                                    Color(0xFFFFB300).copy(alpha = 0.5f)
                                                } else {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                }
                                            ),
                                            modifier = Modifier.testTag("prerelease_badge_wide")
                                        ) {
                                            Text(
                                                text = if (isPreReleaseBuild) {
                                                    "PRE-RELEASE v${BuildConfig.VERSION_NAME} • LOCAL TEST ONLY"
                                                } else {
                                                    "v${BuildConfig.VERSION_NAME}"
                                                },
                                                color = if (isPreReleaseBuild) {
                                                    Color(0xFFFFB300)
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isPreReleaseBuild) {
                                            "Non-Production Build • For Local Device Testing Only"
                                        } else {
                                            "Intelligent SMS, MMS & RCS Organizer"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            actions = {
                                val hasUpdate = uiState.updateCheckResult?.isUpdateAvailable == true
                                IconButton(
                                    onClick = { viewModel.openUpdatesDialog() },
                                    modifier = Modifier.testTag("top_bar_updates_btn_wide")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (hasUpdate) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.secondary,
                                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                                ) {
                                                    Text("!", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (hasUpdate) AppIcons.SystemUpdate else AppIcons.Update,
                                            contentDescription = "Check for GitHub Updates",
                                            tint = if (hasUpdate) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
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
                            onRequestSmsPermission = requestSmsPermissionsAction,
                            onRequestSendSmsPermission = requestSendSmsPermissionAction,
                            onRequestContactsPermission = requestContactsPermissionAction,
                            onRequestDefaultSmsRole = requestDefaultSmsRoleAction,
                            onHandBackDefaultSmsRole = handBackDefaultSmsRoleAction,
                            onPickMedia = pickMediaAction,
                            onNavigateToPreview = { viewModel.navigateToTab(NavigationTab.PREVIEW) }
                        )
                        SmsScanProgressOverlay(isScanning = uiState.isScanning)
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
                            val isPreReleaseBuild = BuildConfig.VERSION_NAME.contains("-")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = AppIcons.CleaningServices,
                                            contentDescription = "Logo",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "CleanSMS",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp
                                        )
                                        Surface(
                                            color = if (isPreReleaseBuild) {
                                                Color(0xFFFFB300).copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            },
                                            shape = CircleShape,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isPreReleaseBuild) {
                                                    Color(0xFFFFB300).copy(alpha = 0.5f)
                                                } else {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                }
                                            ),
                                            modifier = Modifier.testTag("prerelease_badge_compact")
                                        ) {
                                            Text(
                                                text = if (isPreReleaseBuild) {
                                                    "PRE v${BuildConfig.VERSION_NAME}"
                                                } else {
                                                    "v${BuildConfig.VERSION_NAME}"
                                                },
                                                color = if (isPreReleaseBuild) {
                                                    Color(0xFFFFB300)
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isPreReleaseBuild) {
                                            "Non-Production • Local Device Testing"
                                        } else {
                                            "SMS, MMS & RCS Organizer"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            val hasUpdate = uiState.updateCheckResult?.isUpdateAvailable == true
                            IconButton(
                                onClick = { viewModel.openUpdatesDialog() },
                                modifier = Modifier.testTag("top_bar_updates_btn")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (hasUpdate) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.secondary,
                                                contentColor = MaterialTheme.colorScheme.onSecondary
                                            ) {
                                                Text("!", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (hasUpdate) AppIcons.SystemUpdate else AppIcons.Update,
                                        contentDescription = "Check for GitHub Updates",
                                        tint = if (hasUpdate) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
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
                                NavigationTab.MESSAGES -> unreadMessagesCount
                                NavigationTab.PREVIEW -> cleanableCount
                                NavigationTab.VAULT -> quarantinedCount
                                else -> 0
                            }

                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.navigateToTab(tab) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge(
                                                    containerColor = if (tab == NavigationTab.VAULT) {
                                                        MaterialTheme.colorScheme.secondary
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    },
                                                    contentColor = if (tab == NavigationTab.VAULT) {
                                                        MaterialTheme.colorScheme.onSecondary
                                                    } else {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    }
                                                ) {
                                                    Text(badgeCount.toString(), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(ImageVector.vectorResource(tab.iconRes), contentDescription = tab.title)
                                    }
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
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
                        onRequestSmsPermission = requestSmsPermissionsAction,
                        onRequestSendSmsPermission = requestSendSmsPermissionAction,
                        onRequestContactsPermission = requestContactsPermissionAction,
                        onRequestDefaultSmsRole = requestDefaultSmsRoleAction,
                        onHandBackDefaultSmsRole = handBackDefaultSmsRoleAction,
                        onPickMedia = pickMediaAction,
                        onNavigateToPreview = { viewModel.navigateToTab(NavigationTab.PREVIEW) }
                    )
                    SmsScanProgressOverlay(isScanning = uiState.isScanning)
                }
            }
        }
    }

    // Window Overlay BackHandler (registered after screen content so open modals dismiss before sub-levels or tabs)
    BackHandler(enabled = uiState.hasOpenOverlay) {
        viewModel.dismissTopOverlay()
    }

    // Confirmation Step Dialog before Permanent Deletions
    if (uiState.showPermanentDeleteDialog) {
        PermanentDeleteConfirmDialog(
            messages = uiState.deleteTargetMessages,
            isFromQuarantine = uiState.isDeletingFromQuarantine,
            testingRestoreEnabled = uiState.testingRestoreDeletedEnabled,
            isDemoMode = uiState.isDemoMode,
            isDefaultSmsApp = uiState.isDefaultSmsApp,
            onRequestDefaultSmsRole = requestDefaultSmsRoleAction,
            onConfirm = { viewModel.confirmPermanentDelete() },
            onDismiss = { viewModel.dismissPermanentDeleteDialog() }
        )
    }

    // Create / Edit Custom Rule BottomSheet
    if (uiState.showAddCustomRuleSheet) {
        AddCustomRuleSheet(
            existingRule = uiState.editingRule,
            existingMessages = uiState.allMessages,
            availableContacts = uiState.availableContacts,
            hasContactsPermission = uiState.hasContactsPermission,
            onRequestContactsPermission = requestContactsPermissionAction,
            onSaveRule = { rule -> viewModel.saveCustomRule(rule) },
            onDismiss = { viewModel.closeAddRuleSheet() }
        )
    }

    // Main Detection Per-Contact Filter Picker Dialog
    if (uiState.showMainContactPickerDialog) {
        val initiallySelectedTokens = remember(uiState.selectedContacts) {
            buildSet {
                uiState.selectedContacts.forEach { contact ->
                    add(contact.id.trim().lowercase())
                    add(contact.displayName.trim().lowercase())
                    contact.phoneNumbers.forEach { addAll(PhoneNormalizer.extractMatchKeys(it)) }
                }
            }
        }
        ContactPickerDialog(
            availableContacts = uiState.availableContacts,
            initiallySelectedTokens = initiallySelectedTokens,
            hasContactsPermission = uiState.hasContactsPermission,
            onRequestContactsPermission = requestContactsPermissionAction,
            onConfirmSelection = { chosen ->
                viewModel.setSelectedContacts(chosen)
                viewModel.closeMainContactPicker()
            },
            onDismiss = { viewModel.closeMainContactPicker() }
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

    // SMS Permissions Educational Rationale Dialog
    if (uiState.showPermissionRationaleDialog) {
        com.example.ui.components.SmsPermissionRationaleDialog(
            hasReadPermission = uiState.hasReadSmsPermission,
            hasWritePermission = uiState.hasWriteSmsPermission,
            onRequestPermissions = {
                viewModel.dismissPermissionRationaleDialog()
                requestSmsPermissionsAction()
            },
            onContinueInDemo = {
                viewModel.dismissPermissionRationaleDialog()
                viewModel.setDemoMode(true)
            },
            onDismiss = { viewModel.dismissPermissionRationaleDialog() }
        )
    }

    // SMS Permissions Settings Redirect Dialog (Permanently Denied)
    if (uiState.showPermissionPermanentlyDeniedDialog) {
        com.example.ui.components.SmsPermissionSettingsDialog(
            onDismiss = { viewModel.dismissPermissionPermanentlyDeniedDialog() },
            onContinueInDemo = {
                viewModel.dismissPermissionPermanentlyDeniedDialog()
                viewModel.setDemoMode(true)
            }
        )
    }

    // GitHub Release Update Checker & Direct In-App Updater Dialog
    if (uiState.showUpdatesDialog) {
        AppUpdatesDialog(
            uiState = uiState,
            onCheckNow = { viewModel.checkForGitHubUpdates(userInitiated = true) },
            onTogglePrereleases = { viewModel.toggleIncludePrereleases(it) },
            onSaveRepoSlug = { viewModel.updateGitHubRepoSlug(it) },
            onDownloadAndInstall = { viewModel.downloadAndInstallUpdate() },
            onCancelDownload = { viewModel.cancelUpdateDownload() },
            onTriggerInstall = launchDownloadedApkInstallAction,
            onRequestInstallPermission = requestUnknownAppInstallPermissionAction,
            onNotify = { viewModel.showSnackbar(it) },
            onDismiss = { viewModel.dismissUpdatesDialog() }
        )
    }
    }
}

@Composable
private fun AdaptiveCleanSmsThemeIfUnmanaged(
    useDeviceMaterialTheme: Boolean,
    content: @Composable () -> Unit
) {
    if (LocalCleanSmsThemeManaged.current) {
        content()
    } else {
        CleanSMSTheme(
            useDeviceMaterialTheme = useDeviceMaterialTheme,
            themeManaged = true,
            content = content
        )
    }
}

@Composable
fun BoxScope.SmsScanProgressOverlay(isScanning: Boolean) {
    AnimatedVisibility(
        visible = isScanning,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .semantics {
                    contentDescription = "Scanning SMS, MMS, and RCS inbox"
                }
                .testTag("sms_scan_progress_bar"),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun AppScreenContent(
    currentTab: NavigationTab,
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestSmsPermission: () -> Unit,
    onRequestSendSmsPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    onHandBackDefaultSmsRole: () -> Unit,
    onPickMedia: () -> Unit,
    onNavigateToPreview: () -> Unit
) {
    when (currentTab) {
        NavigationTab.MESSAGES -> {
            MessagesScreen(
                uiState = uiState,
                viewModel = viewModel,
                onPickMedia = onPickMedia,
                onRequestSendSmsPermission = onRequestSendSmsPermission,
                onRequestDefaultSmsRole = onRequestDefaultSmsRole
            )
        }

        NavigationTab.SCANNER -> {
            ScannerScreen(
                uiState = uiState,
                viewModel = viewModel,
                onRequestSmsPermission = onRequestSmsPermission,
                onRequestContactsPermission = onRequestContactsPermission,
                onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                onHandBackDefaultSmsRole = onHandBackDefaultSmsRole,
                onNavigateToPreview = onNavigateToPreview
            )
        }

        NavigationTab.PREVIEW -> {
            PreviewCleanScreen(
                uiState = uiState,
                viewModel = viewModel,
                onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                onHandBackDefaultSmsRole = onHandBackDefaultSmsRole
            )
        }

        NavigationTab.VAULT -> {
            VaultRestoreScreen(
                uiState = uiState,
                viewModel = viewModel,
                onRequestContactsPermission = onRequestContactsPermission
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
