package com.example.update

/**
 * Strict Semantic Versioning 2.0.0 parser & comparator supporting optional `v` prefix,
 * `MAJOR.MINOR.PATCH`, pre-release qualifiers (e.g. `-pre.1`, `-beta.2`, `-rc.1`),
 * and CleanSMS versionCode mapping (`MAJOR * 10000 + MINOR * 100 + PATCH`).
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
    val buildMetadata: String = "",
    val raw: String = ""
) : Comparable<SemanticVersion> {

    val isPreRelease: Boolean
        get() = preRelease.isNotEmpty()

    /**
     * CleanSMS release schema version code formula from README.md:
     * versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
     */
    fun toVersionCode(): Int = (major * 10000) + (minor * 100) + patch

    fun displayString(): String {
        val base = "$major.$minor.$patch"
        return if (preRelease.isEmpty()) base else "$base-${preRelease.joinToString(".")}"
    }

    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        // SemVer 2.0.0: A normal version has higher precedence than a pre-release version.
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1

        val maxLen = maxOf(preRelease.size, other.preRelease.size)
        for (i in 0 until maxLen) {
            if (i >= preRelease.size) return -1
            if (i >= other.preRelease.size) return 1

            val a = preRelease[i]
            val b = other.preRelease[i]
            val aNum = a.toIntOrNull()
            val bNum = b.toIntOrNull()

            val cmp = when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                aNum != null -> -1 // Numeric identifiers always have lower precedence than non-numeric
                bNum != null -> 1
                else -> a.compareTo(b, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    companion object {
        private val SEMVER_REGEX = Regex(
            """^\s*[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+([0-9A-Za-z.-]+))?\s*$"""
        )

        fun parse(input: String?): SemanticVersion? {
            if (input.isNullOrBlank()) return null
            val trimmed = input.trim()
            val match = SEMVER_REGEX.matchEntire(trimmed) ?: return null

            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: 0
            val patch = match.groupValues[3].toIntOrNull() ?: 0
            val preRaw = match.groupValues[4]
            val buildRaw = match.groupValues[5]

            val preParts = if (preRaw.isNotEmpty()) {
                preRaw.split('.').filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

            return SemanticVersion(
                major = major,
                minor = minor,
                patch = patch,
                preRelease = preParts,
                buildMetadata = buildRaw,
                raw = trimmed
            )
        }

        /**
         * Normalizes either `owner/repo` or `https://github.com/owner/repo(.git)` into `owner/repo`.
         */
        fun normalizeRepoSlug(rawInput: String): Pair<String, String>? {
            val cleaned = rawInput.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("github.com/")
                .removeSuffix(".git")
                .trim('/')
            val parts = cleaned.split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            return parts[0] to parts[1]
        }
    }
}
