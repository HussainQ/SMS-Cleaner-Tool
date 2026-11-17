package com.example.update

data class GitHubAssetDto(
    val name: String = "",
    val browserDownloadUrl: String = "",
    val size: Long = 0L,
    val contentType: String = "",
    val digest: String? = null
)

data class GitHubReleaseDto(
    val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val htmlUrl: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val publishedAt: String? = null,
    val assets: List<GitHubAssetDto> = emptyList()
)

data class GitHubApkAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256Checksum: String? = null,
    val sha256AssetUrl: String? = null
)

data class GitHubReleaseInfo(
    val tagName: String,
    val title: String,
    val version: SemanticVersion,
    val isPrerelease: Boolean,
    val publishedAt: String,
    val htmlUrl: String,
    val releaseNotes: String,
    val apkAsset: GitHubApkAsset? = null
)

/**
 * Result of comparing the locally installed version against all eligible releases on GitHub.
 * Lists every skipped intermediate release when the user is multiple versions behind, ensuring
 * transparent and airtight direct upgrades from any older version to the latest release.
 */
data class UpdateCheckResult(
    val repoSlug: String,
    val installedVersionName: String,
    val installedVersionCode: Int,
    val includePrereleases: Boolean,
    val latestRelease: GitHubReleaseInfo?,
    val upgradeChain: List<GitHubReleaseInfo>,
    val isUpdateAvailable: Boolean,
    val checkedAtEpoch: Long = System.currentTimeMillis(),
    val verifiedSha256: String? = null,
    val directUpgradeGuaranteed: Boolean = true,
    val upgradePathSummary: String = "",
    val errorMessage: String? = null
) {
    val versionsBehindCount: Int
        get() = upgradeChain.size
}

data class UpgradeMigrationReport(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val previousVersionName: String?,
    val previousVersionCode: Int?,
    val databaseSchemaVersion: Int,
    val appliedMilestones: List<String>,
    val wasMultiVersionJump: Boolean,
    val migratedAtEpoch: Long
)

enum class UpdateCheckStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    ERROR
}

enum class InAppUpdateInstallPhase {
    IDLE,
    DOWNLOADING,
    VERIFYING,
    READY_TO_INSTALL,
    PERMISSION_REQUIRED,
    INSTALL_LAUNCHED,
    FAILED
}

data class InAppDownloadState(
    val phase: InAppUpdateInstallPhase = InAppUpdateInstallPhase.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val progressFraction: Float = 0f,
    val apkFilePath: String? = null,
    val apkFileName: String? = null,
    val verifiedSha256: String? = null,
    val sha256Matched: Boolean? = null,
    val errorMessage: String? = null
)

data class DownloadedApkResult(
    val filePath: String,
    val fileName: String,
    val bytesDownloaded: Long,
    val computedSha256: String,
    val sha256Verified: Boolean
)

