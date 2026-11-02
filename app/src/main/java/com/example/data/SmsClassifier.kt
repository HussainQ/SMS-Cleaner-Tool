package com.example.data

import com.example.model.CustomFilterRule
import com.example.model.DuplicateKeepStrategy
import com.example.model.DuplicateThreadCluster
import com.example.model.MessageProtocol
import com.example.model.RcsDeliveryStatus
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import java.util.regex.Pattern

object SmsClassifier {

    private val OTP_REGEX = Pattern.compile("(?i)(?:otp|code|pin|password|verification|auth|secret)[^0-9]{0,10}([0-9]{4,8})")
    private val STANDALONE_DIGITS = Pattern.compile("\\b([0-9]{4,8})\\b")

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

    /**
     * Processes a list of raw SMS and RCS messages and classifies them into:
     * - Duplicates within conversation threads (keeps master copy, marks redundant retries/sync copies)
     * - Expired or All OTPs
     * - Promotions
     * - Spam & Phishing
     * - Custom Rules matches
     * - Normal
     */
    fun classifyMessages(
        rawMessages: List<SmsMessageItem>,
        activeRules: List<CustomFilterRule>,
        filterDuplicates: Boolean = true,
        filterOtps: Boolean = true,
        onlyExpiredOtps: Boolean = true,
        filterPromotions: Boolean = true,
        filterSpam: Boolean = true,
        duplicateKeepStrategy: DuplicateKeepStrategy = DuplicateKeepStrategy.KEEP_OLDEST
    ): List<SmsMessageItem> {
        val now = System.currentTimeMillis()

        // Step 1: Detect Duplicates
        // Thread-aware grouping: preserves RCS and SMS conversation thread continuity
        val normalizedGroups = mutableMapOf<String, MutableList<SmsMessageItem>>()

        for (msg in rawMessages) {
            val threadPrefix = if (msg.threadId > 0) "thread_${msg.threadId}:::" else ""
            val key = "$threadPrefix${msg.address.trim().lowercase()}:::${msg.body.trim().replace("\\s+".toRegex(), " ").lowercase()}"
            normalizedGroups.getOrPut(key) { mutableListOf() }.add(msg)
        }

        val resultList = mutableListOf<SmsMessageItem>()

        for ((groupId, groupItems) in normalizedGroups) {
            // Sort group items according to keep strategy:
            // For RCS chat messages, prioritize keeping verified READ/DELIVERED status
            val sorted = if (duplicateKeepStrategy == DuplicateKeepStrategy.KEEP_OLDEST) {
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
                val isMaster = totalInGroup > 1 && index == 0

                var category = SmsCategory.NORMAL
                var matchReason = ""
                var otpCode: String? = null
                var isExpiredOtp = false
                var matchedRuleName: String? = null

                if (isDuplicate && filterDuplicates) {
                    category = SmsCategory.DUPLICATE
                    matchReason = if (msg.isRcsChat || msg.protocol == MessageProtocol.RCS_CHAT) {
                        "RCS Duplicate in Thread #${msg.threadId}: Copy #${index + 1} of $totalInGroup (Master kept)"
                    } else if (msg.threadId > 0) {
                        "Duplicate in Thread #${msg.threadId}: Copy #${index + 1} of $totalInGroup from ${msg.address}"
                    } else {
                        "Duplicate: Identical copy #${index + 1} of $totalInGroup from ${msg.address}"
                    }
                } else {
                    // Check Custom Rules first
                    val matchedRule = activeRules.firstOrNull { rule ->
                        rule.isEnabled && matchesCustomRule(msg, rule, now)
                    }

                    if (matchedRule != null) {
                        category = SmsCategory.CUSTOM_RULE
                        matchedRuleName = matchedRule.name
                        matchReason = "Matched custom rule: '${matchedRule.name}'"
                    } else if (filterSpam && isSpamMessage(msg.body)) {
                        category = SmsCategory.SPAM
                        matchReason = "Spam / Phishing: Contains urgent threat or suspicious link"
                    } else if (filterOtps && isOtpMessage(msg.body)) {
                        val code = extractOtpCode(msg.body)
                        otpCode = code
                        val ageMillis = now - msg.date
                        isExpiredOtp = ageMillis > (15 * 60 * 1000)

                        if (!onlyExpiredOtps || isExpiredOtp) {
                            category = SmsCategory.OTP
                            val ageStr = formatAge(ageMillis)
                            matchReason = if (isExpiredOtp) {
                                "Expired OTP ($code): Received $ageStr ago"
                            } else {
                                "Active 2FA code ($code)"
                            }
                        }
                    } else if (filterPromotions && isPromotionalMessage(msg.address, msg.body)) {
                        category = SmsCategory.PROMOTION
                        matchReason = "Promotional offer / Commercial marketing campaign"
                    }
                }

                resultList.add(
                    msg.copy(
                        category = category,
                        duplicateGroupId = if (totalInGroup > 1) groupId else null,
                        duplicateIndex = index + 1,
                        totalInDuplicateGroup = totalInGroup,
                        isDuplicateExtra = isDuplicate,
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
     * Extracts clusters of duplicate messages grouped by conversation thread.
     */
    fun extractDuplicateThreadClusters(classifiedMessages: List<SmsMessageItem>): List<DuplicateThreadCluster> {
        val clusters = mutableListOf<DuplicateThreadCluster>()
        val byGroup = classifiedMessages
            .filter { it.duplicateGroupId != null }
            .groupBy { it.duplicateGroupId!! }

        for ((groupId, items) in byGroup) {
            val master = items.firstOrNull { !it.isDuplicateExtra } ?: items.first()
            val duplicates = items.filter { it.isDuplicateExtra }
            if (duplicates.isNotEmpty()) {
                clusters.add(
                    DuplicateThreadCluster(
                        groupId = groupId,
                        threadId = master.threadId,
                        sender = master.address,
                        snippet = master.body,
                        masterMessage = master,
                        duplicateCopies = duplicates,
                        isRcs = master.isRcsChat || master.protocol == MessageProtocol.RCS_CHAT
                    )
                )
            }
        }
        return clusters.sortedByDescending { it.duplicateCount }
    }

    private fun matchesCustomRule(msg: SmsMessageItem, rule: CustomFilterRule, now: Long): Boolean {
        // Sender check
        if (rule.senderFilter.isNotBlank()) {
            if (!msg.address.contains(rule.senderFilter.trim(), ignoreCase = true)) {
                return false
            }
        }

        // Keywords check
        if (rule.keywords.isNotBlank()) {
            val kwList = rule.keywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (kwList.isNotEmpty()) {
                val bodyLower = msg.body.lowercase()
                val matchesKeywords = if (rule.matchAnyKeyword) {
                    kwList.any { bodyLower.contains(it) }
                } else {
                    kwList.all { bodyLower.contains(it) }
                }
                if (!matchesKeywords) return false
            }
        }

        // Age check (older than X days)
        if (rule.olderThanDays != null && rule.olderThanDays > 0) {
            val minAgeMillis = rule.olderThanDays * 86400000L
            if ((now - msg.date) < minAgeMillis) {
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

    private fun isOtpMessage(body: String): Boolean {
        val lower = body.lowercase()
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

    private fun isPromotionalMessage(address: String, body: String): Boolean {
        val lower = body.lowercase()

        val isAlphanumericSender = address.length in 5..9 && address.matches("^[A-Za-z0-9_-]+$".toRegex()) && !address.matches("^\\+?[0-9]+$".toRegex())

        val hasPromoWord = PROMO_KEYWORDS.any { lower.contains(it) }
        val hasDealLink = lower.contains("http") || lower.contains(".com/") || lower.contains(".in/") || lower.contains(".co/")

        return (hasPromoWord && (isAlphanumericSender || hasDealLink)) ||
                (isAlphanumericSender && (lower.contains("off") || lower.contains("sale") || lower.contains("coupon") || lower.contains("order now")))
    }

    private fun isSpamMessage(body: String): Boolean {
        val lower = body.lowercase()
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
