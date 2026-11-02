package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CloudBackupPayload
import com.example.ui.theme.TealAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RestoreCloudDialog(
    payload: CloudBackupPayload,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(payload.backupDate))

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("restore_cloud_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Restore",
                tint = TealAccent,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Restore from ${payload.provider}?",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Backup date: $dateStr\nMessages: ${payload.messages.size} | Rules: ${payload.rules.size}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Restoring will add these messages back to your active inbox and re-enable any saved custom cleaning rules.",
                    fontSize = 13.sp
                )

                Text(
                    text = "Messages in backup:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(payload.messages) { msg ->
                        Column {
                            Text(
                                text = "${msg.address}: ${msg.body.take(50)}...",
                                fontSize = 11.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmRestore,
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                modifier = Modifier.testTag("confirm_restore_button")
            ) {
                Text("Restore to Inbox", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
