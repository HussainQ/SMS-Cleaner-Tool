package com.example.model

data class CloudBackupPayload(
    val version: Int = 2,
    val app: String = "CleanSMS",
    val backupDate: Long = System.currentTimeMillis(),
    val provider: String = "Google Drive",
    val totalCount: Int,
    val messages: List<BackupMessageDto>,
    val rules: List<CustomFilterRule> = emptyList()
)

data class BackupMessageDto(
    val id: String,
    val address: String,
    val body: String,
    val date: Long,
    val category: String,
    val matchReason: String,
    val otpCode: String? = null,
    val protocol: String = "SMS",
    val threadId: Long = 0L,
    val isRcsChat: Boolean = false
)

data class BackupHistoryRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val provider: String,
    val backupTimestamp: Long = System.currentTimeMillis(),
    val messageCount: Int,
    val fileName: String,
    val sizeBytes: Long,
    val fileContentJson: String
)
