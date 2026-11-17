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

    fun digitsOnly(raw: String): String {
        val len = raw.length
        if (len == 0) return ""
        var allDigits = true
        for (i in 0 until len) {
            val ch = raw[i]
            if (ch < '0' || ch > '9') {
                allDigits = false
                break
            }
        }
        if (allDigits) return raw
        val sb = StringBuilder(len)
        for (i in 0 until len) {
            val ch = raw[i]
            if (ch in '0'..'9') {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun extractMatchKeys(raw: String): Set<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptySet()
        val lower = trimmed.lowercase()
        val digits = digitsOnly(trimmed)
        return buildSet(4) {
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
        val tokens = HashSet<String>(contacts.size * 4)
        for (c in contacts) {
            val idLower = c.id.trim().lowercase()
            if (idLower.isNotEmpty()) tokens.add(idLower)
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

        val contactIds = selectedContacts.mapTo(HashSet(selectedContacts.size)) { it.id }
        val tokens = collectTokensForContacts(selectedContacts)
        val threadIds = HashSet<Long>()
        for (contact in selectedContacts) {
            contact.threadId?.takeIf { it > 0L }?.let { threadIds.add(it) }
        }
        for (msg in messages) {
            if (msg.threadId > 0L && msg.threadId !in threadIds && messageMatchesTokens(msg, tokens)) {
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
     * Builds checklist entries with live message/thread counts in O(C + M) via inverted token maps.
     */
    fun buildEntriesForInbox(
        contacts: List<ContactInfo>,
        messages: List<SmsMessageItem>
    ): List<ContactThreadEntry> {
        if (contacts.isEmpty()) return emptyList()

        val contactCount = contacts.size
        val tokenToContacts = HashMap<String, MutableList<Int>>(contactCount * 4)
        val threadToContacts = HashMap<Long, MutableList<Int>>()
        val counts = IntArray(contactCount)
        val threadSets = Array<HashSet<Long>>(contactCount) { idx ->
            HashSet<Long>(2).also { set ->
                contacts[idx].threadId?.takeIf { it > 0L }?.let { set.add(it) }
            }
        }

        for (i in 0 until contactCount) {
            val contact = contacts[i]
            val idLower = contact.id.trim().lowercase()
            if (idLower.isNotEmpty()) {
                tokenToContacts.getOrPut(idLower) { ArrayList(1) }.add(i)
            }
            val nameLower = contact.displayName.trim().lowercase()
            if (nameLower.isNotEmpty()) {
                tokenToContacts.getOrPut(nameLower) { ArrayList(1) }.add(i)
            }
            for (phone in contact.phoneNumbers) {
                for (key in PhoneNormalizer.extractMatchKeys(phone)) {
                    tokenToContacts.getOrPut(key) { ArrayList(1) }.add(i)
                }
            }
            contact.threadId?.let { tid ->
                threadToContacts.getOrPut(tid) { ArrayList(1) }.add(i)
            }
        }

        val seenStamp = IntArray(contactCount)
        var currentStamp = 0

        for (msg in messages) {
            currentStamp++
            fun recordMatches(list: List<Int>?) {
                if (list == null) return
                for (k in 0 until list.size) {
                    val idx = list[k]
                    if (seenStamp[idx] != currentStamp) {
                        seenStamp[idx] = currentStamp
                        counts[idx]++
                        if (msg.threadId > 0L) {
                            threadSets[idx].add(msg.threadId)
                        }
                    }
                }
            }

            msg.contactId?.trim()?.lowercase()?.let { recordMatches(tokenToContacts[it]) }
            msg.contactName?.trim()?.lowercase()?.let { recordMatches(tokenToContacts[it]) }
            val addrLower = msg.address.trim().lowercase()
            recordMatches(tokenToContacts[addrLower])
            val pKey = PhoneNormalizer.primaryKey(msg.address)
            if (pKey != addrLower) {
                recordMatches(tokenToContacts[pKey])
            }
            recordMatches(threadToContacts[msg.threadId])
        }

        val entries = ArrayList<ContactThreadEntry>(contactCount)
        for (i in 0 until contactCount) {
            entries.add(
                ContactThreadEntry(
                    contact = contacts[i],
                    messageCount = counts[i],
                    threadIds = threadSets[i]
                )
            )
        }

        return entries.sortedWith(
            compareByDescending<ContactThreadEntry> { it.messageCount > 0 }
                .thenByDescending { it.messageCount }
                .thenBy { it.contact.displayName.lowercase() }
        )
    }

    /**
     * Extracts contact groups specifically present inside Vault (Quarantine + Deleted Test snapshots)
     * in O(M + C) so users can filter and review quarantined/deleted messages by contact group.
     */
    fun buildVaultContactEntries(
        vaultMessages: List<SmsMessageItem>,
        availableContacts: List<ContactInfo>
    ): List<ContactThreadEntry> {
        if (vaultMessages.isEmpty()) return emptyList()

        val tokenToContact = if (availableContacts.isNotEmpty()) {
            HashMap<String, ContactInfo>(availableContacts.size * 4).apply {
                for (contact in availableContacts) {
                    val idLower = contact.id.trim().lowercase()
                    if (idLower.isNotEmpty()) putIfAbsent(idLower, contact)
                    val nameLower = contact.displayName.trim().lowercase()
                    if (nameLower.isNotEmpty()) putIfAbsent(nameLower, contact)
                    for (phone in contact.phoneNumbers) {
                        for (key in PhoneNormalizer.extractMatchKeys(phone)) {
                            putIfAbsent(key, contact)
                        }
                    }
                }
            }
        } else {
            emptyMap()
        }

        val grouped = LinkedHashMap<String, MutableList<SmsMessageItem>>()
        for (msg in vaultMessages) {
            val key = vaultGroupKey(msg)
            grouped.getOrPut(key) { ArrayList() }.add(msg)
        }

        return grouped.map { (key, msgs) ->
            val sample = msgs.first()
            val matchedAvailable = if (tokenToContact.isEmpty()) {
                null
            } else {
                sample.contactId?.trim()?.lowercase()?.let { tokenToContact[it] }
                    ?: sample.contactName?.trim()?.lowercase()?.let { tokenToContact[it] }
                    ?: tokenToContact[sample.address.trim().lowercase()]
                    ?: tokenToContact[PhoneNormalizer.primaryKey(sample.address)]
            }
            val contact = matchedAvailable ?: ContactInfo(
                id = "vault_$key",
                displayName = sample.displaySender,
                phoneNumbers = listOf(sample.address).distinct(),
                isFromDeviceContacts = !sample.contactName.isNullOrBlank(),
                threadId = sample.threadId.takeIf { it > 0L }
            )
            val threads = msgs.mapNotNullTo(HashSet()) { it.threadId.takeIf { tid -> tid > 0L } }
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

