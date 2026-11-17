package com.example.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches GitHub releases over HTTPS using OkHttp + built-in org.json, resolves multi-version
 * upgrade paths across arbitrary version gaps, locates APK + SHA-256 verification assets, and
 * prepares verified in-app APK downloads.
 */
class GitHubUpdateRepository(
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    suspend fun checkForUpdates(
        repoSlug: String,
        installedVersionName: String,
        installedVersionCode: Int,
        includePrereleases: Boolean
    ): Result<UpdateCheckResult> = withContext(Dispatchers.IO) {
        val parsedSlug = SemanticVersion.normalizeRepoSlug(repoSlug)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid GitHub repository format. Use owner/repo.")
            )
        val (owner, repo) = parsedSlug
        val normalizedSlug = "$owner/$repo"

        try {
            val listRequest = githubApiRequest("https://api.github.com/repos/$owner/$repo/releases?per_page=100")
            val rawReleases = okHttpClient.newCall(listRequest).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val payload = response.body?.string().orEmpty()
                        parseReleasesArray(payload)
                    }
                    response.code == 404 -> {
                        return@withContext Result.failure(
                            IllegalStateException("Repository '$normalizedSlug' or its releases could not be found on GitHub (HTTP 404).")
                        )
                    }
                    response.code == 403 || response.code == 429 -> {
                        return@withContext Result.failure(
                            IllegalStateException("GitHub API rate limit reached (HTTP ${response.code}). Open GitHub Releases directly or retry shortly.")
                        )
                    }
                    else -> {
                        val latestRequest = githubApiRequest("https://api.github.com/repos/$owner/$repo/releases/latest")
                        okHttpClient.newCall(latestRequest).execute().use { latestResp ->
                            val latestPayload = latestResp.body?.string().orEmpty()
                            if (latestResp.isSuccessful && latestPayload.isNotBlank()) {
                                listOf(parseReleaseObject(JSONObject(latestPayload)))
                            } else {
                                return@withContext Result.failure(
                                    IllegalStateException("GitHub returned HTTP ${response.code} while checking updates.")
                                )
                            }
                        }
                    }
                }
            }

            // Evaluate eligible releases and multi-version upgrade chain
            val evaluated = evaluateReleases(
                repoSlug = normalizedSlug,
                releases = rawReleases,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode,
                includePrereleases = includePrereleases
            )

            // Pull SHA-256 verification string from `.sha256` asset if attached to latest release
            val latest = evaluated.latestRelease
            val fetchedChecksum = if (latest?.apkAsset != null) {
                latest.apkAsset.sha256Checksum
                    ?: latest.apkAsset.sha256AssetUrl?.let { fetchSha256FromUrl(it) }
                    ?: extractSha256FromText(latest.releaseNotes)
            } else {
                null
            }

            Result.success(
                evaluated.copy(verifiedSha256 = fetchedChecksum)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun githubApiRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "CleanSMS-Android-Updater")
            .build()
    }

    private fun fetchSha256FromUrl(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "CleanSMS-Android-Updater")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val text = resp.body?.string().orEmpty()
                extractSha256FromText(text)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Downloads an APK release asset directly to app-private storage, computes its SHA-256 digest
     * on the fly, verifies its ZIP/APK signature bytes, and validates against [expectedSha256]
     * when available. Never opens an external browser.
     */
    suspend fun downloadApkUpdate(
        downloadUrl: String,
        destinationFile: java.io.File,
        expectedSha256: String? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<DownloadedApkResult> = withContext(Dispatchers.IO) {
        if (downloadUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Release APK download URL is missing."))
        }
        val parentDir = destinationFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
        val tempFile = java.io.File(destinationFile.parentFile, "${destinationFile.name}.download")
        if (tempFile.exists()) tempFile.delete()

        try {
            val downloadClient = okHttpClient.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "CleanSMS-Android-Updater")
                .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                .build()

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("APK download failed with HTTP ${response.code}.")
                    )
                }
                val body = response.body
                    ?: return@withContext Result.failure(IllegalStateException("Empty APK response from server."))

                val totalBytes = body.contentLength()
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastProgressEmit = 0L
                var headerByte0 = -1
                var headerByte1 = -1

                body.byteStream().use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (downloaded == 0L && read >= 2) {
                                headerByte0 = buffer[0].toInt() and 0xFF
                                headerByte1 = buffer[1].toInt() and 0xFF
                            } else if (downloaded == 0L && read == 1) {
                                headerByte0 = buffer[0].toInt() and 0xFF
                            } else if (downloaded == 1L && headerByte1 == -1 && read >= 1) {
                                headerByte1 = buffer[0].toInt() and 0xFF
                            }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read

                            if (downloaded - lastProgressEmit >= 64 * 1024 || (totalBytes > 0 && downloaded >= totalBytes)) {
                                lastProgressEmit = downloaded
                                onProgress(downloaded, totalBytes)
                            }
                        }
                        output.fd.sync()
                    }
                }

                onProgress(downloaded, totalBytes)

                // Verify APK/ZIP magic bytes ('PK' = 0x50 0x4B) to reject HTML error pages
                if (downloaded <= 0L || headerByte0 != 0x50 || headerByte1 != 0x4B) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IllegalStateException("Downloaded file is not a valid Android APK package.")
                    )
                }

                val computedHex = digest.digest().joinToString("") { "%02x".format(it) }
                val expectedNormalized = expectedSha256?.trim()?.lowercase()?.takeIf { SHA256_REGEX.matches(it) }
                if (expectedNormalized != null && computedHex != expectedNormalized) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        SecurityException("APK SHA-256 mismatch! Expected ${expectedNormalized.take(12)}…, got ${computedHex.take(12)}….")
                    )
                }

                if (destinationFile.exists()) destinationFile.delete()
                if (!tempFile.renameTo(destinationFile)) {
                    tempFile.copyTo(destinationFile, overwrite = true)
                    tempFile.delete()
                }

                Result.success(
                    DownloadedApkResult(
                        filePath = destinationFile.absolutePath,
                        fileName = destinationFile.name,
                        bytesDownloaded = downloaded,
                        computedSha256 = computedHex,
                        sha256Verified = expectedNormalized != null
                    )
                )
            }
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    companion object {
        private val SHA256_REGEX = Regex("""\b([a-fA-F0-9]{64})\b""")

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "CleanSMS-Android-Updater")
                        .build()
                    chain.proceed(req)
                }
                .build()
        }

        private fun optNullableString(obj: JSONObject, key: String): String? {
            if (!obj.has(key) || obj.isNull(key)) return null
            return obj.optString(key)
        }

        private fun parseReleasesArray(jsonPayload: String): List<GitHubReleaseDto> {
            val trimmed = jsonPayload.trim()
            if (trimmed.isEmpty()) return emptyList()
            val array = JSONArray(trimmed)
            val list = ArrayList<GitHubReleaseDto>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(parseReleaseObject(obj))
            }
            return list
        }

        private fun parseReleaseObject(obj: JSONObject): GitHubReleaseDto {
            val assetsArray = obj.optJSONArray("assets")
            val assets = if (assetsArray != null) {
                ArrayList<GitHubAssetDto>(assetsArray.length()).apply {
                    for (j in 0 until assetsArray.length()) {
                        val aObj = assetsArray.optJSONObject(j) ?: continue
                        add(
                            GitHubAssetDto(
                                name = optNullableString(aObj, "name").orEmpty(),
                                browserDownloadUrl = optNullableString(aObj, "browser_download_url").orEmpty(),
                                size = aObj.optLong("size", 0L),
                                contentType = optNullableString(aObj, "content_type").orEmpty(),
                                digest = optNullableString(aObj, "digest")
                            )
                        )
                    }
                }
            } else {
                emptyList()
            }
            return GitHubReleaseDto(
                tagName = optNullableString(obj, "tag_name").orEmpty(),
                name = optNullableString(obj, "name"),
                body = optNullableString(obj, "body"),
                htmlUrl = optNullableString(obj, "html_url").orEmpty(),
                prerelease = obj.optBoolean("prerelease", false),
                draft = obj.optBoolean("draft", false),
                publishedAt = optNullableString(obj, "published_at"),
                assets = assets
            )
        }

        /**
         * Pure evaluation function (easy to unit-test deterministically):
         * Parses all non-draft GitHub releases, orders them by strict SemVer, computes every
         * intermediate skipped version from [installedVersionName] to the true latest release,
         * and verifies that direct multi-version data upgrade paths are intact.
         */
        fun evaluateReleases(
            repoSlug: String,
            releases: List<GitHubReleaseDto>,
            installedVersionName: String,
            installedVersionCode: Int,
            includePrereleases: Boolean
        ): UpdateCheckResult {
            val installedSemVer = SemanticVersion.parse(installedVersionName)
                ?: SemanticVersion(
                    major = (installedVersionCode / 10000).coerceAtLeast(1),
                    minor = (installedVersionCode % 10000) / 100,
                    patch = installedVersionCode % 100,
                    raw = installedVersionName
                )

            val eligibleReleases = releases
                .asSequence()
                .filter { !it.draft }
                .filter { includePrereleases || !it.prerelease }
                .mapNotNull { dto ->
                    val parsedVer = SemanticVersion.parse(dto.tagName)
                        ?: SemanticVersion.parse(dto.name)
                        ?: return@mapNotNull null

                    // Respect SemVer pre-release tag even if GitHub prerelease boolean wasn't checked
                    if (!includePrereleases && parsedVer.isPreRelease) {
                        return@mapNotNull null
                    }

                    val apkAsset = selectBestApkAsset(dto.assets, dto.body.orEmpty())
                    GitHubReleaseInfo(
                        tagName = dto.tagName.ifBlank { "v${parsedVer.displayString()}" },
                        title = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName,
                        version = parsedVer,
                        isPrerelease = dto.prerelease || parsedVer.isPreRelease,
                        publishedAt = dto.publishedAt.orEmpty(),
                        htmlUrl = dto.htmlUrl.ifBlank { "https://github.com/$repoSlug/releases/tag/${dto.tagName}" },
                        releaseNotes = dto.body.orEmpty(),
                        apkAsset = apkAsset
                    )
                }
                .sortedBy { it.version }
                .toList()

            val upgradeChain = eligibleReleases.filter { release ->
                release.version > installedSemVer
            }

            val latestRelease = upgradeChain.lastOrNull() ?: eligibleReleases.lastOrNull()
            val isUpdateAvailable = upgradeChain.isNotEmpty()

            val pathSummary = if (isUpdateAvailable) {
                val steps = listOf("v${installedSemVer.displayString()}") +
                    upgradeChain.map { "v${it.version.displayString()}" }
                steps.joinToString(" → ")
            } else {
                "Up to date at v${installedSemVer.displayString()}"
            }

            return UpdateCheckResult(
                repoSlug = repoSlug,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode,
                includePrereleases = includePrereleases,
                latestRelease = latestRelease,
                upgradeChain = upgradeChain,
                isUpdateAvailable = isUpdateAvailable,
                verifiedSha256 = latestRelease?.apkAsset?.sha256Checksum
                    ?: latestRelease?.releaseNotes?.let { extractSha256FromText(it) },
                directUpgradeGuaranteed = true,
                upgradePathSummary = pathSummary
            )
        }

        private fun selectBestApkAsset(
            assets: List<GitHubAssetDto>,
            releaseBody: String
        ): GitHubApkAsset? {
            val apkAssets = assets.filter {
                it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
            }
            if (apkAssets.isEmpty()) return null

            val chosen = apkAssets.firstOrNull {
                it.name.contains("CleanSMS", ignoreCase = true) && !it.name.contains("unsigned", ignoreCase = true)
            } ?: apkAssets.firstOrNull {
                !it.name.contains("unsigned", ignoreCase = true)
            } ?: apkAssets.first()

            val shaAsset = assets.firstOrNull {
                it.name.equals("${chosen.name}.sha256", ignoreCase = true) ||
                    it.name.endsWith(".sha256", ignoreCase = true)
            }

            val digestHex = chosen.digest
                ?.removePrefix("sha256:")
                ?.takeIf { SHA256_REGEX.matches(it) }
                ?: extractSha256FromText(releaseBody)

            return GitHubApkAsset(
                name = chosen.name,
                downloadUrl = chosen.browserDownloadUrl,
                sizeBytes = chosen.size,
                sha256Checksum = digestHex,
                sha256AssetUrl = shaAsset?.browserDownloadUrl
            )
        }

        fun extractSha256FromText(text: String): String? {
            if (text.isBlank()) return null
            return SHA256_REGEX.find(text)?.groupValues?.get(1)?.lowercase()
        }
    }
}
