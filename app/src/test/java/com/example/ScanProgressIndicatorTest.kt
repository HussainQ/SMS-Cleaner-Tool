package com.example

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.ui.CleanSmsUiState
import com.example.ui.CleanSmsViewModel
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.CleanSMSTheme
import com.example.update.AppUpgradeManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScanProgressIndicatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences(AppUpgradeManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun indeterminateProgressOverlay_displaysOnlyWhileScanning() {
        var scanning by mutableStateOf(false)

        composeRule.setContent {
            CleanSMSTheme(themeManaged = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SmsScanProgressOverlay(isScanning = scanning)
                }
            }
        }

        composeRule.onNodeWithTag("sms_scan_progress_bar").assertDoesNotExist()

        composeRule.runOnIdle { scanning = true }
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.onNodeWithTag("sms_scan_progress_bar").assertIsDisplayed()

        composeRule.runOnIdle { scanning = false }
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithTag("sms_scan_progress_bar").assertDoesNotExist()
    }

    @Test
    fun scannerScreen_showsInlineIndeterminateProgressBarDuringScan() {
        val viewModel = CleanSmsViewModel(application)
        var scanningState by mutableStateOf(true)

        composeRule.setContent {
            CleanSMSTheme(themeManaged = true) {
                ScannerScreen(
                    uiState = CleanSmsUiState(isScanning = scanningState),
                    viewModel = viewModel,
                    onRequestSmsPermission = {},
                    onRequestContactsPermission = {},
                    onRequestDefaultSmsRole = {},
                    onHandBackDefaultSmsRole = {},
                    onNavigateToPreview = {}
                )
            }
        }

        composeRule.onNodeWithTag("scanner_indeterminate_progress").assertIsDisplayed()
        composeRule.onNodeWithText("Scanning Inbox...").assertIsDisplayed()
        composeRule.onNodeWithText("Reading messages & evaluating rules...").assertIsDisplayed()

        composeRule.runOnIdle { scanningState = false }
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithTag("scanner_indeterminate_progress").assertDoesNotExist()
        composeRule.onNodeWithText("Inbox Analysis").assertIsDisplayed()
    }
}
