package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SmsClassifier
import com.example.model.ContactInfo
import com.example.model.CustomFilterRule
import com.example.model.SmsMessageItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomRuleSheet(
    existingRule: CustomFilterRule? = null,
    existingMessages: List<SmsMessageItem>,
    availableContacts: List<ContactInfo>,
    hasContactsPermission: Boolean,
    onRequestContactsPermission: () -> Unit,
    onSaveRule: (CustomFilterRule) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = existingRule != null

    var name by remember(existingRule?.id) { mutableStateOf(existingRule?.name ?: "") }
    var senderFilter by remember(existingRule?.id) { mutableStateOf(existingRule?.senderFilter ?: "") }
    var contactFilter by remember(existingRule?.id) { mutableStateOf(existingRule?.contactFilter ?: "") }
    var keywords by remember(existingRule?.id) { mutableStateOf(existingRule?.keywords ?: "") }
    var matchAnyKeyword by remember(existingRule?.id) { mutableStateOf(existingRule?.matchAnyKeyword ?: true) }
    var olderThanDaysText by remember(existingRule?.id) { mutableStateOf(existingRule?.olderThanDays?.toString() ?: "") }
    var isEnabled by remember(existingRule?.id) { mutableStateOf(existingRule?.isEnabled ?: true) }
    var showContactPicker by remember { mutableStateOf(false) }

    var matchingCount by remember { mutableIntStateOf(0) }

    // Evaluate live preview count off the Main thread so typing never drops frames
    LaunchedEffect(senderFilter, contactFilter, keywords, matchAnyKeyword, olderThanDaysText, existingMessages) {
        val candidateRule = CustomFilterRule(
            id = existingRule?.id ?: "preview",
            name = "preview",
            senderFilter = senderFilter.trim(),
            contactFilter = contactFilter.trim(),
            keywords = keywords.trim(),
            matchAnyKeyword = matchAnyKeyword,
            olderThanDays = olderThanDaysText.toIntOrNull(),
            isEnabled = true
        )
        matchingCount = withContext(Dispatchers.Default) {
            SmsClassifier.countRuleMatches(existingMessages, candidateRule)
        }
    }

    val contactTokens = remember(contactFilter) {
        contactFilter.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_custom_rule_sheet")
    ) {
        BackHandler(enabled = showContactPicker) {
            showContactPicker = false
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Custom Cleaning Rule" else "New Custom Cleaning Rule",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Text(
                text = if (isEditing) {
                    "Update this rule's contacts, sender filters, keywords, or age threshold."
                } else {
                    "Define rules to automatically flag and clean specific messages by contact, sender, keywords, or age."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Rule Name") },
                placeholder = { Text("e.g., Old Banking Notifications") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rule_name_input")
            )

            // Per-Contact Picker Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Per-Contact Filter",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Link from Device Contacts & Google Messages",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = { showContactPicker = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("rule_select_contacts_btn")
                        ) {
                            Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (contactTokens.isEmpty()) "Select Contacts" else "Edit (${contactTokens.size})",
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (contactTokens.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            contactTokens.forEach { token ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        val remaining = contactTokens.filterNot { it == token }
                                        contactFilter = remaining.joinToString(", ")
                                    },
                                    label = { Text(token, fontSize = 11.sp) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove contact",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = senderFilter,
                onValueChange = { senderFilter = it },
                label = { Text("Sender Name or Phone Number (Optional)") },
                placeholder = { Text("e.g. Bank, Amazon, or +1800...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rule_sender_input")
            )

            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = { Text("Keywords in Message Body (Comma-separated)") },
                placeholder = { Text("e.g. statement, balance, order placed") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rule_keywords_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Match Any Keyword (OR vs AND)",
                    fontSize = 14.sp
                )
                Switch(
                    checked = matchAnyKeyword,
                    onCheckedChange = { matchAnyKeyword = it }
                )
            }

            OutlinedTextField(
                value = olderThanDaysText,
                onValueChange = { olderThanDaysText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Message Age (Optional)") },
                placeholder = { Text("e.g. 30 (older than 30 days)") },
                trailingIcon = { Text("days", modifier = Modifier.padding(end = 12.dp)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rule_age_input")
            )

            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Rule Enabled",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.testTag("rule_enabled_switch")
                    )
                }
            }

            // Live rule matching count badge
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Match Preview: Currently matches $matchingCount message(s) in inbox",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            val hasCriteria = senderFilter.isNotBlank() ||
                contactFilter.isNotBlank() ||
                keywords.isNotBlank() ||
                olderThanDaysText.isNotBlank()

            Button(
                onClick = {
                    if (name.isNotBlank() && hasCriteria) {
                        val rule = if (existingRule != null) {
                            existingRule.copy(
                                name = name.trim(),
                                senderFilter = senderFilter.trim(),
                                contactFilter = contactFilter.trim(),
                                keywords = keywords.trim(),
                                matchAnyKeyword = matchAnyKeyword,
                                olderThanDays = olderThanDaysText.toIntOrNull(),
                                isEnabled = isEnabled
                            )
                        } else {
                            CustomFilterRule(
                                name = name.trim(),
                                senderFilter = senderFilter.trim(),
                                contactFilter = contactFilter.trim(),
                                keywords = keywords.trim(),
                                matchAnyKeyword = matchAnyKeyword,
                                olderThanDays = olderThanDaysText.toIntOrNull(),
                                isEnabled = true
                            )
                        }
                        onSaveRule(rule)
                    }
                },
                enabled = name.isNotBlank() && hasCriteria,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_rule_button")
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Default.Save else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEditing) "Update Rule & Apply" else "Save Rule & Apply",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showContactPicker) {
        val initialTokens = remember(contactFilter) { SmsClassifier.parseContactFilterTokens(contactFilter) }
        ContactPickerDialog(
            availableContacts = availableContacts,
            initiallySelectedTokens = initialTokens,
            hasContactsPermission = hasContactsPermission,
            onRequestContactsPermission = onRequestContactsPermission,
            onConfirmSelection = { chosen ->
                val labels = chosen.flatMap { c ->
                    listOfNotNull(
                        c.displayName.takeIf { it.isNotBlank() },
                        c.phoneNumbers.firstOrNull()?.takeIf { it.isNotBlank() && !it.equals(c.displayName, ignoreCase = true) }
                    )
                }.distinct()
                contactFilter = labels.joinToString(", ")
                if (name.isBlank() && chosen.isNotEmpty()) {
                    name = if (chosen.size == 1) {
                        "Contact: ${chosen.first().displayName}"
                    } else {
                        "Contacts (${chosen.size} selected)"
                    }
                }
                showContactPicker = false
            },
            onDismiss = { showContactPicker = false }
        )
    }
}
