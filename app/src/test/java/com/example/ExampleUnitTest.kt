package com.example

import com.example.cloud.CloudBackupManager
import com.example.data.SmsClassifier
import com.example.data.SmsRepository
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    // EDGE CASE 1: Whitespace & Casing Normalization in Deduplication
    @Test
    fun smsClassifier_duplicatesWithWhitespaceAndCasing_detectedCorrectly() {
        val now = System.currentTimeMillis()
        val messages = listOf(
            SmsMessageItem(
                id = "m1",
                address = "AMAZON",
                body = "Your order   has   been delivered.",
                date = now - 5000
            ),
            SmsMessageItem(
                id = "m2",
                address = "amazon",
                body = "Your order has been delivered.\n",
                date = now
            )
        )

        val classified = SmsClassifier.classifyMessages(
            rawMessages = messages,
            activeRules = emptyList(),
            filterDuplicates = true
        )

        val duplicates = classified.filter { it.category == SmsCategory.DUPLICATE }
        assertEquals(1, duplicates.size)
        assertTrue(duplicates.first().isDuplicateExtra)
    }

    // EDGE CASE 2: Large Duplicate Clusters (5 copies received)
    @Test
    fun smsClassifier_largeDuplicateCluster_preservesSingleMaster() {
        val now = System.currentTimeMillis()
        val messages = (1..5).map { i ->
            SmsMessageItem(
                id = "msg_$i",
                address = "UBER",
                body = "Your ride is arriving in 2 minutes",
                date = now - (i * 1000L)
            )
        }

        val classified = SmsClassifier.classifyMessages(
            rawMessages = messages,
            activeRules = emptyList(),
            filterDuplicates = true
        )

        val extraDuplicates = classified.filter { it.isDuplicateExtra }
        val masters = classified.filter { !it.isDuplicateExtra && it.totalInDuplicateGroup > 1 }

        assertEquals(4, extraDuplicates.size)
        assertEquals(1, masters.size)
    }

    // EDGE CASE 3: RCS Chat Duplicates with Verified READ Status
    @Test
    fun smsClassifier_rcsChatDeduplication_preservesThreadAndReadStatus() {
        val now = System.currentTimeMillis()
        val rcsMaster = SmsMessageItem(
            id = "rcs_master",
            address = "+1 (555) 392-1084",
            body = "Hey, are we still meeting at 4:30?",
            date = now - 10000,
            protocol = MessageProtocol.RCS_CHAT,
            threadId = 101L,
            isRcsChat = true,
            rcsDeliveryStatus = RcsDeliveryStatus.READ
        )

        val rcsRetryDuplicate = SmsMessageItem(
            id = "rcs_duplicate",
            address = "+1 (555) 392-1084",
            body = "Hey, are we still meeting at 4:30?",
            date = now,
            protocol = MessageProtocol.RCS_CHAT,
            threadId = 101L,
            isRcsChat = true,
            rcsDeliveryStatus = RcsDeliveryStatus.SENT
        )

        val classified = SmsClassifier.classifyMessages(
            rawMessages = listOf(rcsMaster, rcsRetryDuplicate),
            activeRules = emptyList(),
            filterDuplicates = true
        )

        val masterResult = classified.first { it.id == "rcs_master" }
        val duplicateResult = classified.first { it.id == "rcs_duplicate" }

        assertFalse(masterResult.isDuplicateExtra)
        assertEquals(RcsDeliveryStatus.READ, masterResult.rcsDeliveryStatus)
        assertEquals(101L, masterResult.threadId)

        assertTrue(duplicateResult.isDuplicateExtra)
        assertEquals(SmsCategory.DUPLICATE, duplicateResult.category)
        assertTrue(duplicateResult.matchReason.contains("RCS Duplicate"))
    }

    // EDGE CASE 4: Separate RCS Threads Must NOT Conflate Identical Messages
    @Test
    fun smsClassifier_rcsChat_separateThreadsNotConflated() {
        val now = System.currentTimeMillis()
        val thread1Msg = SmsMessageItem(
            id = "t1",
            address = "+1 (555) 111-1111",
            body = "Sounds good!",
            date = now - 5000,
            protocol = MessageProtocol.RCS_CHAT,
            threadId = 201L,
            isRcsChat = true
        )

        val thread2Msg = SmsMessageItem(
            id = "t2",
            address = "+1 (555) 222-2222",
            body = "Sounds good!",
            date = now,
            protocol = MessageProtocol.RCS_CHAT,
            threadId = 202L,
            isRcsChat = true
        )

        val classified = SmsClassifier.classifyMessages(
            rawMessages = listOf(thread1Msg, thread2Msg),
            activeRules = emptyList(),
            filterDuplicates = true
        )

        val duplicates = classified.filter { it.isDuplicateExtra }
        assertEquals(0, duplicates.size)
    }

    // EDGE CASE 5: Fresh OTP vs Expired OTP Handling
    @Test
    fun smsClassifier_freshOtp_notFlaggedWhenOnlyExpiredEnabled() {
        val now = System.currentTimeMillis()
        // 2 minutes old OTP (Fresh)
        val freshOtpMsg = SmsMessageItem(
            id = "fresh_otp",
            address = "Apple",
            body = "Your Apple verification code is 849201. Valid for 10 mins.",
            date = now - (2 * 60 * 1000L)
        )

        // 2 hours old OTP (Expired)
        val expiredOtpMsg = SmsMessageItem(
            id = "expired_otp",
            address = "Google",
            body = "G-192837 is your Google verification code.",
            date = now - (2 * 3600 * 1000L)
        )

        val classified = SmsClassifier.classifyMessages(
            rawMessages = listOf(freshOtpMsg, expiredOtpMsg),
            activeRules = emptyList(),
            filterOtps = true,
            onlyExpiredOtps = true
        )

        val freshResult = classified.first { it.id == "fresh_otp" }
        val expiredResult = classified.first { it.id == "expired_otp" }

        // Fresh OTP should NOT be flagged as cleanable OTP when onlyExpiredOtps is true
        assertEquals(SmsCategory.NORMAL, freshResult.category)
        assertFalse(freshResult.isCleanCandidate)

        // Expired OTP MUST be flagged
        assertEquals(SmsCategory.OTP, expiredResult.category)
        assertTrue(expiredResult.isCleanCandidate)
        assertTrue(expiredResult.isExpiredOtp)
        assertEquals("192837", expiredResult.otpCode)
    }

    // EDGE CASE 6: OTP Code Extraction with Prefixes (G-, FB-, etc.)
    @Test
    fun smsClassifier_otpCodeFormats_extractedAccurately() {
        val now = System.currentTimeMillis() - (3600 * 1000L)
        val msg1 = SmsMessageItem(id = "1", address = "Google", body = "G-492019 is your code.", date = now)
        val msg2 = SmsMessageItem(id = "2", address = "Bank", body = "Your auth PIN is 987654.", date = now)
        val msg3 = SmsMessageItem(id = "3", address = "Uber", body = "Use OTP: 1234 to verify.", date = now)

        val classified = SmsClassifier.classifyMessages(
            rawMessages = listOf(msg1, msg2, msg3),
            activeRules = emptyList(),
            filterOtps = true,
            onlyExpiredOtps = false
        )

        assertEquals("492019", classified.first { it.id == "1" }.otpCode)
        assertEquals("987654", classified.first { it.id == "2" }.otpCode)
        assertEquals("1234", classified.first { it.id == "3" }.otpCode)
    }

    // EDGE CASE 7: Personal messages containing casual words should NOT be marked as promo
    @Test
    fun smsClassifier_casualPersonalText_notFalselyClassifiedAsPromo() {
        val now = System.currentTimeMillis()
        val personalMsg = SmsMessageItem(
            id = "chat_1",
            address = "+1 (555) 234-5678",
            body = "Hey! Are you free for coffee later? There is a good coffee deal nearby.",
            date = now
        )

        val classified = SmsClassifier.classifyMessages(
            rawMessages = listOf(personalMsg),
            activeRules = emptyList(),
            filterPromotions = true,
            filterSpam = true
        )

        // Should remain NORMAL because it is from an individual phone without promo shortcodes or links
        assertEquals(SmsCategory.NORMAL, classified.first().category)
        assertFalse(classified.first().isCleanCandidate)
    }

    // EDGE CASE 8: Custom Filter Rules with AND vs OR Keyword Matching
    @Test
    fun smsClassifier_customRule_matchAnyVsMatchAll() {
        val now = System.currentTimeMillis()
        val msg = SmsMessageItem(
            id = "order_1",
            address = "StoreApp",
            body = "Your package has shipped and will arrive tomorrow.",
            date = now
        )

        // Rule with matchAny = true ("shipped, cancelled") -> Should match because "shipped" is present
        val ruleOr = CustomFilterRule(
            id = "r_or",
            name = "Shipment Rule OR",
            keywords = "shipped, cancelled",
            matchAnyKeyword = true,
            isEnabled = true
        )

        val classifiedOr = SmsClassifier.classifyMessages(
            rawMessages = listOf(msg),
            activeRules = listOf(ruleOr)
        )
        assertEquals(SmsCategory.CUSTOM_RULE, classifiedOr.first().category)

        // Rule with matchAny = false (both "shipped" AND "cancelled" required) -> Should NOT match
        val ruleAnd = CustomFilterRule(
            id = "r_and",
            name = "Shipment Rule AND",
            keywords = "shipped, cancelled",
            matchAnyKeyword = false,
            isEnabled = true
        )

        val classifiedAnd = SmsClassifier.classifyMessages(
            rawMessages = listOf(msg),
            activeRules = listOf(ruleAnd)
        )
        assertEquals(SmsCategory.NORMAL, classifiedAnd.first().category)
    }

    // EDGE CASE 9: Custom Rule Age Boundary
    @Test
    fun smsClassifier_customRule_ageBoundaryConditions() {
        val now = System.currentTimeMillis()
        val oneDay = 86400000L

        val recentMsg = SmsMessageItem(
            id = "recent",
            address = "Bank",
            body = "June statement generated",
            date = now - (15 * oneDay) // 15 days old
        )

        val oldMsg = SmsMessageItem(
            id = "old",
            address = "Bank",
            body = "April statement generated",
            date = now - (35 * oneDay) // 35 days old
        )

        val rule30Days = CustomFilterRule(
            id = "rule_age",
            name = "Bank Statements > 30d",
            senderFilter = "Bank",
            olderThanDays = 30,
            isEnabled = true
        )

        val classified = SmsClassifier.classifyMessages(
            rawMessages = listOf(recentMsg, oldMsg),
            activeRules = listOf(rule30Days)
        )

        // 15 days old message should NOT match > 30 days rule
        assertEquals(SmsCategory.NORMAL, classified.first { it.id == "recent" }.category)

        // 35 days old message SHOULD match
        assertEquals(SmsCategory.CUSTOM_RULE, classified.first { it.id == "old" }.category)
    }

    // EDGE CASE 10: Disabled Custom Rule Must Be Ignored
    @Test
    fun smsClassifier_disabledRule_notApplied() {
        val now = System.currentTimeMillis()
        val msg = SmsMessageItem(
            id = "promo_custom",
            address = "FoodDelivery",
            body = "Your food is on the way!",
            date = now
        )

        val disabledRule = CustomFilterRule(
            id = "r_disabled",
            name = "Food Delivery",
            senderFilter = "FoodDelivery",
            isEnabled = false // Disabled!
        )

        val classified = SmsClassifier.classifyMessages(
            rawMessages = listOf(msg),
            activeRules = listOf(disabledRule)
        )

        assertEquals(SmsCategory.NORMAL, classified.first().category)
    }

    // EDGE CASE 11: Cloud Backup JSON Roundtrip with Special Characters & Emojis
    @Test
    fun cloudBackupPayload_specialCharsAndEmojis_roundTripValid() {
        val payload = CloudBackupPayload(
            version = 2,
            app = "CleanSMS",
            provider = "Google Drive",
            totalCount = 1,
            messages = listOf(
                BackupMessageDto(
                    id = "msg_special",
                    address = "+1 (555) 999-0000",
                    body = "Hello! \"Quotes\", \\backslashes\\, newlines\nand emojis: 🚀🎉🔥",
                    date = 1700000000000L,
                    category = "RCS_CHAT",
                    matchReason = "RCS Duplicate in Thread #105",
                    otpCode = null,
                    protocol = "RCS_CHAT",
                    threadId = 105L,
                    isRcsChat = true
                )
            ),
            rules = listOf(
                CustomFilterRule(
                    id = "rule_spec",
                    name = "Special rule & test"
                )
            )
        )

        // Verify DTO fields integrity
        val item = payload.messages.first()
        assertTrue(item.isRcsChat)
        assertEquals(105L, item.threadId)
        assertTrue(item.body.contains("🚀"))
        assertEquals("Google Drive", payload.provider)
    }

    // EDGE CASE 12: End-to-End Demo Simulation
    @Test
    fun fullDemoSimulation_runsSuccessfullyWithoutErrors() {
        // Step 1: Generate simulated test messages
        val rawMessages = listOf(
            // Duplicate RCS chat
            SmsMessageItem(
                id = "sim_rcs_1",
                address = "+1 (555) 000-1111",
                body = "See you soon! (RCS)",
                date = 1000L,
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 501L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.READ
            ),
            SmsMessageItem(
                id = "sim_rcs_2",
                address = "+1 (555) 000-1111",
                body = "See you soon! (RCS)",
                date = 2000L,
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 501L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.SENT
            ),
            // Expired OTP
            SmsMessageItem(
                id = "sim_otp",
                address = "Google",
                body = "G-994820 is your Google verification code.",
                date = System.currentTimeMillis() - (3600 * 1000L)
            ),
            // Commercial promo
            SmsMessageItem(
                id = "sim_promo",
                address = "AD-SHOP",
                body = "Flash Sale! Flat 50% discount with promo code DEAL50 at checkout.",
                date = System.currentTimeMillis()
            ),
            // Phishing spam
            SmsMessageItem(
                id = "sim_spam",
                address = "+1800000000",
                body = "URGENT ALERT: Account suspended. Verify immediately at http://bit.ly/bank-unlock",
                date = System.currentTimeMillis()
            ),
            // Safe personal conversation
            SmsMessageItem(
                id = "sim_safe",
                address = "Mom",
                body = "Dinner is at 7pm tonight.",
                date = System.currentTimeMillis()
            )
        )

        // Step 2: Classify messages with active filters
        val classified = SmsClassifier.classifyMessages(
            rawMessages = rawMessages,
            activeRules = emptyList(),
            filterDuplicates = true,
            filterOtps = true,
            onlyExpiredOtps = true,
            filterPromotions = true,
            filterSpam = true
        )

        // Step 3: Assert expected classifications
        val duplicates = classified.filter { it.category == SmsCategory.DUPLICATE }
        val otps = classified.filter { it.category == SmsCategory.OTP }
        val promos = classified.filter { it.category == SmsCategory.PROMOTION }
        val spam = classified.filter { it.category == SmsCategory.SPAM }
        val normal = classified.filter { it.category == SmsCategory.NORMAL }

        assertEquals(1, duplicates.size)
        assertEquals(1, otps.size)
        assertEquals(1, promos.size)
        assertEquals(1, spam.size)
        // 1 original RCS master + 1 safe personal message = 2 normal messages kept
        assertEquals(2, normal.size)

        // Step 4: Verify Clean Candidates
        val cleanCandidates = classified.filter { it.isCleanCandidate }
        assertEquals(4, cleanCandidates.size) // duplicate copy, expired OTP, promo, spam

        // Step 5: Verify Safe Messages are NEVER marked as clean candidates
        val safeItem = classified.first { it.id == "sim_safe" }
        assertFalse(safeItem.isCleanCandidate)
        assertEquals(SmsCategory.NORMAL, safeItem.category)

        // Step 6: Verify RCS Master is preserved in thread
        val rcsMaster = classified.first { it.id == "sim_rcs_1" }
        assertFalse(rcsMaster.isDuplicateExtra)
        assertEquals(501L, rcsMaster.threadId)
        assertEquals(RcsDeliveryStatus.READ, rcsMaster.rcsDeliveryStatus)
    }
}
