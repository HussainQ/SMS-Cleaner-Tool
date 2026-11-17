package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import com.example.model.DuplicateKeepStrategy
import com.example.model.MessageProtocol
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.components.MessageCard
import com.example.ui.components.ThreadDuplicateClusterCard
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PreviewCleanScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    onRequestDefaultSmsRole: () -> Unit = {},
    onHandBackDefaultSmsRole: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchInput by remember { mutableStateOf(uiState.searchQuery) }

    // Keep local search input aligned when ViewModel resets searchQuery to empty (e.g., via Back)
    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery.isEmpty() && searchInput.isNotEmpty()) {
            searchInput = ""
        }
    }

    // Debounce search query updates to avoid per-keystroke stalls on large inboxes
    LaunchedEffect(searchInput) {
        if (searchInput.isEmpty()) {
            viewModel.setSearchQuery("")
        } else {
            delay(150)
            viewModel.setSearchQuery(searchInput)
        }
    }

    // Sub-level BackHandlers: return category filter to ALL, or clear active search FIRST
    val hasActiveSearch = searchInput.isNotEmpty() || uiState.searchQuery.isNotEmpty()
    BackHandler(enabled = !uiState.hasOpenOverlay && !hasActiveSearch && uiState.selectedCategoryTab != SmsCategory.ALL) {
        viewModel.setCategoryTab(SmsCategory.ALL)
    }
    BackHandler(enabled = !uiState.hasOpenOverlay && hasActiveSearch) {
        searchInput = ""
        viewModel.setSearchQuery("")
    }

    // Quick suggestion keywords for search
    val quickSearchSuggestions = remember {
        listOf("RCS", "Amazon", "Uber", "OTP", "Dominos", "Sale", "Verify", "Bank")
    }

    // Filter candidates on Dispatchers.Default so scrolling and typing stay 60fps
    val cleanableMessages by produceState<List<SmsMessageItem>>(
        initialValue = emptyList(),
        uiState.classifiedMessages,
        uiState.selectedCategoryTab,
        uiState.searchQuery
    ) {
        value = withContext(Dispatchers.Default) {
            val query = uiState.searchQuery.trim().lowercase()
            val selectedTab = uiState.selectedCategoryTab
            uiState.classifiedMessages.filter { msg ->
                if (!msg.isCleanCandidate) return@filter false
                val matchesCategory = selectedTab == SmsCategory.ALL || msg.category == selectedTab
                if (!matchesCategory) return@filter false
                if (query.isEmpty()) {
                    true
                } else {
                    msg.displaySender.lowercase().contains(query) ||
                        msg.address.lowercase().contains(query) ||
                        msg.body.lowercase().contains(query) ||
                        (query == "rcs" && (msg.isRcsChat || msg.protocol == MessageProtocol.RCS_CHAT)) ||
                        (msg.otpCode != null && msg.otpCode.contains(query)) ||
                        msg.matchReason.lowercase().contains(query)
                }
            }
        }
    }

    val selectedCount = remember(cleanableMessages) { cleanableMessages.count { it.isSelected } }
    val allSelected = remember(cleanableMessages, selectedCount) {
        cleanableMessages.isNotEmpty() && selectedCount == cleanableMessages.size
    }
    val matchingIds = remember(cleanableMessages) { cleanableMessages.mapTo(HashSet()) { it.id } }

    val isDuplicateTab = uiState.selectedCategoryTab == SmsCategory.DUPLICATE
    val threadClusters = uiState.duplicateThreadClusters

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search & Category Header Area
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Default SMS status indicator or inline request prompt in Device Mode
                if (uiState.isDefaultSmsApp) {
                    Surface(
                        color = TealAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preview_default_sms_status_indicator")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    AppIcons.Verified,
                                    contentDescription = "Default SMS App",
                                    tint = TealAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Default SMS App • From ${uiState.previousDefaultSmsAppName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TealAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = onHandBackDefaultSmsRole,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.testTag("preview_hand_back_sms_btn")
                            ) {
                                Text("Hand Back", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealAccent)
                            }
                        }
                    }
                } else if (!uiState.isDemoMode) {
                    Surface(
                        color = AmberAccent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.40f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preview_default_sms_needed_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    AppIcons.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = AmberAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Set as Default SMS App to delete permanently",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = onRequestDefaultSmsRole,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.testTag("preview_set_default_sms_btn")
                            ) {
                                Text("Set Default", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                            }
                        }
                    }
                }

                // Interactive Search Bar
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    placeholder = {
                        Text(
                            text = "Search contact, content, sender, or OTP...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (searchInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            if (searchInput.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = "${cleanableMessages.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        searchInput = ""
                                        viewModel.setSearchQuery("")
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preview_search_input")
                )

                // Quick Search Keyword Suggestions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    quickSearchSuggestions.forEach { tag ->
                        val isCurrentSearch = searchInput.equals(tag, ignoreCase = true)
                        SuggestionChip(
                            onClick = {
                                if (isCurrentSearch) {
                                    searchInput = ""
                                    viewModel.setSearchQuery("")
                                } else {
                                    searchInput = tag
                                    viewModel.setSearchQuery(tag)
                                }
                            },
                            label = { Text(tag, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isCurrentSearch) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                labelColor = if (isCurrentSearch) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (isCurrentSearch) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("quick_search_$tag")
                        )
                    }
                }

                // Category Filter Pills (O(1) precomputed summary counts)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf(
                        SmsCategory.ALL,
                        SmsCategory.DUPLICATE,
                        SmsCategory.OTP,
                        SmsCategory.PROMOTION,
                        SmsCategory.SPAM,
                        SmsCategory.CUSTOM_RULE
                    )

                    categories.forEach { cat ->
                        val isSelected = uiState.selectedCategoryTab == cat
                        val count = uiState.summary.countFor(cat)

                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setCategoryTab(cat) },
                            label = {
                                Text("${cat.displayName} ($count)")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("chip_${cat.name.lowercase()}")
                        )
                    }
                }

                // Duplicates View Toolbar (Threads view + Keep Strategy)
                if (isDuplicateTab && threadClusters.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    Icon(
                                        AppIcons.Forum,
                                        contentDescription = null,
                                        tint = AmberAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Fix Existing Threads (${threadClusters.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Preserves master message in thread; removes redundant copies",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.fixAllDuplicateThreads() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("auto_fix_all_threads_button")
                                ) {
                                    Text(
                                        text = "Auto-Fix All",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Keep:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    SuggestionChip(
                                        onClick = { viewModel.setDuplicateKeepStrategy(DuplicateKeepStrategy.KEEP_OLDEST) },
                                        label = { Text("Oldest (Original)", fontSize = 10.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (uiState.duplicateKeepStrategy == DuplicateKeepStrategy.KEEP_OLDEST) AmberAccent.copy(alpha = 0.25f) else Color.Transparent,
                                            labelColor = if (uiState.duplicateKeepStrategy == DuplicateKeepStrategy.KEEP_OLDEST) AmberAccent else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    SuggestionChip(
                                        onClick = { viewModel.setDuplicateKeepStrategy(DuplicateKeepStrategy.KEEP_NEWEST) },
                                        label = { Text("Newest", fontSize = 10.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (uiState.duplicateKeepStrategy == DuplicateKeepStrategy.KEEP_NEWEST) AmberAccent.copy(alpha = 0.25f) else Color.Transparent,
                                            labelColor = if (uiState.duplicateKeepStrategy == DuplicateKeepStrategy.KEEP_NEWEST) AmberAccent else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Group Threads", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = uiState.groupByThreadsInDuplicates,
                                        onCheckedChange = { viewModel.toggleGroupByThreads(it) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Selection Controller Bar (Context-aware for search results)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchInput.isNotBlank()) {
                            "$selectedCount of ${cleanableMessages.size} matching selected"
                        } else {
                            "$selectedCount of ${cleanableMessages.size} selected"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                if (searchInput.isNotBlank()) {
                                    viewModel.selectFilteredMessages(matchingIds, !allSelected)
                                } else {
                                    viewModel.selectAll(!allSelected, uiState.selectedCategoryTab)
                                }
                            },
                            modifier = Modifier.testTag("select_all_toggle")
                        ) {
                            Text(
                                text = if (allSelected) "Deselect All" else if (searchInput.isNotBlank()) "Select All Matching" else "Select All",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Messages List or Empty State
        if (cleanableMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (searchInput.isNotBlank()) AppIcons.SearchOff else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (searchInput.isNotBlank()) AmberAccent else TealAccent,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = if (searchInput.isNotBlank()) "No Matching Messages Found" else "No Messages to Clean",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (searchInput.isNotBlank())
                            "No messages matching '$searchInput' found in '${uiState.selectedCategoryTab.displayName}'. Try another sender name, number, or keyword."
                        else
                            "Great news! There are no messages matching '${uiState.selectedCategoryTab.displayName}'.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    if (searchInput.isNotBlank()) {
                        Button(
                            onClick = {
                                searchInput = ""
                                viewModel.setSearchQuery("")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clear Search Query", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        } else if (isDuplicateTab && uiState.groupByThreadsInDuplicates && threadClusters.isNotEmpty() && searchInput.isBlank()) {
            // Render Thread Clusters View
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(threadClusters, key = { it.groupId }) { cluster ->
                    ThreadDuplicateClusterCard(
                        cluster = cluster,
                        onFixThread = { viewModel.fixDuplicateThread(cluster) },
                        onClickDetail = { viewModel.showMessageDetails(it) }
                    )
                }
            }
        } else {
            // Standard individual message cards
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cleanableMessages, key = { it.id }) { message ->
                    MessageCard(
                        message = message,
                        isSelected = message.isSelected,
                        onToggleSelect = { viewModel.toggleMessageSelection(message.id) },
                        onClickDetail = { viewModel.showMessageDetails(message) }
                    )
                }
            }
        }

        // Bottom Sticky Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Safe Quarantine button
                Button(
                    onClick = { viewModel.quarantineSelectedMessages() },
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("quarantine_action_button")
                ) {
                    Icon(
                        AppIcons.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quarantine ($selectedCount)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Permanent Delete button (opens confirmation dialog!)
                OutlinedButton(
                    onClick = { viewModel.promptPermanentDelete(fromQuarantine = false) },
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RedDestructive
                    ),
                    border = ButtonDefaults.outlinedButtonBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(RedDestructive.copy(alpha = 0.6f))
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("permanent_delete_action_button")
                ) {
                    Icon(
                        AppIcons.DeleteForever,
                        contentDescription = null,
                        tint = RedDestructive,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Delete ($selectedCount)",
                        color = RedDestructive,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
