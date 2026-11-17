package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ComposeAttachment
import com.example.model.ContactInfo
import com.example.model.ConversationThreadSummary
import com.example.model.MessageFolderFilter
import com.example.model.MessageProtocol
import com.example.model.MessagingHelper
import com.example.model.MessagingSendProtocol
import com.example.model.SmsMessageItem
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagesScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onPickMedia: () -> Unit,
    onRequestSendSmsPermission: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Handles Back inside Messages tab: Conversation Thread -> Search Query -> Protocol Filter -> Folder -> Tab/Exit
    BackHandler(
        enabled = !uiState.hasOpenOverlay &&
            (uiState.activeConversationKey != null ||
                uiState.messagesSearchQuery.isNotEmpty() ||
                uiState.messagesProtocolFilter != null ||
                uiState.messagesFolderFilter != MessageFolderFilter.INBOX)
    ) {
        viewModel.handleBackPress()
    }

    val activeThread = remember(uiState.conversationThreads, uiState.activeConversationKey) {
        uiState.conversationThreads.firstOrNull { it.threadKey == uiState.activeConversationKey }
    }
    val hasActiveConversation = activeThread != null || !uiState.pendingNewRecipientAddress.isNullOrBlank()
    val compactMode = uiState.compactMessagesUi
    var actionMenuThread by remember { mutableStateOf<ConversationThreadSummary?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                ConversationListPane(
                    uiState = uiState,
                    compactMode = compactMode,
                    onSearchChange = { viewModel.setMessagesSearchQuery(it) },
                    onFolderFilterSelect = { viewModel.setMessagesFolderFilter(it) },
                    onProtocolFilterSelect = { viewModel.setMessagesProtocolFilter(it) },
                    onToggleCompactUi = { viewModel.setCompactMessagesUi(it) },
                    onOpenSettings = { viewModel.openMessagesSettingsDialog() },
                    onThreadClick = { viewModel.openConversation(it.threadKey) },
                    onOpenThreadActions = { actionMenuThread = it },
                    onNewMessageClick = { viewModel.openNewMessageDialog() },
                    onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                    onRequestSendSmsPermission = onRequestSendSmsPermission,
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight()
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Box(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight()
                ) {
                    if (hasActiveConversation) {
                        ConversationDetailPane(
                            thread = activeThread,
                            pendingAddress = uiState.pendingNewRecipientAddress,
                            pendingName = uiState.pendingNewRecipientName,
                            uiState = uiState,
                            compactMode = compactMode,
                            viewModel = viewModel,
                            onBack = { viewModel.closeConversation() },
                            onPickMedia = onPickMedia,
                            onRequestSendSmsPermission = onRequestSendSmsPermission
                        )
                    } else {
                        EmptyConversationDetailPlaceholder(
                            onNewMessageClick = { viewModel.openNewMessageDialog() }
                        )
                    }
                }
            }
        } else {
            if (hasActiveConversation) {
                ConversationDetailPane(
                    thread = activeThread,
                    pendingAddress = uiState.pendingNewRecipientAddress,
                    pendingName = uiState.pendingNewRecipientName,
                    uiState = uiState,
                    compactMode = compactMode,
                    viewModel = viewModel,
                    onBack = { viewModel.closeConversation() },
                    onPickMedia = onPickMedia,
                    onRequestSendSmsPermission = onRequestSendSmsPermission
                )
            } else {
                ConversationListPane(
                    uiState = uiState,
                    compactMode = compactMode,
                    onSearchChange = { viewModel.setMessagesSearchQuery(it) },
                    onFolderFilterSelect = { viewModel.setMessagesFolderFilter(it) },
                    onProtocolFilterSelect = { viewModel.setMessagesProtocolFilter(it) },
                    onToggleCompactUi = { viewModel.setCompactMessagesUi(it) },
                    onOpenSettings = { viewModel.openMessagesSettingsDialog() },
                    onThreadClick = { viewModel.openConversation(it.threadKey) },
                    onOpenThreadActions = { actionMenuThread = it },
                    onNewMessageClick = { viewModel.openNewMessageDialog() },
                    onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                    onRequestSendSmsPermission = onRequestSendSmsPermission,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (uiState.showNewMessageDialog) {
        NewMessageRecipientDialog(
            recipientInput = uiState.newMessageRecipientInput,
            contacts = uiState.availableContacts,
            onRecipientChange = { viewModel.setNewMessageRecipientInput(it) },
            onSelectContact = { contact ->
                viewModel.startConversationWithRecipient(
                    recipient = contact.primaryNumber,
                    displayName = contact.displayName
                )
            },
            onStartConversation = {
                viewModel.startConversationWithRecipient(uiState.newMessageRecipientInput)
            },
            onDismiss = { viewModel.dismissNewMessageDialog() }
        )
    }

    if (uiState.showMessagesSettingsDialog) {
        MessagesSettingsDialog(
            compactMessagesUi = uiState.compactMessagesUi,
            onCompactChange = { viewModel.setCompactMessagesUi(it) },
            useDeviceMaterialTheme = uiState.useDeviceMaterialTheme,
            onUseDeviceMaterialThemeChange = { viewModel.setUseDeviceMaterialTheme(it) },
            onDismiss = { viewModel.dismissMessagesSettingsDialog() }
        )
    }

    val pendingActionThread = actionMenuThread
    if (pendingActionThread != null) {
        ConversationActionsDialog(
            thread = pendingActionThread,
            onArchiveToggle = {
                actionMenuThread = null
                viewModel.setThreadArchived(pendingActionThread, !pendingActionThread.isArchived)
            },
            onSpamToggle = {
                actionMenuThread = null
                viewModel.setThreadSpam(pendingActionThread, !pendingActionThread.isSpam)
            },
            onDismiss = { actionMenuThread = null }
        )
    }
}

@Composable
private fun ConversationListPane(
    uiState: CleanSmsUiState,
    compactMode: Boolean,
    onSearchChange: (String) -> Unit,
    onFolderFilterSelect: (MessageFolderFilter) -> Unit,
    onProtocolFilterSelect: (MessageProtocol?) -> Unit,
    onToggleCompactUi: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onThreadClick: (ConversationThreadSummary) -> Unit,
    onOpenThreadActions: (ConversationThreadSummary) -> Unit,
    onNewMessageClick: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    onRequestSendSmsPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outerHorizontal = if (compactMode) 12.dp else 16.dp
    val sectionGap = if (compactMode) 5.dp else 9.dp
    val listSpacing = if (compactMode) 5.dp else 8.dp
    val bottomPadding = if (compactMode) 76.dp else 88.dp

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (compactMode) {
                FloatingActionButton(
                    onClick = onNewMessageClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("new_message_fab")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Compose new message")
                }
            } else {
                ExtendedFloatingActionButton(
                    onClick = onNewMessageClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Compose new message") },
                    text = { Text("New Message", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("new_message_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = outerHorizontal)
        ) {
            Spacer(modifier = Modifier.height(if (compactMode) 4.dp else 8.dp))

            // Top Search Bar paired with direct 1-tap Compact UI Toggle + Display Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = uiState.messagesSearchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            text = if (compactMode) {
                                "Search sender or text..."
                            } else {
                                "Search SMS, MMS, RCS by sender or text..."
                            },
                            fontSize = if (compactMode) 13.sp else 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = if (compactMode) 13.sp else 14.sp
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search conversations",
                            modifier = Modifier.size(if (compactMode) 18.dp else 20.dp)
                        )
                    },
                    trailingIcon = {
                        if (uiState.messagesSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchChange("") },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("messages_search_clear_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(if (compactMode) 12.dp else 14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("messages_search_input")
                )

                // Direct 1-tap Compact UI Toggle synced with Settings
                Surface(
                    shape = RoundedCornerShape(if (compactMode) 12.dp else 14.dp),
                    color = if (compactMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = if (compactMode) 1.3.dp else 1.dp,
                        color = if (compactMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                    ),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .toggleable(
                            value = compactMode,
                            role = Role.Switch,
                            onValueChange = onToggleCompactUi
                        )
                        .testTag("messages_compact_toggle_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (compactMode) AppIcons.Compress else AppIcons.ViewAgenda,
                            contentDescription = if (compactMode) "Compact UI On" else "Compact UI Off",
                            tint = if (compactMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Compact",
                            fontSize = 11.sp,
                            fontWeight = if (compactMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (compactMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(if (compactMode) 12.dp else 14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("messages_settings_btn")
                    ) {
                        Icon(
                            imageVector = AppIcons.Tune,
                            contentDescription = "Messaging display settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Cross-App Folder Switcher (Inbox / Archived / Spam)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FolderFilterChipItem(
                    label = "Inbox",
                    icon = AppIcons.Inbox,
                    selected = uiState.messagesFolderFilter == MessageFolderFilter.INBOX,
                    accentColor = MaterialTheme.colorScheme.primary,
                    compactMode = compactMode,
                    onClick = { onFolderFilterSelect(MessageFolderFilter.INBOX) },
                    testTag = "messages_folder_inbox"
                )
                val archivedLabel = if (uiState.archivedThreadsCount > 0) {
                    "Archived (${uiState.archivedThreadsCount})"
                } else {
                    "Archived"
                }
                FolderFilterChipItem(
                    label = archivedLabel,
                    icon = AppIcons.Archive,
                    selected = uiState.messagesFolderFilter == MessageFolderFilter.ARCHIVED,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    compactMode = compactMode,
                    onClick = { onFolderFilterSelect(MessageFolderFilter.ARCHIVED) },
                    testTag = "messages_folder_archived"
                )
                val spamLabel = if (uiState.spamThreadsCount > 0) {
                    "Spam (${uiState.spamThreadsCount})"
                } else {
                    "Spam"
                }
                FolderFilterChipItem(
                    label = spamLabel,
                    icon = AppIcons.Report,
                    selected = uiState.messagesFolderFilter == MessageFolderFilter.SPAM,
                    accentColor = Color(0xFFFF6B6B),
                    compactMode = compactMode,
                    onClick = { onFolderFilterSelect(MessageFolderFilter.SPAM) },
                    testTag = "messages_folder_spam"
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Protocol filter pills (All / SMS / MMS / RCS)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProtocolFilterChipItem(
                    label = "All",
                    selected = uiState.messagesProtocolFilter == null,
                    accentColor = MaterialTheme.colorScheme.primary,
                    compactMode = compactMode,
                    onClick = { onProtocolFilterSelect(null) },
                    testTag = "messages_filter_all"
                )
                ProtocolFilterChipItem(
                    label = "SMS",
                    selected = uiState.messagesProtocolFilter == MessageProtocol.SMS,
                    accentColor = Color(MessageProtocol.SMS.badgeColorHex),
                    compactMode = compactMode,
                    onClick = {
                        onProtocolFilterSelect(
                            if (uiState.messagesProtocolFilter == MessageProtocol.SMS) null else MessageProtocol.SMS
                        )
                    },
                    testTag = "messages_filter_sms"
                )
                ProtocolFilterChipItem(
                    label = "MMS",
                    selected = uiState.messagesProtocolFilter == MessageProtocol.MMS,
                    accentColor = Color(MessageProtocol.MMS.badgeColorHex),
                    compactMode = compactMode,
                    onClick = {
                        onProtocolFilterSelect(
                            if (uiState.messagesProtocolFilter == MessageProtocol.MMS) null else MessageProtocol.MMS
                        )
                    },
                    testTag = "messages_filter_mms"
                )
                ProtocolFilterChipItem(
                    label = "RCS",
                    selected = uiState.messagesProtocolFilter == MessageProtocol.RCS_CHAT,
                    accentColor = Color(MessageProtocol.RCS_CHAT.badgeColorHex),
                    compactMode = compactMode,
                    onClick = {
                        onProtocolFilterSelect(
                            if (uiState.messagesProtocolFilter == MessageProtocol.RCS_CHAT) null else MessageProtocol.RCS_CHAT
                        )
                    },
                    testTag = "messages_filter_rcs"
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            val pinStatusAtTop = !compactMode || (!uiState.isDemoMode && !uiState.hasSendSmsPermission)
            if (pinStatusAtTop) {
                MessagingStatusStrip(
                    isDemoMode = uiState.isDemoMode,
                    isDefaultSmsApp = uiState.isDefaultSmsApp,
                    hasSendSmsPermission = uiState.hasSendSmsPermission,
                    unreadCount = uiState.totalUnreadMessagesCount,
                    compactMode = compactMode,
                    onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                    onRequestSendSmsPermission = onRequestSendSmsPermission
                )

                Spacer(modifier = Modifier.height(sectionGap))
            }

            if (uiState.conversationThreads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (compactMode) 16.dp else 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = when (uiState.messagesFolderFilter) {
                                MessageFolderFilter.ARCHIVED -> AppIcons.Archive
                                MessageFolderFilter.SPAM -> AppIcons.VerifiedUser
                                MessageFolderFilter.INBOX -> AppIcons.Forum
                            },
                            contentDescription = "No messages",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        val emptyText = when {
                            uiState.messagesSearchQuery.isNotBlank() && uiState.messagesProtocolFilter != null ->
                                "No ${uiState.messagesProtocolFilter.displayName} conversations matching \"${uiState.messagesSearchQuery}\""
                            uiState.messagesSearchQuery.isNotBlank() ->
                                "No conversations matching \"${uiState.messagesSearchQuery}\""
                            uiState.messagesProtocolFilter != null ->
                                "No ${uiState.messagesProtocolFilter.displayName} conversations in ${uiState.messagesFolderFilter.label}"
                            uiState.messagesFolderFilter == MessageFolderFilter.ARCHIVED ->
                                "No archived conversations"
                            uiState.messagesFolderFilter == MessageFolderFilter.SPAM ->
                                "No spam or blocked conversations"
                            else -> "No conversations yet"
                        }
                        Text(
                            text = emptyText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (compactMode) 14.sp else 16.sp
                        )
                        if (uiState.messagesSearchQuery.isNotBlank() || uiState.messagesProtocolFilter != null) {
                            OutlinedButton(
                                onClick = {
                                    onSearchChange("")
                                    onProtocolFilterSelect(null)
                                },
                                modifier = Modifier.testTag("messages_clear_filters_btn")
                            ) {
                                Text("Clear Filters")
                            }
                        } else if (uiState.messagesFolderFilter != MessageFolderFilter.INBOX) {
                            OutlinedButton(
                                onClick = { onFolderFilterSelect(MessageFolderFilter.INBOX) },
                                modifier = Modifier.testTag("messages_back_to_inbox_btn")
                            ) {
                                Text("Back to Inbox")
                            }
                        } else {
                            Button(
                                onClick = onNewMessageClick,
                                modifier = Modifier.testTag("empty_start_chat_btn")
                            ) {
                                Text("Start a Conversation")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("messages_inbox_list"),
                    verticalArrangement = Arrangement.spacedBy(listSpacing),
                    contentPadding = PaddingValues(bottom = bottomPadding)
                ) {
                    items(
                        items = uiState.conversationThreads,
                        key = { it.threadKey }
                    ) { thread ->
                        val isSelected = uiState.activeConversationKey == thread.threadKey
                        ConversationThreadRow(
                            thread = thread,
                            isSelected = isSelected,
                            compactMode = compactMode,
                            onClick = { onThreadClick(thread) },
                            onOpenActions = { onOpenThreadActions(thread) }
                        )
                    }
                    if (!pinStatusAtTop) {
                        item(key = "messaging_status_footer") {
                            Spacer(modifier = Modifier.height(4.dp))
                            MessagingStatusStrip(
                                isDemoMode = uiState.isDemoMode,
                                isDefaultSmsApp = uiState.isDefaultSmsApp,
                                hasSendSmsPermission = uiState.hasSendSmsPermission,
                                unreadCount = uiState.totalUnreadMessagesCount,
                                compactMode = compactMode,
                                onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                                onRequestSendSmsPermission = onRequestSendSmsPermission
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderFilterChipItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accentColor: Color,
    compactMode: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(if (compactMode) 10.dp else 12.dp),
        color = if (selected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.3.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = if (compactMode) 36.dp else 40.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(
                horizontal = if (compactMode) 10.dp else 12.dp,
                vertical = if (compactMode) 6.dp else 7.dp
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compactMode) 14.dp else 16.dp)
            )
            Text(
                text = label,
                fontSize = if (compactMode) 11.sp else 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProtocolFilterChipItem(
    label: String,
    selected: Boolean,
    accentColor: Color,
    compactMode: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(if (compactMode) 8.dp else 10.dp),
        color = if (selected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.2.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = if (compactMode) 32.dp else 36.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(
                horizontal = if (compactMode) 10.dp else 12.dp,
                vertical = if (compactMode) 5.dp else 6.dp
            )
        ) {
            Text(
                text = label,
                fontSize = if (compactMode) 11.sp else 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessagingStatusStrip(
    isDemoMode: Boolean,
    isDefaultSmsApp: Boolean,
    hasSendSmsPermission: Boolean,
    unreadCount: Int,
    compactMode: Boolean,
    onRequestDefaultSmsRole: () -> Unit,
    onRequestSendSmsPermission: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(if (compactMode) 10.dp else 12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compactMode) 10.dp else 12.dp,
                    vertical = if (compactMode) 4.dp else 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isDefaultSmsApp) AppIcons.Verified else AppIcons.Sms,
                    contentDescription = "Messaging status",
                    tint = if (isDefaultSmsApp) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compactMode) 15.dp else 18.dp)
                )
                val statusTitle = when {
                    isDemoMode -> if (compactMode) "Demo Messaging • SMS/MMS/RCS" else "Demo Messaging Active • SMS / MMS / RCS"
                    isDefaultSmsApp -> "Default Messaging App Active"
                    else -> "Standard SMS Reader"
                }
                if (compactMode) {
                    Text(
                        text = if (unreadCount > 0) "$statusTitle • $unreadCount unread" else statusTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Column {
                        Text(
                            text = statusTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (unreadCount > 0) {
                            Text(
                                text = "$unreadCount unread conversation${if (unreadCount == 1) "" else "s"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (!isDemoMode && !hasSendSmsPermission) {
                TextButton(
                    onClick = onRequestSendSmsPermission,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("grant_send_sms_btn")
                ) {
                    Text("Allow Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else if (!isDefaultSmsApp) {
                TextButton(
                    onClick = onRequestDefaultSmsRole,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("messages_set_default_sms_btn")
                ) {
                    Text("Set Default", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ConversationThreadRow(
    thread: ConversationThreadSummary,
    isSelected: Boolean,
    compactMode: Boolean,
    onClick: () -> Unit,
    onOpenActions: () -> Unit
) {
    val protocolColor = Color(thread.protocol.badgeColorHex)
    val isUnread = thread.unreadCount > 0
    val cardBorder = when {
        isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isUnread -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    }

    Surface(
        shape = RoundedCornerShape(if (compactMode) 11.dp else 14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("conversation_row_${thread.threadKey}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (compactMode) 10.dp else 12.dp,
                    end = 4.dp,
                    top = if (compactMode) 7.dp else 12.dp,
                    bottom = if (compactMode) 7.dp else 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 10.dp)
        ) {
            // Avatar with initials
            Surface(
                shape = CircleShape,
                color = protocolColor.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, protocolColor.copy(alpha = 0.5f)),
                modifier = Modifier.size(if (compactMode) 38.dp else 46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = avatarInitials(thread.displayName),
                        color = protocolColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compactMode) 13.sp else 15.sp
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = thread.displayName,
                        fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = if (compactMode) 14.sp else 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatShortTimestamp(thread.lastMessage.date),
                        fontSize = if (compactMode) 10.sp else 11.sp,
                        color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                    )
                }

                val prefix = if (thread.lastMessage.type == 2) "You: " else ""
                Text(
                    text = "$prefix${thread.lastMessage.body}",
                    fontSize = if (compactMode) 12.sp else 13.sp,
                    color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = if (compactMode) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 6.dp)
                ) {
                    Surface(
                        color = protocolColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(if (compactMode) 5.dp else 6.dp)
                    ) {
                        Text(
                            text = thread.protocol.displayName,
                            color = protocolColor,
                            fontSize = if (compactMode) 9.sp else 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                horizontal = if (compactMode) 5.dp else 6.dp,
                                vertical = if (compactMode) 1.dp else 2.dp
                            )
                        )
                    }
                    if (thread.hasAttachments) {
                        Icon(
                            imageVector = AppIcons.Image,
                            contentDescription = "Contains MMS attachment",
                            tint = Color(MessageProtocol.MMS.badgeColorHex),
                            modifier = Modifier.size(if (compactMode) 12.dp else 14.dp)
                        )
                    }
                    Text(
                        text = "${thread.messageCount} msg${if (thread.messageCount == 1) "" else "s"}",
                        fontSize = if (compactMode) 10.sp else 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (thread.isSpam || thread.isArchived) {
                        val badgeColor = if (thread.isSpam) Color(0xFFFF6B6B) else TealAccent
                        val badgeLabel = when {
                            thread.folderSource == MessagingHelper.FOLDER_SOURCE_BLOCKED_NUMBER -> "Blocked"
                            thread.folderSource == MessagingHelper.FOLDER_SOURCE_PROVIDER_SPAM -> "Provider Spam"
                            thread.folderSource == MessagingHelper.FOLDER_SOURCE_PROVIDER_ARCHIVED -> "Synced Archive"
                            thread.folderSource == MessagingHelper.FOLDER_SOURCE_SMART_SPAM -> "Smart Spam"
                            thread.isSpam -> "Spam"
                            else -> "Archived"
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(if (compactMode) 5.dp else 6.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                color = badgeColor,
                                fontSize = if (compactMode) 9.sp else 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (thread.hasCleanableClutter) {
                        Surface(
                            color = Color(0xFFFFB300).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(if (compactMode) 5.dp else 6.dp)
                        ) {
                            Text(
                                text = "Clutter",
                                color = Color(0xFFFFB300),
                                fontSize = if (compactMode) 9.sp else 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            if (isUnread) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("unread_badge_${thread.threadKey}")
                ) {
                    Text(
                        text = thread.unreadCount.toString(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onOpenActions,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("conversation_menu_${thread.threadKey}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Conversation actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ConversationActionsDialog(
    thread: ConversationThreadSummary,
    onArchiveToggle: () -> Unit,
    onSpamToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("conversation_actions_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = thread.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${thread.protocol.displayName} • ${thread.messageCount} message${if (thread.messageCount == 1) "" else "s"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onArchiveToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag(
                            if (thread.isArchived) {
                                "action_unarchive_${thread.threadKey}"
                            } else {
                                "action_archive_${thread.threadKey}"
                            }
                        )
                ) {
                    Icon(
                        imageVector = if (thread.isArchived) AppIcons.Unarchive else AppIcons.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (thread.isArchived) "Unarchive" else "Archive")
                }
                OutlinedButton(
                    onClick = onSpamToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag(
                            if (thread.isSpam) {
                                "action_not_spam_${thread.threadKey}"
                            } else {
                                "action_mark_spam_${thread.threadKey}"
                            }
                        )
                ) {
                    Icon(
                        imageVector = if (thread.isSpam) AppIcons.VerifiedUser else AppIcons.Report,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (thread.isSpam) "Not Spam (Move to Inbox)" else "Mark as Spam")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationDetailPane(
    thread: ConversationThreadSummary?,
    pendingAddress: String?,
    pendingName: String?,
    uiState: CleanSmsUiState,
    compactMode: Boolean,
    viewModel: CleanSmsViewModel,
    onBack: () -> Unit,
    onPickMedia: () -> Unit,
    onRequestSendSmsPermission: () -> Unit
) {
    val displayTitle = thread?.displayName ?: pendingName?.takeIf { it.isNotBlank() } ?: pendingAddress.orEmpty()
    val subtitleAddress = thread?.address ?: pendingAddress.orEmpty()
    val messages = thread?.messages.orEmpty()
    val threadProtocol = thread?.protocol ?: MessageProtocol.SMS
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, thread?.threadKey) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("conversation_thread_screen")
    ) {
        // Thread Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compactMode) 6.dp else 8.dp,
                        vertical = if (compactMode) 4.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("conversation_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to conversations"
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compactMode) 15.sp else 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("conversation_header_title")
                    )
                    if (subtitleAddress.isNotBlank() && subtitleAddress != displayTitle) {
                        Text(
                            text = subtitleAddress,
                            fontSize = if (compactMode) 11.sp else 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                val protoColor = Color(threadProtocol.badgeColorHex)
                Surface(
                    color = protoColor.copy(alpha = 0.16f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, protoColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = threadProtocol.displayName,
                        color = protoColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (thread != null) {
                    IconButton(
                        onClick = { viewModel.setThreadArchived(thread, !thread.isArchived) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("conversation_archive_button")
                    ) {
                        Icon(
                            imageVector = if (thread.isArchived) AppIcons.Unarchive else AppIcons.Archive,
                            contentDescription = if (thread.isArchived) "Unarchive conversation" else "Archive conversation",
                            tint = if (thread.isArchived) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setThreadSpam(thread, !thread.isSpam) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("conversation_spam_button")
                    ) {
                        Icon(
                            imageVector = if (thread.isSpam) AppIcons.VerifiedUser else AppIcons.Report,
                            contentDescription = if (thread.isSpam) "Mark as not spam" else "Mark conversation as spam",
                            tint = if (thread.isSpam) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Message Stream
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "New conversation with $displayTitle.\nSend an SMS, MMS photo, or RCS chat below.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (compactMode) 10.dp else 12.dp)
                    .testTag("conversation_messages_list"),
                contentPadding = PaddingValues(vertical = if (compactMode) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 8.dp)
            ) {
                items(items = messages, key = { it.id }) { msg ->
                    MessageChatBubble(
                        message = msg,
                        compactMode = compactMode,
                        onInspect = { viewModel.showMessageDetails(msg) }
                    )
                }
            }
        }

        // Permission reminder banner on real devices missing SEND_SMS
        if (!uiState.isDemoMode && !uiState.hasSendSmsPermission) {
            Surface(
                color = Color(0xFFFFB300).copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SEND_SMS permission needed to send carrier texts",
                        fontSize = 12.sp,
                        color = Color(0xFFFFB300),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRequestSendSmsPermission) {
                        Text("Grant", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compactMode) 8.dp else 10.dp,
                        vertical = if (compactMode) 5.dp else 8.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp)
            ) {
                // Protocol mode selector row (Auto / SMS / MMS / RCS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MessagingSendProtocol.entries.forEach { proto ->
                        val selected = uiState.composerProtocol == proto
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setComposerProtocol(proto) },
                            label = { Text(proto.label, fontSize = 11.sp) },
                            modifier = Modifier.testTag("composer_protocol_${proto.name.lowercase()}")
                        )
                    }
                }

                // Active attachment preview chip
                uiState.composerAttachment?.let { att ->
                    ComposerAttachmentPreview(
                        attachment = att,
                        onRemove = { viewModel.setComposerAttachment(null) }
                    )
                }

                // Input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onPickMedia,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("attach_media_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.AddPhotoAlternate,
                            contentDescription = "Attach image for MMS",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = uiState.composerBody,
                        onValueChange = { viewModel.setComposerBody(it) },
                        placeholder = {
                            val hint = when {
                                uiState.composerAttachment != null -> "Add MMS caption..."
                                uiState.composerProtocol == MessagingSendProtocol.MMS -> "MMS message..."
                                uiState.composerProtocol == MessagingSendProtocol.RCS -> "RCS chat message..."
                                else -> "Text message..."
                            }
                            Text(hint)
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_composer_input")
                    )

                    val canSend = !uiState.isSendingMessage &&
                        (uiState.composerBody.isNotBlank() || uiState.composerAttachment != null)
                    FilledIconButton(
                        onClick = { viewModel.sendCurrentMessage() },
                        enabled = canSend,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        if (uiState.isSendingMessage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageChatBubble(
    message: SmsMessageItem,
    compactMode: Boolean,
    onInspect: () -> Unit
) {
    val isOutgoing = message.type == 2
    val protoColor = Color(message.protocol.badgeColorHex)
    val bubbleShape = RoundedCornerShape(
        topStart = if (compactMode) 14.dp else 16.dp,
        topEnd = if (compactMode) 14.dp else 16.dp,
        bottomStart = if (isOutgoing) (if (compactMode) 14.dp else 16.dp) else 4.dp,
        bottomEnd = if (isOutgoing) 4.dp else (if (compactMode) 14.dp else 16.dp)
    )
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            border = if (isOutgoing) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clickable(onClick = onInspect)
                .testTag("message_bubble_${message.id}")
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (compactMode) 10.dp else 12.dp,
                    vertical = if (compactMode) 6.dp else 9.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)
            ) {
                if (!message.mmsSubject.isNullOrBlank()) {
                    Text(
                        text = "Subject: ${message.mmsSubject}",
                        fontSize = if (compactMode) 11.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = protoColor
                    )
                }

                // Attachment visual card / image thumbnail when present
                if (!message.attachmentUri.isNullOrBlank() || !message.attachmentLabel.isNullOrBlank()) {
                    MessageAttachmentCard(
                        attachmentUri = message.attachmentUri,
                        mimeType = message.attachmentMimeType,
                        label = message.attachmentLabel ?: "MMS Attachment"
                    )
                }

                SelectionContainer {
                    Text(
                        text = message.body,
                        fontSize = if (compactMode) 13.sp else 14.sp,
                        color = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatBubbleTimestamp(message.date),
                        fontSize = if (compactMode) 9.sp else 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• ${message.protocol.displayName}",
                        fontSize = if (compactMode) 9.sp else 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = protoColor
                    )
                    if (isOutgoing) {
                        val statusLabel = message.rcsDeliveryStatus?.displayName ?: "Sent"
                        Text(
                            text = "• $statusLabel",
                            fontSize = if (compactMode) 9.sp else 10.sp,
                            color = TealAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageAttachmentCard(
    attachmentUri: String?,
    mimeType: String?,
    label: String
) {
    val context = LocalContext.current
    var decodedBitmap by remember(attachmentUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(attachmentUri, mimeType) {
        if (!attachmentUri.isNullOrBlank() && (mimeType == null || mimeType.startsWith("image/"))) {
            decodedBitmap = withContext(Dispatchers.IO) {
                decodeSampledImageBitmap(context, Uri.parse(attachmentUri))
            }
        }
    }

    if (decodedBitmap != null) {
        Image(
            bitmap = decodedBitmap!!,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Surface(
            color = Color(MessageProtocol.MMS.badgeColorHex).copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Image,
                    contentDescription = label,
                    tint = Color(MessageProtocol.MMS.badgeColorHex),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ComposerAttachmentPreview(
    attachment: ComposeAttachment,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(MessageProtocol.MMS.badgeColorHex).copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color(MessageProtocol.MMS.badgeColorHex).copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("composer_attachment_preview")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = AppIcons.AttachFile,
                    contentDescription = "MMS attachment",
                    tint = Color(MessageProtocol.MMS.badgeColorHex),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "MMS: ${attachment.displayName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("remove_attachment_btn")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove attachment")
            }
        }
    }
}

@Composable
private fun EmptyConversationDetailPlaceholder(
    onNewMessageClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.Forum,
                        contentDescription = "Select conversation",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = "Select a conversation to read & reply",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Text(
                text = "Send standard SMS, attach media via MMS, or message existing RCS contacts directly.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onNewMessageClick) {
                Text("Start New Conversation")
            }
        }
    }
}

@Composable
private fun NewMessageRecipientDialog(
    recipientInput: String,
    contacts: List<ContactInfo>,
    onRecipientChange: (String) -> Unit,
    onSelectContact: (ContactInfo) -> Unit,
    onStartConversation: () -> Unit,
    onDismiss: () -> Unit
) {
    val filteredContacts = remember(contacts, recipientInput) {
        val q = recipientInput.trim().lowercase()
        if (q.isEmpty()) contacts else contacts.filter {
            it.displayName.lowercase().contains(q) ||
                it.phoneNumbers.any { p -> p.lowercase().contains(q) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .testTag("new_message_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "New Message",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = recipientInput,
                    onValueChange = onRecipientChange,
                    label = { Text("Phone number or contact name") },
                    placeholder = { Text("+1 (555) 123-4567") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_message_recipient_input")
                )

                if (filteredContacts.isNotEmpty()) {
                    Text(
                        text = "Quick Select Contact",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredContacts, key = { it.id }) { contact ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectContact(contact) }
                                    .testTag("new_message_contact_${contact.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = contact.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = contact.primaryNumber,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("new_message_cancel_btn")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onStartConversation,
                        enabled = recipientInput.isNotBlank(),
                        modifier = Modifier.testTag("new_message_start_btn")
                    ) {
                        Text("Start Chat")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesSettingsDialog(
    compactMessagesUi: Boolean,
    onCompactChange: (Boolean) -> Unit,
    useDeviceMaterialTheme: Boolean,
    onUseDeviceMaterialThemeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("messages_settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Messages Display Settings",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Adjust layout density and theme appearance for SMS, MMS, and RCS threads. Synced with Rules & Cloud Settings.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use Device Material UI",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (useDeviceMaterialTheme) "Following device Dark/Light & Material You palette" else "Using Classic CleanSMS Navy & Cyan theme",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useDeviceMaterialTheme,
                            onCheckedChange = onUseDeviceMaterialThemeChange,
                            modifier = Modifier.testTag("messages_dialog_device_material_theme_switch")
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Compact Messages UI",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (compactMessagesUi) "More threads visible at once" else "Roomy cards with extra preview lines",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = compactMessagesUi,
                            onCheckedChange = onCompactChange,
                            modifier = Modifier.testTag("messages_density_switch")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (compactMessagesUi) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.background,
                        border = BorderStroke(
                            width = if (compactMessagesUi) 1.5.dp else 1.dp,
                            color = if (compactMessagesUi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCompactChange(true) }
                            .testTag("compact_mode_option_compact")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Compact",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (compactMessagesUi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Slimmer rows & 1-line previews",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!compactMessagesUi) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.background,
                        border = BorderStroke(
                            width = if (!compactMessagesUi) 1.5.dp else 1.dp,
                            color = if (!compactMessagesUi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCompactChange(false) }
                            .testTag("compact_mode_option_comfortable")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Comfortable",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (!compactMessagesUi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Larger avatars & 2-line previews",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("messages_settings_close_btn")
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun decodeSampledImageBitmap(context: android.content.Context, uri: Uri): ImageBitmap? {
    return try {
        val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsBounds) }
        val maxDim = maxOf(optsBounds.outWidth, optsBounds.outHeight)
        var sample = 1
        while (maxDim / sample > 640) {
            sample *= 2
        }
        val optsDecode = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, optsDecode)
        }
        bmp?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private fun avatarInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
        parts.isNotEmpty() -> parts[0].take(1).uppercase()
        else -> "#"
    }
}

private fun formatShortTimestamp(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    val oneMin = 60_000L
    val oneHour = 3_600_000L
    val oneDay = 86_400_000L
    return when {
        diffMs < oneMin -> "Now"
        diffMs < oneHour -> "${diffMs / oneMin}m"
        diffMs < oneDay -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
    }
}

private fun formatBubbleTimestamp(epochMs: Long): String {
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(epochMs))
}
