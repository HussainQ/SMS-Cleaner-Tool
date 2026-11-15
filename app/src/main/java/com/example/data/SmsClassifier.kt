package com.example.data

import com.example.model.ContactFilterMode
import com.example.model.CustomFilterRule
import com.example.model.DuplicateKeepStrategy
import com.example.model.DuplicateThreadCluster
import com.example.model.MessageProtocol
import com.example.model.PhoneNormalizer
import com.example.model.RcsDeliveryStatus
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import java.util.regex.Pattern

data class ClassificationSummary(
    val allCleanableCount: Int = 0,
    val duplicateCount: Int = 0,
    val otpCount: Int = 0,
    val promoCount: Int = 0,
    val spamCount: Int = 0,
    val customRuleCount: Int = 0
) {
    fun countFor(category: SmsCategory): Int = when (category) {
        SmsCategory.ALL -> allCleanableCount
        SmsCategory.DUPLICATE -> duplicateCount
        SmsCategory.OTP -> otpCount
        SmsCategory.PROMOTION -> promoCount
        SmsCategory.SPAM -> spamCount
        SmsCategory.CUSTOM_RULE -> customRuleCount
        SmsCategory.NORMAL -> 0
    }
}

object SmsClassifier {

    private val OTP_REGEX = Pattern.compile("(?i)(?:otp|code|pin|password|verification|auth|secret)[^0-9]{0,10}([0-9]{4,8})")
    private val STANDALONE_DIGITS = Pattern.compile("\\b([0-9]{4,8})\\b")
    private val ALPHANUMERIC_SENDER_REGEX = Regex("^[A-Za-z0-9_-]+$")
    private val PHONE_DIGITS_REGEX = Regex("^\\+?[0-9]+$")

    private val PROMO_KEYWORDS = listOf(
        "discount", "offer", "sale", "coupon", "flat 50%", "cashback", "promo code",
        "limited time", "save up to", "exclusive deal", "shop now", "free delivery",
        "hurry", "voucher", "festive sale", "clearance", "order now and get"
    )

    private val SPAM_PHRASES = listOf(
        "account suspended", "account locked", "verify immediately", "kyc expired",
        "congratulations you won", "claim your prize", "lottery", "unauthorized debit",
        "card is blocked", "click here to unlock", "wire transfer", "crypto investment",
        "redelivery fee", "security alert: verify"
    )

    private val SUSPICIOUS_DOMAINS = listOf(
        "bit.ly", "tinyurl", "shorturl", "cutt.ly", ".xyz", ".top", ".buzz", ".work"
    )

    private class CompiledRule(
        val rule: CustomFilterRule,
        val senderLower: String,
        val contactTokens: Set<String>,
        val keywordsLower: List<String>,
        val minAgeMillis: Long
    )

    /**
     * Fast single-pass whitespace normalizer (avoids compiling/evaluating Regex per message).
     */
    private fun normalizeWhitespaceLower(input: String): String {
        val len = input.length
        if (len == 0) return ""
        val sb = StringBuilder(len)
        var lastWasSpace = true
        for (i in 0 until len) {
            val ch = input[i]
            if (ch.isWhitespace()) {
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else {
                sb.append(ch.lowercaseChar())
                lastWasSpace = false
            }
        }
        val outLen = sb.length
        if (outLen > 0 && sb[outLen - 1] == ' ') {
            sb.setLength(outLen - 1)
        }
        return sb.toString()
    }

    private fun compileRules(activeRules: List<CustomFilterRule>): List<CompiledRule> {
        if (activeRules.isEmpty()) return emptyList()
        val compiled = ArrayList<CompiledRule>(activeRules.size)
        for (rule in activeRules) {
            if (!rule.isEnabled) continue
            val contactTokens = if (rule.contactFilter.isNotBlank()) {
                parseContactFilterTokens(rule.contactFilter)
            } else {
                emptySet()
            }
            val keywordsLower = if (rule.keywords.isNotBlank()) {
                rule.keywords.split(",").mapNotNull { part ->
                    val trimmed = part.trim().lowercase()
                    trimmed.takeIf { it.isNotEmpty() }
                }
            } else {
                emptyList()
            }
            val minAgeMillis = if (rule.olderThanDays != null && rule.olderThanDays > 0) {
                rule.olderThanDays * 86400000L
            } else {
                0L
            }
            compiled.add(
                CompiledRule(
                    rule = rule,
                    senderLower = rule.senderFilter.trim().lowercase(),
                    contactTokens = contactTokens,
                    keywordsLower = keywordsLower,
                    minAgeMillis = minAgeMillis
                )
            )
        }
        return compiled
    }

    fun parseContactFilterTokens(rawFilter: String): Set<String> {
        if (rawFilter.isBlank()) return emptySet()
        val tokens = HashSet<String>()
        for (part in rawFilter.split(",")) {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                tokens.addAll(PhoneNormalizer.extractMatchKeys(trimmed))
            }
        }
        return tokens
    }

