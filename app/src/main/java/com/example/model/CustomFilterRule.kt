package com.example.model

data class CustomFilterRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val senderFilter: String = "", // Matches sender address/name (case-insensitive contains)
    val keywords: String = "", // Comma-separated keywords
    val matchAnyKeyword: Boolean = true, // If true, matches if any keyword found; if false, must have all
    val olderThanDays: Int? = null, // e.g. 30, 90 days
    val startDateEpoch: Long? = null,
    val endDateEpoch: Long? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
