package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.model.SmsCategory
import com.example.ui.CleanSmsViewModel
import com.example.ui.theme.CleanSMSTheme
import com.example.ui.theme.ThemePreferencesRepository
import com.example.update.AppUpgradeManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

/**
 * Renders live screenshots of CleanSMS Jetpack Compose screens inside Robolectric
 * Native Graphics mode.
 *
 * When invoked with `-PgenerateScreenshots=true`, writes updated PNGs directly
 * into `docs/screenshots/` for README showcase; otherwise verifies non-blank pixel rendering
 * in temp output during standard test runs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h860dp-night-xhdpi")
class ReadmeScreenshotGeneratorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences(AppUpgradeManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runBlocking {
            ThemePreferencesRepository(application).clear()
        }
    }

    @Test
    fun renderLiveComposeScreens_forMessagesCleanupVaultAndSettings() {
        val viewModel = CleanSmsViewModel(application)
        viewModel.setUseDeviceMaterialTheme(false)
        viewModel.setDarkModeEnabled(true)

        var rootComposeView: View? = null

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val effectiveDark = state.resolveIsDarkTheme(isSystemInDarkTheme())
            rootComposeView = LocalView.current
            CleanSMSTheme(
                useDeviceMaterialTheme = state.useDeviceMaterialTheme,
                darkTheme = effectiveDark,
                dynamicColor = false,
                themeManaged = true
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CleanSmsApp(viewModel = viewModel)
                }
            }
        }

        // Wait for demo messages and initial threads to populate
        composeRule.waitUntil(timeoutMillis = 8000L) {
            ShadowLooper.idleMainLooper()
            viewModel.uiState.value.conversationThreads.isNotEmpty() &&
                viewModel.uiState.value.classifiedMessages.isNotEmpty()
        }
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()

        val outputDir = resolveScreenshotOutputDir()
        outputDir.mkdirs()

        // 1. Messages tab (landing screen)
        captureAndVerifyPng(rootComposeView, File(outputDir, "messages_inbox.png"))

        // 2. Cleanup -> Scan sub-tab (trigger scan & seed quarantine for Vault screenshot)
        composeRule.onNodeWithTag("nav_tab_cleanup").performClick()
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()

        composeRule.onNodeWithTag("refresh_scan_button").performClick()
        composeRule.waitUntil(timeoutMillis = 8000L) {
            ShadowLooper.idleMainLooper()
            !viewModel.uiState.value.isScanning
        }
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        captureAndVerifyPng(rootComposeView, File(outputDir, "cleanup_scan.png"))

        // 3. Cleanup -> Review sub-tab
        composeRule.onNodeWithTag("cleanup_tab_review").performClick()
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        captureAndVerifyPng(rootComposeView, File(outputDir, "cleanup_review.png"))

        // 4. Cleanup -> Rules sub-tab
        composeRule.onNodeWithTag("cleanup_tab_rules").performClick()
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        captureAndVerifyPng(rootComposeView, File(outputDir, "cleanup_rules.png"))

        // Seed quarantined spam items so Vault displays live items cleanly
        viewModel.setCleanupSubTab(1)
        composeRule.waitForIdle()
        viewModel.selectAll(true, SmsCategory.SPAM)
        composeRule.waitForIdle()
        viewModel.quarantineSelectedMessages()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            viewModel.uiState.value.quarantinedMessages.isNotEmpty()
        }

        // Dismiss transient snackbar so Vault and Settings screenshots remain unobstructed
        viewModel.clearSnackbar()
        composeRule.mainClock.advanceTimeBy(5000L)
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()

        // 5. Vault tab
        composeRule.onNodeWithTag("nav_tab_vault").performClick()
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        captureAndVerifyPng(rootComposeView, File(outputDir, "vault_recovery.png"))

        // 6. Settings tab
        composeRule.onNodeWithTag("nav_tab_settings").performClick()
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        captureAndVerifyPng(rootComposeView, File(outputDir, "settings_hub.png"))
    }

    private fun captureAndVerifyPng(targetView: View?, destination: File) {
        val view = requireNotNull(targetView) { "Root Compose view must be attached before capture" }
        val rootView = view.rootView ?: view
        val width = rootView.width.coerceAtLeast(view.width)
        val height = rootView.height.coerceAtLeast(view.height)
        assertTrue("Expected positive measured dimensions for ${destination.name}, got ${width}x$height", width > 0 && height > 0)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rootView.draw(canvas)

        // Verify the rendered image is genuinely non-blank (contains varied colors/UI pixels)
        val firstPixel = bitmap.getPixel(width / 2, height / 2)
        var hasPixelVariance = false
        val stepX = (width / 24).coerceAtLeast(1)
        val stepY = (height / 24).coerceAtLeast(1)
        outer@ for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                if (bitmap.getPixel(x, y) != firstPixel) {
                    hasPixelVariance = true
                    break@outer
                }
            }
        }
        assertTrue("Rendered screenshot ${destination.name} appears blank", hasPixelVariance)

        FileOutputStream(destination).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        assertTrue("Screenshot file should exist and be non-empty: ${destination.absolutePath}", destination.length() > 1024L)
    }

    private fun resolveScreenshotOutputDir(): File {
        val shouldWriteDocs = System.getProperty("cleansms.generateScreenshots") == "true"
        if (!shouldWriteDocs) {
            return File(application.cacheDir, "rendered_screenshots")
        }
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) {
                return File(dir, "docs/screenshots")
            }
            dir = dir.parentFile
        }
        return File("../docs/screenshots")
    }
}
