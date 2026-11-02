package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.example.model.MessageProtocol
import com.example.model.RcsDeliveryStatus
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem

class SmsRepository(private val context: Context) {

    /**
     * Reads all SMS and RCS messages from the device's Telephony ContentProvider.
     * Returns empty list if permission not granted or on error.
     */
    fun readDeviceSms(): List<SmsMessageItem> {
        val messageList = mutableListOf<SmsMessageItem>()
        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.THREAD_ID
            )

            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val threadIdIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx).toString()
                    val address = it.getString(addressIdx) ?: "Unknown"
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx)
                    val threadId = if (threadIdIdx >= 0) it.getLong(threadIdIdx) else 0L

                    // Check for RCS indicators: conversation threads, JIDs, or E.164 phone chats
                    val isRcsChat = threadId > 0 && (address.startsWith("+") || address.contains("@"))

                    messageList.add(
                        SmsMessageItem(
                            id = id,
                            address = address,
                            body = body,
                            date = date,
                            type = type,
                            threadId = threadId,
                            protocol = if (isRcsChat) MessageProtocol.RCS_CHAT else MessageProtocol.SMS,
                            isRcsChat = isRcsChat,
                            rcsDeliveryStatus = if (isRcsChat) RcsDeliveryStatus.READ else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CleanSMS", "Error reading device SMS/RCS: ${e.message}")
        }
        return messageList
    }

    /**
     * Permanently deletes messages from the Android Telephony ContentProvider.
     * Requires app to be the Default SMS app.
     */
    fun deleteFromDeviceSms(messageIds: List<String>): Int {
        var deletedCount = 0
        try {
            for (idStr in messageIds) {
                val idLong = idStr.toLongOrNull() ?: continue
                val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, idLong)
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) deletedCount += rows
            }
        } catch (e: Exception) {
            Log.e("CleanSMS", "Failed to delete SMS/RCS: ${e.message}")
        }
        return deletedCount
    }

    /**
     * Provides a realistic demo dataset with 30+ messages showcasing:
     * - RCS Chat duplicate retries (with threadId preservation & read receipts)
     * - SMS duplicate message groups from same senders
     * - Expired and active OTPs
     * - Marketing promotions and offers
     * - Phishing / spam alerts
     * - Genuine conversations and receipts
     */
    fun getDemoSmsList(): List<SmsMessageItem> {
        val now = System.currentTimeMillis()
        val oneHour = 3600 * 1000L
        val oneDay = 24 * oneHour

        return listOf(
            // RCS CHAT DUPLICATE GROUP 1: Sarah Miller (Thread #101)
            // Master message (with verified READ status in RCS chat)
            SmsMessageItem(
                id = "demo_rcs_1a",
                address = "+1 (555) 392-1084",
                body = "Hey! Are we still meeting at the cafe at 4:30? Let me know when you're on the way.",
                date = now - (20 * 60 * 1000L),
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 101L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.READ,
                category = SmsCategory.DUPLICATE
            ),
            // Redundant retry duplicate (created during poor signal network retry)
            SmsMessageItem(
                id = "demo_rcs_1b",
                address = "+1 (555) 392-1084",
                body = "Hey! Are we still meeting at the cafe at 4:30? Let me know when you're on the way.",
                date = now - (20 * 60 * 1000L) + 15000,
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 101L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.SENT,
                category = SmsCategory.DUPLICATE
            ),

            // RCS GROUP CHAT DUPLICATE GROUP 2: Trip Group (Thread #102)
            SmsMessageItem(
                id = "demo_rcs_2a",
                address = "+1 (555) 839-2041",
                body = "Alex: I reserved the rental cabin for next weekend! Check the confirmation link in the group.",
                date = now - (3 * oneHour),
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 102L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.DELIVERED,
                category = SmsCategory.DUPLICATE
            ),
            SmsMessageItem(
                id = "demo_rcs_2b",
                address = "+1 (555) 839-2041",
                body = "Alex: I reserved the rental cabin for next weekend! Check the confirmation link in the group.",
                date = now - (3 * oneHour) + 30000,
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 102L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.SENT,
                category = SmsCategory.DUPLICATE
            ),

            // STANDARD SMS DUPLICATE GROUP 1: Amazon Delivery (3 copies received)
            SmsMessageItem(
                id = "demo_dup_1a",
                address = "AMAZON",
                body = "Your order #402-991204 has been delivered to your front door. Thank you for shopping with Amazon!",
                date = now - (2 * oneHour),
                protocol = MessageProtocol.SMS,
                threadId = 201L,
                category = SmsCategory.DUPLICATE
            ),
            SmsMessageItem(
                id = "demo_dup_1b",
                address = "AMAZON",
                body = "Your order #402-991204 has been delivered to your front door. Thank you for shopping with Amazon!",
                date = now - (2 * oneHour) + 60000,
                protocol = MessageProtocol.SMS,
                threadId = 201L,
                category = SmsCategory.DUPLICATE
            ),
            SmsMessageItem(
                id = "demo_dup_1c",
                address = "AMAZON",
                body = "Your order #402-991204 has been delivered to your front door. Thank you for shopping with Amazon!",
                date = now - (2 * oneHour) + 120000,
                protocol = MessageProtocol.SMS,
                threadId = 201L,
                category = SmsCategory.DUPLICATE
            ),

            // DUPLICATE GROUP 2: Uber Ride Updates (2 copies)
            SmsMessageItem(
                id = "demo_dup_2a",
                address = "Uber",
                body = "Your driver Michael in Toyota Prius (ABC-1234) is arriving in 2 minutes. Please be ready at pickup.",
                date = now - (5 * oneHour),
                protocol = MessageProtocol.SMS,
                threadId = 202L,
                category = SmsCategory.DUPLICATE
            ),
            SmsMessageItem(
                id = "demo_dup_2b",
                address = "Uber",
                body = "Your driver Michael in Toyota Prius (ABC-1234) is arriving in 2 minutes. Please be ready at pickup.",
                date = now - (5 * oneHour) + 45000,
                protocol = MessageProtocol.SMS,
                threadId = 202L,
                category = SmsCategory.DUPLICATE
            ),

            // OTP 1: Google verification code (Expired, 1 day old)
            SmsMessageItem(
                id = "demo_otp_1",
                address = "Google",
                body = "G-584920 is your Google verification code. Never share your security code with anyone.",
                date = now - (1 * oneDay),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.OTP,
                otpCode = "584920",
                isExpiredOtp = true
            ),

            // OTP 2: Bank 2FA code (Expired, 4 hours old)
            SmsMessageItem(
                id = "demo_otp_2",
                address = "ChaseBank",
                body = "940218 is your Chase Online verification code for debit card authorization. Valid for 10 minutes. Do not share.",
                date = now - (4 * oneHour),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.OTP,
                otpCode = "940218",
                isExpiredOtp = true
            ),

            // OTP 3: WhatsApp registration code (Expired, 3 days old)
            SmsMessageItem(
                id = "demo_otp_3",
                address = "WhatsApp",
                body = "Your WhatsApp code is 382-910. Do not share this code with anyone.",
                date = now - (3 * oneDay),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.OTP,
                otpCode = "382910",
                isExpiredOtp = true
            ),

            // OTP 4: Apple ID code (Active, 3 minutes old)
            SmsMessageItem(
                id = "demo_otp_4",
                address = "Apple",
                body = "Your Apple Account verification code is: 736192. Don't share it with anyone.",
                date = now - (3 * 60 * 1000),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.OTP,
                otpCode = "736192",
                isExpiredOtp = false
            ),

            // PROMOTIONS & OFFERS
            SmsMessageItem(
                id = "demo_promo_1",
                address = "AD-DOMINOS",
                body = "Weekend Special! Flat 50% discount on all large pizzas + free delivery. Use promo code YUMMY50 at checkout. Order now: dom.pizza/deal",
                date = now - (6 * oneHour),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.PROMOTION
            ),
            SmsMessageItem(
                id = "demo_promo_2",
                address = "VK-MYNTRA",
                body = "Mega Fashion Sale is LIVE! Up to 80% off on premium apparel, sneakers & watches. Extra 10% coupon: MEGAFASH. Shop now: myntra.com/sale",
                date = now - (12 * oneHour),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.PROMOTION
            ),
            SmsMessageItem(
                id = "demo_promo_3",
                address = "AX-SEPHORA",
                body = "Exclusive VIP Offer: Claim $25 voucher on purchases over $100 this weekend. Use code BEAUTYVIP. Valid till Sunday midnight.",
                date = now - (2 * oneDay),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.PROMOTION
            ),

            // SPAM & PHISHING
            SmsMessageItem(
                id = "demo_spam_1",
                address = "+1800555019",
                body = "URGENT SECURITY ALERT: Your bank account has been suspended due to suspicious unauthorized debit. Verify immediately at http://bit.ly/bank-unlock-verify to avoid permanent lock.",
                date = now - (8 * oneHour),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.SPAM
            ),
            SmsMessageItem(
                id = "demo_spam_2",
                address = "+1202555014",
                body = "CONGRATULATIONS! You won 2nd prize of $250,000 in international mobile lottery. Claim your prize now by clicking http://tinyurl.com/claim-cash-2026",
                date = now - (1 * oneDay),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.SPAM
            ),

            // NORMAL / PERSONAL CONVERSATIONS (RCS & SMS)
            SmsMessageItem(
                id = "demo_norm_rcs",
                address = "+1 (555) 789-0123",
                body = "Awesome, see you at the gym in 15 mins! (RCS Chat)",
                date = now - (45 * 60 * 1000),
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 103L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.READ,
                category = SmsCategory.NORMAL
            ),
            SmsMessageItem(
                id = "demo_norm_1",
                address = "Mom",
                body = "Hi honey, don't forget Dad's birthday dinner on Saturday evening. Love you!",
                date = now - (1 * oneDay),
                protocol = MessageProtocol.SMS,
                threadId = 205L,
                category = SmsCategory.NORMAL
            ),
            SmsMessageItem(
                id = "demo_norm_2",
                address = "Dr. Clinic",
                body = "Appointment reminder: Routine dental checkup scheduled for Thursday at 10:00 AM.",
                date = now - (3 * oneDay),
                protocol = MessageProtocol.SMS,
                threadId = 206L,
                category = SmsCategory.NORMAL
            )
        )
    }
}
