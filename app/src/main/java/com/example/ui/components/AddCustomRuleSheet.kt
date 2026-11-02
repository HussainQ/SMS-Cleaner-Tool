package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CustomFilterRule
import com.example.model.SmsMessageItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomRuleSheet(
    existingMessages: List<SmsMessageItem>,
    onSaveRule: (CustomFilterRule) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var senderFilter by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var matchAnyKeyword by remember { mutableStateOf(true) }
    var olderThanDaysText by remember { mutableStateOf("") }

    // Live preview of how many messages match
    val matchingCount = remember(name, senderFilter, keywords, matchAnyKeyword, olderThanDaysText, existingMessages) {
        val now = System.currentTimeMillis()
        val days = olderThanDaysText.toIntOrNull()
        val minAgeMillis = if (days != null && days > 0) days * 86400000L else 0L

        existingMessages.count { msg ->
            var matches = true
            if (senderFilter.isNotBlank()) {
                if (!msg.address.contains(senderFilter.trim(), ignoreCase = true)) {
                    matches = false
                }
            }
            if (matches && keywords.isNotBlank()) {
                val kwList = keywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                if (kwList.isNotEmpty()) {
                    val bodyLower = msg.body.lowercase()
                    val kwMatch = if (matchAnyKeyword) kwList.any { bodyLower.contains(it) } else kwList.all { bodyLower.contains(it) }
                    if (!kwMatch) matches = false
                }
            }
            if (matches && minAgeMillis > 0) {
                if ((now - msg.date) < minAgeMillis) matches = false
            }
            matches && (senderFilter.isNotBlank() || keywords.isNotBlank() || minAgeMillis > 0)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_custom_rule_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "New Custom Cleaning Rule",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Text(
                text = "Define rules to automatically flag and clean specific messages by sender, keywords, or age.",
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

            OutlinedTextField(
                value = senderFilter,
                onValueChange = { senderFilter = it },
                label = { Text("Sender Name or Phone Number") },
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

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val rule = CustomFilterRule(
                            name = name.trim(),
                            senderFilter = senderFilter.trim(),
                            keywords = keywords.trim(),
                            matchAnyKeyword = matchAnyKeyword,
                            olderThanDays = olderThanDaysText.toIntOrNull(),
                            isEnabled = true
                        )
                        onSaveRule(rule)
                    }
                },
                enabled = name.isNotBlank() && (senderFilter.isNotBlank() || keywords.isNotBlank() || olderThanDaysText.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_rule_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Rule & Apply")
            }
        }
    }
}
