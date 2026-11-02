package com.example.model

enum class DuplicateKeepStrategy(val displayName: String, val description: String) {
    KEEP_OLDEST("Keep Oldest", "Preserves original message timestamp in thread"),
    KEEP_NEWEST("Keep Newest", "Preserves most recent message timestamp in thread")
}

data class DuplicateThreadCluster(
    val groupId: String,
    val threadId: Long,
    val sender: String,
    val snippet: String,
    val masterMessage: SmsMessageItem,
    val duplicateCopies: List<SmsMessageItem>,
    val isRcs: Boolean
) {
    val totalCount: Int get() = 1 + duplicateCopies.size
    val duplicateCount: Int get() = duplicateCopies.size
}
