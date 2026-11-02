package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DuplicateKeepStrategy
import com.example.model.SmsCategory
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.components.MessageCard
import com.example.ui.components.ThreadDuplicateClusterCard
import com.example.ui.theme.*

@Composable
fun PreviewCleanScreen(
    uiState: CleanSmsUiState,
    viewModel: CleanSmsViewModel,
    modifier: Modifier = Modifier
) {
    var searchInput by remember { mutableStateOf(uiState.searchQuery) }

    // Quick suggestion keywords for search
    val quickSearchSuggestions = listOf("RCS", "Amazon", "Uber", "OTP", "Dominos", "Sale", "Verify", "Bank")

    // Filter candidates matching category tab and search query
    val cleanableMessages = remember(
        uiState.classifiedMessages,
        uiState.selectedCategoryTab,
        uiState.searchQuery
    ) {
        val query = uiState.searchQuery.trim().lowercase()
        uiState.classifiedMessages.filter { msg ->
            val matchesCandidate = msg.isCleanCandidate
            val matchesCategory = when (uiState.selectedCategoryTab) {
                SmsCategory.ALL -> matchesCandidate
                else -> msg.category == uiState.selectedCategoryTab && matchesCandidate
            }
            val matchesSearch = if (query.isBlank()) {
                true
            } else {
                msg.address.lowercase().contains(query) ||
                        msg.body.lowercase().contains(query) ||
                        (query == "rcs" && (msg.isRcsChat || msg.protocol == com.example.model.MessageProtocol.RCS_CHAT)) ||
                        (msg.otpCode != null && msg.otpCode.contains(query)) ||
                        msg.matchReason.lowercase().contains(query)
            }
            matchesCategory && matchesSearch
        }
    }

    val selectedCount = cleanableMessages.count { it.isSelected }
    val allSelected = cleanableMessages.isNotEmpty() && cleanableMessages.all { it.isSelected }
    val matchingIds = remember(cleanableMessages) { cleanableMessages.map { it.id }.toSet() }

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
                // Interactive Search Bar
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = {
                        searchInput = it
                        viewModel.setSearchQuery(it)
                    },
                    placeholder = {
                        Text(
                            text = "Search content, sender, or OTP code...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (searchInput.isNotBlank()) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
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
                        focusedBorderColor = CyanAccent,
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
                                containerColor = if (isCurrentSearch) CyanAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                labelColor = if (isCurrentSearch) CyanAccent else MaterialTheme.colorScheme.onSurface
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (isCurrentSearch) CyanAccent else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("quick_search_$tag")
                        )
                    }
                }

                // Category Filter Pills
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
                        val count = uiState.classifiedMessages.count {
                            if (cat == SmsCategory.ALL) it.isCleanCandidate else it.category == cat && it.isCleanCandidate
                        }

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
                                        Icons.Default.Forum,
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
                        imageVector = if (searchInput.isNotBlank()) Icons.Default.SearchOff else Icons.Default.CheckCircle,
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
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("quarantine_action_button")
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Navy900,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quarantine ($selectedCount)",
                        color = Navy900,
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
                        Icons.Default.DeleteForever,
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