    /**
     * Checks if a message matches a set of contact filter tokens (by contactId, contactName,
     * raw address, or normalized phone digits).
     */
    fun matchesContactTokens(msg: SmsMessageItem, tokens: Set<String>): Boolean {
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

    /**
     * Processes raw SMS and RCS messages on a background-friendly path with precompiled rules
     * and zero per-message regex allocations.
     */
    fun classifyMessages(
        rawMessages: List<SmsMessageItem>,
        activeRules: List<CustomFilterRule>,
        filterDuplicates: Boolean = true,
        filterOtps: Boolean = true,
        onlyExpiredOtps: Boolean = true,
        filterPromotions: Boolean = true,
        filterSpam: Boolean = true,
        duplicateKeepStrategy: DuplicateKeepStrategy = DuplicateKeepStrategy.KEEP_OLDEST,
        filterByContactEnabled: Boolean = false,
        selectedContactTokens: Set<String> = emptySet(),
        contactFilterMode: ContactFilterMode = ContactFilterMode.TARGET_CONTACTS
    ): List<SmsMessageItem> {
        if (rawMessages.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val compiledRules = compileRules(activeRules)
        val hasGlobalContactFilter = filterByContactEnabled && selectedContactTokens.isNotEmpty()
        // Collect all conversation threadIds that belong to any filtered contact so rules
        // run across every thread belonging to those contacts (e.g. SMS + RCS threads).
        val matchedContactThreadIds = if (hasGlobalContactFilter) {
            val threadIds = HashSet<Long>()
            for (msg in rawMessages) {
                if (msg.threadId > 0L && matchesContactTokens(msg, selectedContactTokens)) {
                    threadIds.add(msg.threadId)
                }
            }
            threadIds
        } else {
            emptySet()
        }

        // Step 1: Group duplicates in O(N) using normalized whitespace without Regex allocation
        val normalizedGroups = HashMap<String, MutableList<SmsMessageItem>>(rawMessages.size)

        for (msg in rawMessages) {
            val threadPrefix = if (msg.threadId > 0) "thread_${msg.threadId}:::" else ""
            val addrNorm = msg.address.trim().lowercase()
            val bodyNorm = normalizeWhitespaceLower(msg.body)
            val key = "$threadPrefix$addrNorm:::$bodyNorm"
            normalizedGroups.getOrPut(key) { ArrayList(2) }.add(msg)
        }

        val resultList = ArrayList<SmsMessageItem>(rawMessages.size)
        val keepOldest = duplicateKeepStrategy == DuplicateKeepStrategy.KEEP_OLDEST

        for ((groupId, groupItems) in normalizedGroups) {
            val sorted = if (groupItems.size == 1) {
                groupItems
            } else if (keepOldest) {
                groupItems.sortedWith(
                    compareByDescending<SmsMessageItem> { it.rcsDeliveryStatus == RcsDeliveryStatus.READ }
                        .thenByDescending { it.rcsDeliveryStatus == RcsDeliveryStatus.DELIVERED }
                        .thenBy { it.date }
                )
            } else {
                groupItems.sortedWith(
                    compareByDescending<SmsMessageItem> { it.rcsDeliveryStatus == RcsDeliveryStatus.READ }
                        .thenByDescending { it.rcsDeliveryStatus == RcsDeliveryStatus.DELIVERED }
                        .thenByDescending { it.date }
                )
            }
            val totalInGroup = sorted.size

            sorted.forEachIndexed { index, msg ->
                val isDuplicate = totalInGroup > 1 && index > 0
                val matchesGlobalContact = hasGlobalContactFilter && (
                    matchesContactTokens(msg, selectedContactTokens) ||
                        (msg.threadId > 0L && msg.threadId in matchedContactThreadIds)
                    )

                // If global Per-Contact Filter is in PROTECT (VIP) mode, keep matched contact safe
                if (matchesGlobalContact && contactFilterMode == ContactFilterMode.PROTECT_CONTACTS) {
                    resultList.add(
                        msg.copy(
                            category = SmsCategory.NORMAL,
                            duplicateGroupId = null,
                            duplicateIndex = 1,
                            totalInDuplicateGroup = 1,
                            isDuplicateExtra = false,
                            matchReason = "Protected contact: ${msg.contactName ?: msg.address}",
                            otpCode = null,
                            isExpiredOtp = false,
                            matchedRuleName = null,
                            isSelected = false
                        )
                    )
                    return@forEachIndexed
                }

                var category = SmsCategory.NORMAL
                var matchReason = ""
                var otpCode: String? = null
                var isExpiredOtp = false
                var matchedRuleName: String? = null

                // In TARGET_CONTACTS mode, run active rules strictly on threads belonging to
                // the selected contacts rather than deleting all messages for those contacts outright.
                val allowRulesForMessage = !hasGlobalContactFilter ||
                    contactFilterMode != ContactFilterMode.TARGET_CONTACTS ||
                    matchesGlobalContact

                val isActiveDuplicate = allowRulesForMessage && isDuplicate && filterDuplicates

                if (isActiveDuplicate) {
                    category = SmsCategory.DUPLICATE
                    matchReason = if (msg.isRcsChat || msg.protocol == MessageProtocol.RCS_CHAT) {
                        "RCS Duplicate in Thread #${msg.threadId}: Copy #${index + 1} of $totalInGroup (Master kept)"
                    } else if (msg.threadId > 0) {
                        "Duplicate in Thread #${msg.threadId}: Copy #${index + 1} of $totalInGroup from ${msg.displaySender}"
                    } else {
                        "Duplicate: Identical copy #${index + 1} of $totalInGroup from ${msg.displaySender}"
                    }
                } else if (allowRulesForMessage) {
                    val bodyLower = msg.body.lowercase()
                    val matchedRule = compiledRules.firstOrNull { compiled ->
                        matchesCompiledRule(msg, bodyLower, compiled, now)
                    }

                    if (matchedRule != null) {
                        category = SmsCategory.CUSTOM_RULE
                        matchedRuleName = matchedRule.rule.name
                        matchReason = "Matched custom rule: '${matchedRule.rule.name}'"
                    } else if (filterSpam && isSpamMessageLower(bodyLower)) {
                        category = SmsCategory.SPAM
                        matchReason = "Spam / Phishing: Contains urgent threat or suspicious link"
                    } else if (filterOtps && isOtpMessageLower(msg.body, bodyLower)) {
                        val code = extractOtpCode(msg.body)
                        otpCode = code
                        val ageMillis = now - msg.date
                        isExpiredOtp = ageMillis > (15 * 60 * 1000L)

                        if (!onlyExpiredOtps || isExpiredOtp) {
                            category = SmsCategory.OTP
                            val ageStr = formatAge(ageMillis)
                            matchReason = if (isExpiredOtp) {
                                "Expired OTP ($code): Received $ageStr ago"
                            } else {
                                "Active 2FA code ($code)"
                            }
                        }
                    } else if (filterPromotions && isPromotionalMessageLower(msg.address, bodyLower)) {
                        category = SmsCategory.PROMOTION
                        matchReason = "Promotional offer / Commercial marketing campaign"
                    }
                }

                val trackDuplicateMetadata = allowRulesForMessage && filterDuplicates && totalInGroup > 1
                resultList.add(
                    msg.copy(
                        category = category,
                        duplicateGroupId = if (trackDuplicateMetadata) groupId else null,
                        duplicateIndex = if (trackDuplicateMetadata) index + 1 else 1,
                        totalInDuplicateGroup = if (trackDuplicateMetadata) totalInGroup else 1,
                        isDuplicateExtra = isActiveDuplicate,
                        matchReason = matchReason,
                        otpCode = otpCode,
                        isExpiredOtp = isExpiredOtp,
                        matchedRuleName = matchedRuleName,
                        isSelected = category != SmsCategory.NORMAL
                    )
                )
            }
        }

        // Return sorted by date descending (most recent first)
        return resultList.sortedByDescending { it.date }
    }

    /**
     * Computes all category counts in a single O(N) pass so Compose UI avoids repeated scans.
     */
    fun computeSummary(classifiedMessages: List<SmsMessageItem>): ClassificationSummary {
        var dup = 0
        var otp = 0
        var promo = 0
        var spam = 0
        var custom = 0
        for (msg in classifiedMessages) {
            if (!msg.isCleanCandidate) continue
            when (msg.category) {
                SmsCategory.DUPLICATE -> dup++
                SmsCategory.OTP -> otp++
                SmsCategory.PROMOTION -> promo++
                SmsCategory.SPAM -> spam++
                SmsCategory.CUSTOM_RULE -> custom++
                else -> Unit
            }
        }
        return ClassificationSummary(
            allCleanableCount = dup + otp + promo + spam + custom,
            duplicateCount = dup,
            otpCount = otp,
            promoCount = promo,
            spamCount = spam,
            customRuleCount = custom
        )
    }

    /**
     * Extracts clusters of duplicate messages grouped by conversation thread.
     */
    fun extractDuplicateThreadClusters(classifiedMessages: List<SmsMessageItem>): List<DuplicateThreadCluster> {
        val byGroup = HashMap<String, MutableList<SmsMessageItem>>()
        for (msg in classifiedMessages) {
            val gid = msg.duplicateGroupId ?: continue
            byGroup.getOrPut(gid) { ArrayList(2) }.add(msg)
        }
        if (byGroup.isEmpty()) return emptyList()

        val clusters = ArrayList<DuplicateThreadCluster>(byGroup.size)
        for ((groupId, items) in byGroup) {
            var master: SmsMessageItem? = null
            val duplicates = ArrayList<SmsMessageItem>(items.size)
            for (item in items) {
                if (!item.isDuplicateExtra && master == null) {
                    master = item
                } else if (item.isDuplicateExtra) {
                    duplicates.add(item)
                }
            }
            val resolvedMaster = master ?: items.first()
            if (duplicates.isNotEmpty()) {
                clusters.add(
                    DuplicateThreadCluster(
                        groupId = groupId,
                        threadId = resolvedMaster.threadId,
                        sender = resolvedMaster.displaySender,
                        snippet = resolvedMaster.body,
                        masterMessage = resolvedMaster,
                        duplicateCopies = duplicates,
                        isRcs = resolvedMaster.isRcsChat || resolvedMaster.protocol == MessageProtocol.RCS_CHAT
                    )
                )
            }
        }
        return clusters.sortedByDescending { it.duplicateCount }
    }

    /**
     * Evaluates how many messages match a rule (used for live rule preview without blocking UI).
     */
    fun countRuleMatches(
        messages: List<SmsMessageItem>,
        rule: CustomFilterRule,
        now: Long = System.currentTimeMillis()
    ): Int {
        val compiled = compileRules(listOf(rule.copy(isEnabled = true))).firstOrNull() ?: return 0
        if (compiled.senderLower.isEmpty() &&
            compiled.contactTokens.isEmpty() &&
            compiled.keywordsLower.isEmpty() &&
            compiled.minAgeMillis <= 0L &&
            rule.startDateEpoch == null &&
            rule.endDateEpoch == null
        ) {
            return 0
        }
        var count = 0
        for (msg in messages) {
            if (matchesCompiledRule(msg, msg.body.lowercase(), compiled, now)) {
                count++
            }
        }
        return count
    }

    private fun matchesCompiledRule(
        msg: SmsMessageItem,
        bodyLower: String,
        compiled: CompiledRule,
        now: Long
    ): Boolean {
        val rule = compiled.rule

        // Per-Contact filter check
        if (compiled.contactTokens.isNotEmpty()) {
            if (!matchesContactTokens(msg, compiled.contactTokens)) {
                return false
            }
        }

        // Sender check (matches address or linked contact name)
        if (compiled.senderLower.isNotEmpty()) {
            val matchesAddress = msg.address.contains(compiled.senderLower, ignoreCase = true)
            val matchesContactName = msg.contactName?.contains(compiled.senderLower, ignoreCase = true) == true
            if (!matchesAddress && !matchesContactName) {
                return false
            }
        }

        // Keywords check
        if (compiled.keywordsLower.isNotEmpty()) {
            val matchesKeywords = if (rule.matchAnyKeyword) {
                compiled.keywordsLower.any { bodyLower.contains(it) }
            } else {
                compiled.keywordsLower.all { bodyLower.contains(it) }
            }
            if (!matchesKeywords) return false
        }

        // Age check (older than X days)
        if (compiled.minAgeMillis > 0L) {
            if ((now - msg.date) < compiled.minAgeMillis) {
                return false
            }
        }

        // Date range check
        if (rule.startDateEpoch != null && msg.date < rule.startDateEpoch) {
            return false
        }
        if (rule.endDateEpoch != null && msg.date > rule.endDateEpoch) {
            return false
        }

        return true
    }

    private fun isOtpMessageLower(body: String, lower: String): Boolean {
        val hasOtpKeyword = lower.contains("otp") ||
                lower.contains("pin") ||
                lower.contains("verification code") ||
                lower.contains("security code") ||
                lower.contains("is your code") ||
                lower.contains("login code") ||
                lower.contains("auth code") ||
                lower.contains("one-time") ||
                lower.contains("secret code") ||
                (lower.contains("code") && lower.contains("do not share"))

        return hasOtpKeyword && (OTP_REGEX.matcher(body).find() || STANDALONE_DIGITS.matcher(body).find())
    }

    private fun extractOtpCode(body: String): String {
        val m = OTP_REGEX.matcher(body)
        if (m.find()) {
            return m.group(1) ?: ""
        }
        val m2 = STANDALONE_DIGITS.matcher(body)
        if (m2.find()) {
            return m2.group(1) ?: ""
        }
        return ""
    }

    private fun isPromotionalMessageLower(address: String, lower: String): Boolean {
        val isAlphanumericSender = address.length in 5..9 &&
                ALPHANUMERIC_SENDER_REGEX.matches(address) &&
                !PHONE_DIGITS_REGEX.matches(address)

        val hasPromoWord = PROMO_KEYWORDS.any { lower.contains(it) }
        val hasDealLink = lower.contains("http") || lower.contains(".com/") || lower.contains(".in/") || lower.contains(".co/")

        return (hasPromoWord && (isAlphanumericSender || hasDealLink)) ||
                (isAlphanumericSender && (lower.contains("off") || lower.contains("sale") || lower.contains("coupon") || lower.contains("order now")))
    }

    private fun isSpamMessageLower(lower: String): Boolean {
        val hasSpamPhrase = SPAM_PHRASES.any { lower.contains(it) }
        val hasSuspiciousUrl = SUSPICIOUS_DOMAINS.any { lower.contains(it) }
        return hasSpamPhrase || (lower.contains("urgent") && hasSuspiciousUrl) || (lower.contains("verify") && hasSuspiciousUrl)
    }

    private fun formatAge(millis: Long): String {
        val minutes = millis / (60 * 1000)
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "$days days"
            hours > 0 -> "$hours hrs"
            else -> "$minutes mins"
        }
    }
}
