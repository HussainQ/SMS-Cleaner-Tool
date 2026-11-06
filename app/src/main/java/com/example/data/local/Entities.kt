package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.MessageProtocol
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem

@Entity(tableName = "quarantine_messages")
data class QuarantineEntity(
    @PrimaryKey val id: String,
    val address: String,
    val body: String,
    val date: Long,
    val categoryString: String,
    val matchReason: String,
    val otpCode: String?,
    val protocolString: String = "SMS",
    val threadId: Long = 0L,
    val isRcsChat: Boolean = false,
    val quarantinedAt: Long = System.currentTimeMillis()
) {
    fun toSmsMessageItem(): SmsMessageItem {
        val cat = try {
            SmsCategory.valueOf(categoryString)
        } catch (_: Exception) {
            SmsCategory.NORMAL
        }
        val proto = try {
            MessageProtocol.valueOf(protocolString)
        } catch (_: Exception) {
            if (isRcsChat) MessageProtocol.RCS_CHAT else MessageProtocol.SMS
        }
        return SmsMessageItem(
            id = id,
            address = address,
            body = body,
            date = date,
            category = cat,
            protocol = proto,
            threadId = threadId,
            isRcsChat = isRcsChat || proto == MessageProtocol.RCS_CHAT,
            matchReason = matchReason,
            otpCode = otpCode,
            isQuarantined = true,
            isSelected = false
        )
    }

    companion object {
        fun fromSmsMessageItem(item: SmsMessageItem): QuarantineEntity {
            return QuarantineEntity(
                id = item.id,
                address = item.address,
                body = item.body,
                date = item.date,
                categoryString = item.category.name,
                matchReason = item.matchReason,
                otpCode = item.otpCode,
                protocolString = item.protocol.name,
                threadId = item.threadId,
                isRcsChat = item.isRcsChat || item.protocol == MessageProtocol.RCS_CHAT,
                quarantinedAt = System.currentTimeMillis()
            )
        }
    }
}

@Entity(tableName = "custom_rules")
data class CustomRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val senderFilter: String,
    val keywords: String,
    val matchAnyKeyword: Boolean,
    val olderThanDays: Int?,
    val startDateEpoch: Long?,
    val endDateEpoch: Long?,
    val isEnabled: Boolean,
    val createdAt: Long
)

@Entity(tableName = "backup_records")
data class BackupRecordEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val backupTimestamp: Long,
    val messageCount: Int,
    val fileName: String,
    val sizeBytes: Long,
    val fileContentJson: String
)

@Entity(tableName = "oauth_credentials")
data class OAuthCredentialEntity(
    @PrimaryKey val serviceId: String,
    val serviceName: String,
    val accountName: String,
    val accessToken: String,
    val tokenType: String,
    val scopes: String,
    val authorizedAt: Long,
    val expiresAt: Long,
    val isLocallyStored: Boolean = true
) {
    fun toLocalCredential(): com.example.model.LocalOAuthCredential {
        return com.example.model.LocalOAuthCredential(
            serviceId = serviceId,
            serviceName = serviceName,
            accountName = accountName,
            accessToken = accessToken,
            tokenType = tokenType,
            scopes = scopes,
            authorizedAt = authorizedAt,
            expiresAt = expiresAt,
            isLocallyStored = isLocallyStored
        )
    }

    companion object {
        fun fromLocalCredential(cred: com.example.model.LocalOAuthCredential): OAuthCredentialEntity {
            return OAuthCredentialEntity(
                serviceId = cred.serviceId,
                serviceName = cred.serviceName,
                accountName = cred.accountName,
                accessToken = cred.accessToken,
                tokenType = cred.tokenType,
                scopes = cred.scopes,
                authorizedAt = cred.authorizedAt,
                expiresAt = cred.expiresAt,
                isLocallyStored = cred.isLocallyStored
            )
        }
    }
}

