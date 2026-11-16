package com.example.model

enum class MessageProtocol(val displayName: String, val badgeColorHex: Long) {
    SMS("SMS", 0xFF64748B),
    MMS("MMS", 0xFF0284C7),
    RCS_CHAT("RCS Chat", 0xFF00E5FF)
}

enum class RcsDeliveryStatus(val displayName: String) {
    SENT("Sent"),
    DELIVERED("Delivered"),
    READ("Read")
}

data class SmsMessageItem(
    val id: String,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int = 1, // 1 = Inbox, 2 = Sent
    val protocol: MessageProtocol = MessageProtocol.SMS,
    val threadId: Long = 0L,
    val rcsDeliveryStatus: RcsDeliveryStatus? = null,
    val isRcsChat: Boolean = false,
    val contactName: String? = null,
    val contactId: String? = null,
    val category: SmsCategory = SmsCategory.NORMAL,
    val duplicateGroupId: String? = null,
    val duplicateIndex: Int = 0,
    val totalInDuplicateGroup: Int = 1,
    val isDuplicateExtra: Boolean = false,
    val matchReason: String = "",
    val otpCode: String? = null,
    val isExpiredOtp: Boolean = false,
    val matchedRuleName: String? = null,
    val isSelected: Boolean = true,
    val isQuarantined: Boolean = false,
    val deletedAt: Long? = null,
    val isRead: Boolean = true,
    val attachmentUri: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentLabel: String? = null,
    val mmsSubject: String? = null
) {
    val displaySender: String
        get() = if (!contactName.isNullOrBlank() && !contactName.equals(address, ignoreCase = true)) {
            "$contactName ($address)"
        } else {
            address
        }

    val isCleanCandidate: Boolean
        get() = category != SmsCategory.NORMAL && (!category.equals(SmsCategory.DUPLICATE) || isDuplicateExtra)
}
