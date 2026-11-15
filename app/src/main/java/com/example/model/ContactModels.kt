package com.example.model

enum class ContactFilterMode(val displayName: String, val description: String) {
    TARGET_CONTACTS(
        displayName = "Target Selected",
        description = "Apply active cleaning rules only to selected contacts' threads"
    ),
    PROTECT_CONTACTS(
        displayName = "Protect (VIP)",
        description = "Exclude selected contacts from all cleaning filters"
    )
}

data class ContactInfo(
    val id: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val isFromDeviceContacts: Boolean = false,
    val threadId: Long? = null
) {
    val primaryNumber: String
        get() = phoneNumbers.firstOrNull() ?: displayName

    val normalizedTokens: Set<String> by lazy {
        buildSet {
            add(displayName.trim().lowercase())
            for (phone in phoneNumbers) {
                addAll(PhoneNormalizer.extractMatchKeys(phone))
            }
        }
    }
}

object PhoneNormalizer {
    private val NON_DIGIT_REGEX = Regex("[^0-9]")

    fun digitsOnly(raw: String): String {
        return NON_DIGIT_REGEX.replace(raw, "")
    }

    fun extractMatchKeys(raw: String): Set<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptySet()
        val lower = trimmed.lowercase()
        val digits = digitsOnly(trimmed)
        return buildSet {
            add(lower)
            if (digits.isNotEmpty()) {
                add(digits)
                if (digits.length >= 10) {
                    add(digits.takeLast(10))
                } else if (digits.length >= 7) {
                    add(digits.takeLast(7))
                }
            }
        }
    }

    fun primaryKey(raw: String): String {
        val digits = digitsOnly(raw)
        return when {
            digits.length >= 10 -> digits.takeLast(10)
            digits.isNotEmpty() -> digits
            else -> raw.trim().lowercase()
        }
    }
}

/**
 * Structured scope model capturing selected contacts, normalized tokens, and resolved conversation
 * thread IDs so deduplication and filtering run strictly inside selected contact threads.
 */
data class ContactThreadFilterScope(
    val enabled: Boolean = false,
    val mode: ContactFilterMode = ContactFilterMode.TARGET_CONTACTS,
    val selectedContactIds: Set<String> = emptySet(),
    val selectedThreadIds: Set<Long> = emptySet(),
    val matchTokens: Set<String> = emptySet()
) {
    val isActive: Boolean
        get() = enabled && (
            selectedContactIds.isNotEmpty() ||
                selectedThreadIds.isNotEmpty() ||
                matchTokens.isNotEmpty()
        )
}

/**
 * Represents a selectable contact entry in the Filter By Contact checklist UI (both Inbox & Vault).
 */
data class ContactThreadEntry(
    val contact: ContactInfo,
    val messageCount: Int = 0,
    val threadIds: Set<Long> = emptySet()
)

object ContactThreadHelper {

    fun collectTokensForContacts(contacts: List<ContactInfo>): Set<String> {
        if (contacts.isEmpty()) return emptySet()
        val tokens = HashSet<String>()
        for (c in contacts) {
            tokens.add(c.id.trim().lowercase())
            val nameLower = c.displayName.trim().lowercase()
            if (nameLower.isNotEmpty()) tokens.add(nameLower)
            for (phone in c.phoneNumbers) {
                tokens.addAll(PhoneNormalizer.extractMatchKeys(phone))
            }
        }
        return tokens
    }

    fun messageMatchesTokens(msg: SmsMessageItem, tokens: Set<String>): Boolean {
        if (tokens.isEmpty()) return false
        val contactId = msg.contactId?.trim()?.lowercase()
        if (contactId != null && contactId in tokens) return true

        val contactName = msg.contactName?.trim()?.lowercase()
        if (contactName != null && contactName in tokens) return true

        val addressLower = msg.address.trim().lowercase()
        if (addressLower in tokens) return true

        val primaryKey = PhoneNormalizer.primaryKey(msg.address)
        if (primaryKey in tokens) return true

        return false
    }

