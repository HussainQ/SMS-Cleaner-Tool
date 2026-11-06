package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OAuthServiceDefinition
import com.example.ui.theme.*

/**
 * Interactive dialog requesting explicit user authorization before pulling device OAuth secrets/keys.
 *
 * Guarantees that:
 * 1. Tokens are pulled from the device IF AND ONLY IF the user clicks "Authorize & Save Locally".
 * 2. If cancelled or denied, no device secrets are accessed or stored.
 * 3. Clear disclosure of scopes and strictly on-device storage.
 */
@Composable
fun AuthorizeServiceDialog(
    serviceDef: OAuthServiceDefinition,
    availableAccounts: List<String>,
    selectedAccount: String,
    isAuthorizing: Boolean,
    onSelectAccount: (String) -> Unit,
    onConfirmAuthorization: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isAuthorizing) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = CyanAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Authorize Connection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = serviceDef.name,
                        fontSize = 13.sp,
                        color = CyanAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = serviceDef.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                // Scopes summary
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Requested OAuth Scopes:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        serviceDef.requiredScopes.forEach { scope ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = TealAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = scope.substringAfterLast("/"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Account Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Device Account for Connection:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val accounts = if (availableAccounts.isEmpty()) listOf(serviceDef.defaultAccount) else availableAccounts
                    accounts.forEach { acc ->
                        val isSelected = acc == selectedAccount
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CyanAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, if (isSelected) CyanAccent else Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAccount(acc) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = if (isSelected) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = acc,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectAccount(acc) },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Privacy Disclosure Guarantee
                Surface(
                    color = AmberAccent.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Strictly Local Storage Policy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = AmberAccent
                            )
                            Text(
                                text = "Device OAuth keys are pulled automatically IF AND ONLY IF you click Authorize. " +
                                       "Keys are stored strictly on this device and are never sent to external servers. You can revoke access at any time.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmAuthorization,
                enabled = !isAuthorizing,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_authorize_button")
            ) {
                if (isAuthorizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Navy900
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pulling Secret...", color = Navy900, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Navy900,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Authorize & Save Locally", color = Navy900, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isAuthorizing,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("cancel_authorize_button")
            ) {
                Text("Deny / Cancel", fontSize = 13.sp)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
