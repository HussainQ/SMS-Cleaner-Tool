package com.example.update

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubAssetDto(
    @param:Json(name = "name") val name: String = "",
    @param:Json(name = "browser_download_url") val browserDownloadUrl: String = "",
    @param:Json(name = "size") val size: Long = 0L,
    @param:Json(name = "content_type") val contentType: String = "",
    @param:Json(name = "digest") val digest: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @param:Json(name = "tag_name") val tagName: String = "",
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "body") val body: String? = null,
    @param:Json(name = "html_url") val htmlUrl: String = "",
    @param:Json(name = "prerelease") val prerelease: Boolean = false,
    @param:Json(name = "draft") val draft: Boolean = false,
    @param:Json(name = "published_at") val publishedAt: String? = null,
    @param:Json(name = "assets") val assets: List<GitHubAssetDto> = emptyList()
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

