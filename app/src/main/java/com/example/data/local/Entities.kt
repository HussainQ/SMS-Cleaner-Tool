package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.ContactInfo
import com.example.model.ContactThreadHelper
import com.example.model.MessageProtocol
import com.example.model.PhoneNormalizer
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem

@Entity(
    tableName = "quarantine_messages",
    indices = [
        Index(value = ["threadId"]),
        Index(value = ["address"]),
        Index(value = ["addressKey"])
    ]
)
data class QuarantineEntity(
    @PrimaryKey val id: String,
    val address: String,
    val addressKey: String = PhoneNormalizer.primaryKey(address),
    val body: String,
    val date: Long,
    val categoryString: String,
    val matchReason: String,
    val otpCode: String?,
    val protocolString: String = "SMS",
    val threadId: Long = 0L,
    val isRcsChat: Boolean = false,
    val contactId: String? = null,
    val contactName: String? = null,
    val type: Int = 1,
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
            type = type,
            category = cat,
            protocol = proto,
            threadId = threadId,
            isRcsChat = isRcsChat || proto == MessageProtocol.RCS_CHAT,
            contactId = contactId,
            contactName = contactName,
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
                addressKey = PhoneNormalizer.primaryKey(item.address),
                body = item.body,
                date = item.date,
                categoryString = item.category.name,
                matchReason = item.matchReason,
                otpCode = item.otpCode,
                protocolString = item.protocol.name,
                threadId = item.threadId,
                isRcsChat = item.isRcsChat || item.protocol == MessageProtocol.RCS_CHAT,
                contactId = item.contactId,
                contactName = item.contactName,
                type = item.type,
                quarantinedAt = System.currentTimeMillis()
            )
        }
    }
}

@Entity(
    tableName = "deleted_test_snapshots",
    indices = [
        Index(value = ["threadId"]),
        Index(value = ["address"]),
        Index(value = ["addressKey"])
    ]
)
data class DeletedTestSnapshotEntity(
    @PrimaryKey val id: String,
    val address: String,
    val addressKey: String = PhoneNormalizer.primaryKey(address),
    val body: String,
    val date: Long,
    val type: Int = 1,
    val categoryString: String,
    val matchReason: String,
    val otpCode: String?,
    val protocolString: String = "SMS",
    val threadId: Long = 0L,
    val isRcsChat: Boolean = false,
    val contactId: String? = null,
    val contactName: String? = null,
    val deletedAt: Long = System.currentTimeMillis()
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
            type = type,
            category = cat,
            protocol = proto,
            threadId = threadId,
            isRcsChat = isRcsChat || proto == MessageProtocol.RCS_CHAT,
            contactId = contactId,
            contactName = contactName,
            matchReason = matchReason,
            otpCode = otpCode,
            isQuarantined = false,
            isSelected = false,
            deletedAt = deletedAt.takeIf { it > 0L }
        )
    }

    companion object {
        fun fromSmsMessageItem(item: SmsMessageItem): DeletedTestSnapshotEntity {
            return DeletedTestSnapshotEntity(
                id = item.id,
                address = item.address,
                addressKey = PhoneNormalizer.primaryKey(item.address),
                body = item.body,
                date = item.date,
                type = item.type,
                categoryString = item.category.name,
                matchReason = item.matchReason,
                otpCode = item.otpCode,
                protocolString = item.protocol.name,
                threadId = item.threadId,
                isRcsChat = item.isRcsChat || item.protocol == MessageProtocol.RCS_CHAT,
                contactId = item.contactId,
                contactName = item.contactName,
                deletedAt = System.currentTimeMillis()
            )
        }
    }
}

@Entity(tableName = "custom_rules")
data class CustomRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val senderFilter: String,
    val contactFilter: String = "",
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

