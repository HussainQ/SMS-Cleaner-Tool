package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SmsMessageItem
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.RedDestructive
import com.example.ui.theme.TealAccent

@Composable
fun PermanentDeleteConfirmDialog(
    messages: List<SmsMessageItem>,
    isFromQuarantine: Boolean,
    testingRestoreEnabled: Boolean = false,
    isDemoMode: Boolean = true,
    isDefaultSmsApp: Boolean = false,
    onRequestDefaultSmsRole: () -> Unit = {},
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("permanent_delete_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = RedDestructive,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Permanently Delete ${messages.size} Messages?",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "This action will permanently remove ${messages.size} selected message(s) from your device and Google Messages.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )

                if (!isDemoMode) {
                    if (isDefaultSmsApp) {
                        Surface(
                            color = TealAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_dialog_default_sms_indicator")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Default SMS App",
                                    tint = TealAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Default SMS App • Direct device delete unlocked",
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = AmberAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "CleanSMS is not currently the Default SMS app. Set it as Default SMS App so Android permits deleting from Google Messages.",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Button(
                                    onClick = onRequestDefaultSmsRole,
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("delete_dialog_set_default_sms_btn")
                                ) {
                                    Icon(
                                        Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Set as Default SMS App",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = if (testingRestoreEnabled) {
                        "Testing Recovery Toggle is ON: A local test snapshot will be saved in Vault > Deleted (Test) so you can restore these messages during testing."
                    } else {
                        "WARNING: This cannot be undone. To prevent accidental data loss, verify your selection below."
                    },
                    color = if (testingRestoreEnabled) MaterialTheme.colorScheme.primary else RedDestructive,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Preview of messages being deleted
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    messages.take(3).forEach { msg ->
                        Text(
                            text = "• [${msg.address}] ${msg.body.take(45)}...",
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (messages.size > 3) {
                        Text(
                            text = "+ ${messages.size - 3} more messages",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = RedDestructive),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text("Delete Permanently", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
