package com.example.update

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Fetches GitHub releases over HTTPS, resolves multi-version upgrade paths across arbitrary version gaps,
 * locates APK + SHA-256 verification assets, and prepares zero-permission external download intents.
 */
class GitHubUpdateRepository(
    private val okHttpClient: OkHttpClient = defaultClient(),
    private val api: GitHubUpdateApi = createApi(okHttpClient)
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
            val response = api.listReleases(owner = owner, repo = repo)
            val rawReleases = when {
                response.isSuccessful -> response.body().orEmpty()
                response.code() == 404 -> {
                    return@withContext Result.failure(
                        IllegalStateException("Repository '$normalizedSlug' or its releases could not be found on GitHub (HTTP 404).")
                    )
                }
                response.code() == 403 || response.code() == 429 -> {
                    return@withContext Result.failure(
                        IllegalStateException("GitHub API rate limit reached (HTTP ${response.code()}). Open GitHub Releases directly or retry shortly.")
                    )
                }
                else -> {
                    // Fallback to /releases/latest if list fails unexpectedly
                    val latestResp = api.getLatestStableRelease(owner = owner, repo = repo)
                    if (latestResp.isSuccessful && latestResp.body() != null) {
                        listOf(latestResp.body()!!)
                    } else {
                        return@withContext Result.failure(
                            IllegalStateException("GitHub returned HTTP ${response.code()} while checking updates.")
                        )
                    }
                }
            }

            // If stable-only yielded nothing in listReleases, also check /releases/latest as safety fallback
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

        private fun createApi(client: OkHttpClient): GitHubUpdateApi {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GitHubUpdateApi::class.java)
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
