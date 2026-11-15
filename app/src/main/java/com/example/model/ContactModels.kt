package com.example.model

enum class ContactFilterMode(val displayName: String, val description: String) {
    TARGET_CONTACTS(
        displayName = "Target Selected",
        description = "Flag messages from selected contacts for cleaning"
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