    fun buildScope(
        enabled: Boolean,
        mode: ContactFilterMode,
        selectedContacts: List<ContactInfo>,
        messages: List<SmsMessageItem>
    ): ContactThreadFilterScope {
        if (!enabled || selectedContacts.isEmpty()) {
            return ContactThreadFilterScope(
                enabled = enabled,
                mode = mode
            )
        }

        val contactIds = selectedContacts.mapTo(HashSet()) { it.id }
        val tokens = collectTokensForContacts(selectedContacts)
        val threadIds = HashSet<Long>()
        for (contact in selectedContacts) {
            contact.threadId?.takeIf { it > 0L }?.let { threadIds.add(it) }
        }
        for (msg in messages) {
            if (msg.threadId > 0L && messageMatchesTokens(msg, tokens)) {
                threadIds.add(msg.threadId)
            }
        }

        return ContactThreadFilterScope(
            enabled = true,
            mode = mode,
            selectedContactIds = contactIds,
            selectedThreadIds = threadIds,
            matchTokens = tokens
        )
    }

    /**
     * Builds checklist entries with live message/thread counts for a contact list & message pool.
     */
    fun buildEntriesForInbox(
        contacts: List<ContactInfo>,
        messages: List<SmsMessageItem>
    ): List<ContactThreadEntry> {
        if (contacts.isEmpty()) return emptyList()
        return contacts.map { contact ->
            val tokens = collectTokensForContacts(listOf(contact))
            var count = 0
            val threads = HashSet<Long>()
            contact.threadId?.takeIf { it > 0L }?.let { threads.add(it) }
            for (msg in messages) {
                if (messageMatchesTokens(msg, tokens) || (contact.threadId != null && msg.threadId == contact.threadId)) {
                    count++
                    if (msg.threadId > 0L) threads.add(msg.threadId)
                }
            }
            ContactThreadEntry(
                contact = contact,
                messageCount = count,
                threadIds = threads
            )
        }.sortedWith(
            compareByDescending<ContactThreadEntry> { it.messageCount > 0 }
                .thenByDescending { it.messageCount }
                .thenBy { it.contact.displayName.lowercase() }
        )
    }

    /**
     * Extracts contact groups specifically present inside Vault (Quarantine + Deleted Test snapshots)
     * so users can filter and review quarantined/deleted messages by contact group.
     */
    fun buildVaultContactEntries(
        vaultMessages: List<SmsMessageItem>,
        availableContacts: List<ContactInfo>
    ): List<ContactThreadEntry> {
        if (vaultMessages.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<String, MutableList<SmsMessageItem>>()
        for (msg in vaultMessages) {
            val key = vaultGroupKey(msg)
            grouped.getOrPut(key) { ArrayList() }.add(msg)
        }

        return grouped.map { (key, msgs) ->
            val sample = msgs.first()
            val matchedAvailable = availableContacts.firstOrNull { contact ->
                val tokens = collectTokensForContacts(listOf(contact))
                messageMatchesTokens(sample, tokens)
            }
            val contact = matchedAvailable ?: ContactInfo(
                id = "vault_$key",
                displayName = sample.displaySender,
                phoneNumbers = listOf(sample.address).distinct(),
                isFromDeviceContacts = !sample.contactName.isNullOrBlank(),
                threadId = sample.threadId.takeIf { it > 0L }
            )
            val threads = msgs.mapNotNull { it.threadId.takeIf { tid -> tid > 0L } }.toSet()
            ContactThreadEntry(
                contact = contact,
                messageCount = msgs.size,
                threadIds = threads
            )
        }.sortedWith(
            compareByDescending<ContactThreadEntry> { it.messageCount }
                .thenBy { it.contact.displayName.lowercase() }
        )
    }

    fun vaultGroupKey(msg: SmsMessageItem): String {
        val cid = msg.contactId?.trim()?.lowercase()
        if (!cid.isNullOrBlank()) return "cid:$cid"
        val pkey = PhoneNormalizer.primaryKey(msg.address)
        return "addr:$pkey"
    }

    fun filterMessagesByContacts(
        messages: List<SmsMessageItem>,
        selectedContacts: List<ContactInfo>
    ): List<SmsMessageItem> {
        if (selectedContacts.isEmpty()) return messages
        val tokens = collectTokensForContacts(selectedContacts)
        val explicitThreadIds = HashSet<Long>()
        for (contact in selectedContacts) {
            contact.threadId?.takeIf { it > 0L }?.let { explicitThreadIds.add(it) }
        }
        for (msg in messages) {
            if (msg.threadId > 0L && messageMatchesTokens(msg, tokens)) {
                explicitThreadIds.add(msg.threadId)
            }
        }
        return messages.filter { msg ->
            messageMatchesTokens(msg, tokens) ||
                (msg.threadId > 0L && msg.threadId in explicitThreadIds)
        }
    }
}

