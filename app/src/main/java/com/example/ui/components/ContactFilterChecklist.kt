package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ContactFilterMode
import com.example.model.ContactInfo
import com.example.model.ContactThreadEntry
import com.example.ui.theme.*

/**
 * Reusable Filter By Contact UI component listing contacts with checkboxes, thread/message counts,
 * search filtering, Select All / Clear controls, and optional Target/Protect mode selection.
 */
@Composable
fun ContactFilterChecklist(
    title: String,
    subtitle: String,
    entries: List<ContactThreadEntry>,
    selectedContactIds: Set<String>,
    onToggleContact: (ContactInfo) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    hasContactsPermission: Boolean = true,
    onRequestContactsPermission: (() -> Unit)? = null,
    filterEnabled: Boolean = true,
    onToggleFilterEnabled: ((Boolean) -> Unit)? = null,
    filterMode: ContactFilterMode? = null,
    onSelectFilterMode: ((ContactFilterMode) -> Unit)? = null,
    scopeSummaryText: String? = null,
    emptyMessage: String = "No contacts or message threads found",
    maxListHeightDp: Int = 220,
    testTagPrefix: String = "contact_filter",
    switchTestTag: String = "${testTagPrefix}_enable_switch",
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    onOpenFullPicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var localSearchQuery by remember { mutableStateOf("") }
    val activeSearchQuery = searchQuery ?: localSearchQuery
    val updateSearchQuery: (String) -> Unit = { next ->
        if (onSearchQueryChange != null) {
            onSearchQueryChange(next)
        } else {
            localSearchQuery = next
        }
    }

    // When ViewModel + Room @Query drives filtering (onSearchQueryChange != null), entries are already
    // SQLite-filtered on background IO threads. Otherwise fallback cleanly for standalone callers.
    val filteredEntries = remember(entries, activeSearchQuery, onSearchQueryChange) {
        if (onSearchQueryChange != null) {
            entries
        } else {
            val q = activeSearchQuery.trim().lowercase()
            if (q.isEmpty()) {
                entries
            } else {
                entries.filter { entry ->
                    entry.contact.displayName.lowercase().contains(q) ||
                        entry.contact.phoneNumbers.any { it.lowercase().contains(q) }
                }
            }
        }
    }

    val selectedCount = selectedContactIds.size
    val selectedThreadCount = remember(entries, selectedContactIds) {
        entries
            .asSequence()
            .filter { it.contact.id in selectedContactIds }
            .flatMap { it.threadIds.asSequence() }
            .distinct()
            .count()
    }

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
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with optional Enable Switch
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
                                text = title,
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
                                        text = "$selectedCount checked",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }

                if (onToggleFilterEnabled != null) {
                    Switch(
                        checked = filterEnabled,
                        onCheckedChange = onToggleFilterEnabled,
                        modifier = Modifier.testTag(switchTestTag)
                    )
                }
            }

            // Mode Chips (Target Selected Threads vs Protect VIP Contacts)
            if (filterMode != null && onSelectFilterMode != null) {
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
                    if (onOpenFullPicker != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = onOpenFullPicker,
                            modifier = Modifier.testTag("main_choose_contacts_button")
                        ) {
                            Icon(
                                imageVector = AppIcons.OpenInFull,
                                contentDescription = "Open Full Contacts Picker",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Active Scope Summary Strip
            if (filterEnabled && selectedCount > 0) {
                val defaultScopeText = if (filterMode == ContactFilterMode.PROTECT_CONTACTS) {
                    "Protecting $selectedCount contact(s) across $selectedThreadCount thread(s) from all rules"
                } else {
                    "Scoped to $selectedCount contact(s) ($selectedThreadCount thread(s)): only duplicates & rules in these threads apply"
                }
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
                        .testTag("${testTagPrefix}_scope_banner")
                ) {
                    Text(
                        text = scopeSummaryText ?: defaultScopeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = scopeAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else if (filterEnabled && onToggleFilterEnabled != null && entries.isNotEmpty()) {
                Text(
                    text = "Check one or more contacts below to scope de-duplication & rules solely to their threads.",
                    fontSize = 11.sp,
                    color = AmberAccent
                )
            }

            // Inline Contacts Permission Prompt if not granted
            if (!hasContactsPermission && onRequestContactsPermission != null) {
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
                            modifier = Modifier.testTag("${testTagPrefix}_grant_contacts_btn")
                        ) {
                            Text("Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Search Bar + Select All / Clear Row
            if (entries.isNotEmpty() || activeSearchQuery.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = activeSearchQuery,
                        onValueChange = updateSearchQuery,
                        placeholder = { Text("Filter contact name or number...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (activeSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { updateSearchQuery("") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear contact search",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("${testTagPrefix}_search_input")
                    )

                    TextButton(
                        onClick = onSelectAll,
                        enabled = entries.isNotEmpty() && selectedCount < entries.size,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("${testTagPrefix}_select_all_btn")
                    ) {
                        Text("All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onClearAll,
                        enabled = selectedCount > 0,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("${testTagPrefix}_clear_btn")
                    ) {
                        Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Checkbox Contact List
            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (activeSearchQuery.isNotBlank()) "No contacts match '$activeSearchQuery'" else emptyMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeightDp.dp)
                        .testTag("${testTagPrefix}_list"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredEntries, key = { it.contact.id }) { entry ->
                        val contact = entry.contact
                        val isChecked = contact.id in selectedContactIds
                        ContactCheckboxRow(
                            entry = entry,
                            isChecked = isChecked,
                            onToggle = { onToggleContact(contact) },
                            testTagPrefix = testTagPrefix
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactCheckboxRow(
    entry: ContactThreadEntry,
    isChecked: Boolean,
    onToggle: () -> Unit,
    testTagPrefix: String
) {
    val contact = entry.contact
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(
            1.dp,
            if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .testTag("${testTagPrefix}_row_${contact.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("${testTagPrefix}_checkbox_${contact.id}")
            )

            Surface(
                shape = CircleShape,
                color = if (contact.isFromDeviceContacts) TealAccent.copy(alpha = 0.2f) else PurpleAccent.copy(alpha = 0.2f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = contact.displayName.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (contact.isFromDeviceContacts) TealAccent else PurpleAccent
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = contact.phoneNumbers.firstOrNull()?.takeIf {
                    !it.equals(contact.displayName, ignoreCase = true)
                } ?: if (contact.isFromDeviceContacts) "Device Contact" else "Message Thread"
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry.messageCount > 0) {
                Surface(
                    color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${entry.messageCount} msg${if (entry.messageCount == 1) "" else "s"}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
