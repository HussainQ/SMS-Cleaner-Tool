package com.example.model

enum class SmsCategory(val displayName: String, val badgeColorHex: Long) {
    ALL("All Flagged", 0xFF6366F1),
    DUPLICATE("Duplicates", 0xFFF59E0B),
    OTP("OTPs & 2FA", 0xFF8B5CF6),
    PROMOTION("Promotions", 0xFFEC4899),
    SPAM("Spam & Phishing", 0xFFEF4444),
    CUSTOM_RULE("Custom Rules", 0xFF10B981),
    NORMAL("Normal / Safe", 0xFF64748B)
}
