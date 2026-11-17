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

data class ConversationThreadsWithMetrics(
    val threads: List<ConversationThreadSummary> = emptyList(),
    val inboxUnreadCount: Int = 0,
    val archivedThreadsCount: Int = 0,
    val spamThreadsCount: Int = 0
)

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
        return buildConversationThreadsAndMetrics(
            messages = messages,
            searchQuery = searchQuery,
            protocolFilter = protocolFilter,
            folderFilter = folderFilter
        ).threads
    }

    /**
     * Single-pass thread grouping + cross-folder metrics calculation.
     * Eliminates redundant second passes when UI needs both active folder threads and folder badges.
     */
    fun buildConversationThreadsAndMetrics(
        messages: List<SmsMessageItem>,
        searchQuery: String = "",
        protocolFilter: MessageProtocol? = null,
        folderFilter: MessageFolderFilter? = MessageFolderFilter.INBOX
    ): ConversationThreadsWithMetrics {
        if (messages.isEmpty()) return ConversationThreadsWithMetrics()

        val grouped = LinkedHashMap<String, MutableList<SmsMessageItem>>(minOf(messages.size, 256))
        for (msg in messages) {
            if (msg.isQuarantined) continue
            val key = threadKeyFor(msg)
            grouped.getOrPut(key) { ArrayList(4) }.add(msg)
        }
        if (grouped.isEmpty()) return ConversationThreadsWithMetrics()

        val query = searchQuery.trim()
        val hasQuery = query.isNotEmpty()
        val queryDigits = if (hasQuery) PhoneNormalizer.digitsOnly(query) else ""

        var inboxUnread = 0
        var archivedCount = 0
        var spamCount = 0
        val matchingSummaries = ArrayList<ConversationThreadSummary>(grouped.size)
        val msgChronoComparator = compareBy<SmsMessageItem> { it.date }.thenBy { it.id }

        for ((key, list) in grouped) {
            var unreadCount = 0
            var hasExplicitOverrideNotSpam = false
            var anySpamMarked = false
            var allSpamCategory = true
            var anyArchived = false

            for (i in 0 until list.size) {
                val m = list[i]
                if (m.type == 1 && !m.isRead) unreadCount++
                if (m.folderSource == FOLDER_SOURCE_USER_OVERRIDE && !m.isSpamMarked) {
                    hasExplicitOverrideNotSpam = true
                }
                if (m.isSpamMarked) anySpamMarked = true
                if (m.category != SmsCategory.SPAM) allSpamCategory = false
                if (m.isArchived) anyArchived = true
            }

            val isSpamThread = anySpamMarked || (!hasExplicitOverrideNotSpam && allSpamCategory)
            val isArchivedThread = !isSpamThread && anyArchived

            when {
                isSpamThread -> spamCount++
                isArchivedThread -> archivedCount++
                else -> inboxUnread += unreadCount
            }

            val matchesFolder = when (folderFilter) {
                MessageFolderFilter.INBOX -> !isArchivedThread && !isSpamThread
                MessageFolderFilter.ARCHIVED -> isArchivedThread && !isSpamThread
                MessageFolderFilter.SPAM -> isSpamThread
                null -> true
            }
            if (!matchesFolder) continue

            val chronological = if (list.size <= 1) list else list.sortedWith(msgChronoComparator)
            val latest = chronological.last()
            val hasAttachments = chronological.any { !it.attachmentUri.isNullOrBlank() || it.protocol == MessageProtocol.MMS }
            val protocol = when {
                latest.protocol == MessageProtocol.MMS || !latest.attachmentUri.isNullOrBlank() -> MessageProtocol.MMS
                chronological.any { it.protocol == MessageProtocol.RCS_CHAT || it.isRcsChat } -> MessageProtocol.RCS_CHAT
                else -> MessageProtocol.SMS
            }

            if (protocolFilter != null &&
                protocol != protocolFilter &&
                chronological.none { it.protocol == protocolFilter }
            ) {
                continue
            }

            val primaryParticipant = chronological.firstOrNull { !it.contactName.isNullOrBlank() } ?: latest
            val address = chronological.firstOrNull { it.address.isNotBlank() && it.address != "Unknown" }?.address
                ?: latest.address
            val contactName = primaryParticipant.contactName?.takeIf { it.isNotBlank() }
            val displayName = contactName ?: address

            if (hasQuery) {
                val matchesSearch = displayName.contains(query, ignoreCase = true) ||
                    address.contains(query, ignoreCase = true) ||
                    (queryDigits.isNotEmpty() && PhoneNormalizer.digitsOnly(address).contains(queryDigits)) ||
                    chronological.any { msg ->
                        msg.body.contains(query, ignoreCase = true) ||
                            (msg.mmsSubject?.contains(query, ignoreCase = true) == true)
                    }
                if (!matchesSearch) continue
            }

            val contactId = chronological.firstNotNullOfOrNull { it.contactId?.takeIf { id -> id.isNotBlank() } }
            val threadId = chronological.firstOrNull { it.threadId > 0L }?.threadId ?: 0L
            val hasClutter = chronological.any { it.isCleanCandidate }
            val resolvedSource = chronological.firstNotNullOfOrNull {
                it.folderSource.takeIf { src -> src.isNotBlank() && src != FOLDER_SOURCE_USER_OVERRIDE }
            } ?: if (isSpamThread) FOLDER_SOURCE_SMART_SPAM else ""

            matchingSummaries.add(
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
            )
        }

        matchingSummaries.sortByDescending { it.lastMessage.date }
        return ConversationThreadsWithMetrics(
            threads = matchingSummaries,
            inboxUnreadCount = inboxUnread,
            archivedThreadsCount = archivedCount,
            spamThreadsCount = spamCount
        )
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
