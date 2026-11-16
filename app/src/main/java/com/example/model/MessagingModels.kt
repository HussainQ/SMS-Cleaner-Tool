package com.example.model

import android.content.Intent
import android.net.Uri

enum class MessagingSendProtocol(val label: String, val shortBadge: String) {
    AUTO("Auto (SMS/RCS)", "AUTO"),
    SMS("SMS", "SMS"),
    MMS("MMS", "MMS"),
    RCS("RCS Chat", "RCS")
}

enum class MessageFolderFilter(val label: String) {
    INBOX("Inbox"),
    ARCHIVED("Archived"),
    SPAM("Spam")
}

data class ComposeAttachment(
    val uriString: String,
    val mimeType: String = "image/*",
    val displayName: String = "Attached image",
    val sizeBytes: Long = 0L
)

data class ConversationThreadSummary(
    val threadKey: String,
    val threadId: Long,
    val address: String,
    val displayName: String,
    val contactId: String?,
    val lastMessage: SmsMessageItem,
    val messages: List<SmsMessageItem>,
    val unreadCount: Int,
    val protocol: MessageProtocol,
    val hasAttachments: Boolean,
    val hasCleanableClutter: Boolean,
    val isArchived: Boolean = false,
    val isSpam: Boolean = false,
    val folderSource: String = ""
) {
    val messageCount: Int get() = messages.size
    val isUnread: Boolean get() = unreadCount > 0
}

object MessagingHelper {

    fun threadKeyFor(msg: SmsMessageItem): String {
        if (msg.threadId > 0L) {
            return "tid:${msg.threadId}"
        }
        val key = PhoneNormalizer.primaryKey(msg.address)
        return "addr:$key"
    }

    fun threadKeyForRecipient(
        recipient: String,
        existingThreads: List<ConversationThreadSummary>
    ): String {
        val trimmed = recipient.trim()
        val key = PhoneNormalizer.primaryKey(trimmed)
        val matched = existingThreads.firstOrNull { thread ->
            PhoneNormalizer.primaryKey(thread.address) == key ||
                thread.address.equals(trimmed, ignoreCase = true) ||
                thread.displayName.equals(trimmed, ignoreCase = true)
        }
        return matched?.threadKey ?: "addr:$key"
    }