@Entity(
    tableName = "inbox_message_cache",
    indices = [
        Index(value = ["threadId"]),
        Index(value = ["addressKey"]),
        Index(value = ["contactId"]),
        Index(value = ["contactNameLower"])
    ]
)
data class InboxMessageCacheEntity(
    @PrimaryKey val id: String,
    val address: String,
    val addressKey: String,
    val body: String,
    val date: Long,
    val type: Int = 1,
    val threadId: Long = 0L,
    val protocolString: String = "SMS",
    val isRcsChat: Boolean = false,
    val contactId: String? = null,
    val contactName: String? = null,
    val contactNameLower: String = "",
    val isArchived: Boolean = false,
    val isSpamMarked: Boolean = false,
    val folderSource: String = ""
) {
    fun toSmsMessageItem(): SmsMessageItem {
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
            type = type,
            category = SmsCategory.NORMAL,
            protocol = proto,
            threadId = threadId,
            isRcsChat = isRcsChat || proto == MessageProtocol.RCS_CHAT,
            contactId = contactId,
            contactName = contactName,
            isArchived = isArchived,
            isSpamMarked = isSpamMarked,
            folderSource = folderSource
        )
    }

    companion object {
        fun fromSmsMessageItem(item: SmsMessageItem): InboxMessageCacheEntity {
            return InboxMessageCacheEntity(
                id = item.id,
                address = item.address,
                addressKey = PhoneNormalizer.primaryKey(item.address),
                body = item.body,
                date = item.date,
                type = item.type,
                threadId = item.threadId,
                protocolString = item.protocol.name,
                isRcsChat = item.isRcsChat || item.protocol == MessageProtocol.RCS_CHAT,
                contactId = item.contactId,
                contactName = item.contactName,
                contactNameLower = item.contactName?.trim()?.lowercase().orEmpty(),
                isArchived = item.isArchived,
                isSpamMarked = item.isSpamMarked,
                folderSource = item.folderSource
            )
        }
    }
}

@Entity(
    tableName = "contact_filter_selections",
    indices = [
        Index(value = ["scope"]),
        Index(value = ["threadId"])
    ]
)
data class ContactFilterSelectionEntity(
    @PrimaryKey val compositeKey: String,
    val scope: String,
    val contactId: String,
    val displayName: String,
    val phoneNumbersCsv: String,
    val matchTokensCsv: String,
    val isFromDeviceContacts: Boolean = false,
    val threadId: Long? = null,
    val selectedAt: Long = System.currentTimeMillis()
) {
    fun toContactInfo(): ContactInfo {
        val phones = phoneNumbersCsv
            .split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return ContactInfo(
            id = contactId,
            displayName = displayName,
            phoneNumbers = phones,
            isFromDeviceContacts = isFromDeviceContacts,
            threadId = threadId
        )
    }

    companion object {
        const val SCOPE_SCANNER = "SCANNER"
        const val SCOPE_VAULT = "VAULT"

        fun compositeKeyFor(scope: String, contactId: String): String = "$scope:$contactId"

        fun fromContactInfo(scope: String, contact: ContactInfo): ContactFilterSelectionEntity {
            val tokens = ContactThreadHelper.collectTokensForContacts(listOf(contact))
            val sanitizedTokens = tokens
                .map { it.replace(",", "").trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
            val matchCsv = if (sanitizedTokens.isEmpty()) {
                ","
            } else {
                ",${sanitizedTokens.joinToString(",")},"
            }
            return ContactFilterSelectionEntity(
                compositeKey = compositeKeyFor(scope, contact.id),
                scope = scope,
                contactId = contact.id,
                displayName = contact.displayName,
                phoneNumbersCsv = contact.phoneNumbers.joinToString("|"),
                matchTokensCsv = matchCsv,
                isFromDeviceContacts = contact.isFromDeviceContacts,
                threadId = contact.threadId
            )
        }
    }
}

/**
 * Persistent cross-app Archived / Spam state for conversations & senders.
 * Ensures messages archived or marked as spam (in Google Messages, another SMS app,
 * Android Blocked Numbers, or CleanSMS) stay categorized across rescans and app switches.
 */
@Entity(
    tableName = "thread_folder_states",
    indices = [
        Index(value = ["addressKey"]),
        Index(value = ["threadId"]),
        Index(value = ["isArchived"]),
        Index(value = ["isSpam"])
    ]
)
data class ThreadFolderStateEntity(
    @PrimaryKey val stateKey: String,
    val addressKey: String,
    val threadId: Long = 0L,
    val isArchived: Boolean = false,
    val isSpam: Boolean = false,
    val sourceApp: String = "CLEANSMS_USER",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Projection row returned by Room SQLite GROUP BY queries for the 'Filter By Contact' UI.
 */
data class ContactThreadSummaryRow(
    val contactKey: String,
    val contactId: String?,
    val displayName: String,
    val primaryAddress: String,
    val messageCount: Int,
    val threadIdsCsv: String?
)