    /**
     * Groups raw/classified messages into conversation threads ordered newest-first,
     * with per-thread messages sorted chronologically (oldest -> newest) for chat rendering.
     * Supports filtering by folder (Inbox, Archived, Spam), protocol, and live search query.
     */
    fun buildConversationThreads(
        messages: List<SmsMessageItem>,
        searchQuery: String = "",
        protocolFilter: MessageProtocol? = null,
        folderFilter: MessageFolderFilter? = MessageFolderFilter.INBOX
    ): List<ConversationThreadSummary> {
        if (messages.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<String, MutableList<SmsMessageItem>>()
        for (msg in messages) {
            if (msg.isQuarantined) continue
            val key = threadKeyFor(msg)
            grouped.getOrPut(key) { ArrayList() }.add(msg)
        }

        val summaries = grouped.map { (key, list) ->
            val chronological = list.sortedWith(
                compareBy<SmsMessageItem> { it.date }.thenBy { it.id }
            )
            val latest = chronological.last()
            val primaryParticipant = chronological.firstOrNull { !it.contactName.isNullOrBlank() } ?: latest
            val address = chronological.firstOrNull { it.address.isNotBlank() && it.address != "Unknown" }?.address
                ?: latest.address
            val contactName = primaryParticipant.contactName?.takeIf { it.isNotBlank() }
            val displayName = contactName ?: address
            val contactId = chronological.firstNotNullOfOrNull { it.contactId?.takeIf { id -> id.isNotBlank() } }
            val unreadCount = chronological.count { it.type == 1 && !it.isRead }
            val hasAttachments = chronological.any { !it.attachmentUri.isNullOrBlank() || it.protocol == MessageProtocol.MMS }
            val protocol = when {
                latest.protocol == MessageProtocol.MMS || !latest.attachmentUri.isNullOrBlank() -> MessageProtocol.MMS
                chronological.any { it.protocol == MessageProtocol.RCS_CHAT || it.isRcsChat } -> MessageProtocol.RCS_CHAT
                else -> MessageProtocol.SMS
            }
            val threadId = chronological.firstOrNull { it.threadId > 0L }?.threadId ?: 0L
            val hasClutter = chronological.any { it.isCleanCandidate }
            val isExplicitOverrideNotSpam = chronological.any {
                it.folderSource == FOLDER_SOURCE_USER_OVERRIDE && !it.isSpamMarked
            }
            val isSpamThread = chronological.any { it.isSpamMarked } ||
                (!isExplicitOverrideNotSpam && chronological.all { it.category == SmsCategory.SPAM })
            val isArchivedThread = !isSpamThread && chronological.any { it.isArchived }
            val resolvedSource = chronological.firstNotNullOfOrNull {
                it.folderSource.takeIf { src -> src.isNotBlank() && src != FOLDER_SOURCE_USER_OVERRIDE }
            } ?: if (isSpamThread) FOLDER_SOURCE_SMART_SPAM else ""

            ConversationThreadSummary(
                threadKey = key,
                threadId = threadId,
                address = address,
                displayName = displayName,
                contactId = contactId,
                lastMessage = latest,
                messages = chronological,
                unreadCount = unreadCount,
                protocol = protocol,
                hasAttachments = hasAttachments,
                hasCleanableClutter = hasClutter,
                isArchived = isArchivedThread,
                isSpam = isSpamThread,
                folderSource = resolvedSource
            )
        }.sortedByDescending { it.lastMessage.date }

        val folderFiltered = when (folderFilter) {
            MessageFolderFilter.INBOX -> summaries.filter { !it.isArchived && !it.isSpam }
            MessageFolderFilter.ARCHIVED -> summaries.filter { it.isArchived && !it.isSpam }
            MessageFolderFilter.SPAM -> summaries.filter { it.isSpam }
            null -> summaries
        }

        val protocolFiltered = if (protocolFilter == null) {
            folderFiltered
        } else {
            folderFiltered.filter { thread ->
                thread.protocol == protocolFilter ||
                    thread.messages.any { it.protocol == protocolFilter }
            }
        }

        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) return protocolFiltered

        val queryDigits = PhoneNormalizer.digitsOnly(query)
        return protocolFiltered.filter { thread ->
            thread.displayName.lowercase().contains(query) ||
                thread.address.lowercase().contains(query) ||
                (queryDigits.isNotEmpty() && PhoneNormalizer.digitsOnly(thread.address).contains(queryDigits)) ||
                thread.messages.any { msg ->
                    msg.body.lowercase().contains(query) ||
                        (msg.mmsSubject?.lowercase()?.contains(query) == true)
                }
        }
    }

    const val FOLDER_SOURCE_USER_OVERRIDE = "CLEANSMS_USER_OVERRIDE"
    const val FOLDER_SOURCE_SMART_SPAM = "SMART_SPAM"
    const val FOLDER_SOURCE_PROVIDER_ARCHIVED = "PROVIDER_ARCHIVED"
    const val FOLDER_SOURCE_PROVIDER_SPAM = "PROVIDER_SPAM"
    const val FOLDER_SOURCE_BLOCKED_NUMBER = "BLOCKED_NUMBER"

    /**
     * Parses external sms:, smsto:, mms:, mmsto: ACTION_SEND / ACTION_SENDTO intents.
     * Returns Pair(recipient, optionalPrefillBody) or null if the intent does not carry a recipient.
     */
    fun parseSendIntent(intent: Intent?): Pair<String, String?>? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SENDTO) return null

        val data: Uri? = intent.data
        val bodyExtra = intent.getStringExtra("sms_body")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)

        val scheme = data?.scheme?.lowercase()
        val rawSchemePart = if (scheme in setOf("sms", "smsto", "mms", "mmsto")) {
            data?.schemeSpecificPart.orEmpty()
        } else {
            intent.getStringExtra("address").orEmpty()
        }

        val cleanedRecipient = rawSchemePart
            .substringBefore("?")
            .substringBefore(";")
            .substringBefore(",")
            .trim()

        val uriQueryBody = if (rawSchemePart.contains("body=", ignoreCase = true)) {
            runCatching {
                Uri.parse("https://dummy.local/?${rawSchemePart.substringAfter("?")}").getQueryParameter("body")
            }.getOrNull()
        } else {
            data?.takeIf { it.isHierarchical }?.getQueryParameter("body")
        }

        val finalBody = (bodyExtra ?: uriQueryBody)?.takeIf { it.isNotBlank() }
        if (cleanedRecipient.isBlank() && finalBody == null) return null
        return cleanedRecipient to finalBody
    }
}
