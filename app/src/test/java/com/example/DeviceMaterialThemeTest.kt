package com.example

import android.app.Application
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ui.CleanSmsViewModel
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CleanSMSTheme
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.ThemePreferencesRepository
import com.example.update.AppUpgradeManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceMaterialThemeTest {

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
        runBlocking {
            ThemePreferencesRepository(application).clear()
        }
    }

    @Test
    @Config(qualifiers = "notnight")
    fun cleanSmsTheme_followsDeviceLightModeWhenEnabled_andRevertsToClassicDarkWhenDisabled() {
        val viewModel = CleanSmsViewModel(application)
        assertTrue(viewModel.uiState.value.useDeviceMaterialTheme)

        var observedPrimary: Color? = null
        var observedBackground: Color? = null

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            CleanSMSTheme(
                useDeviceMaterialTheme = state.useDeviceMaterialTheme,
                themeManaged = true
            ) {
                observedPrimary = MaterialTheme.colorScheme.primary
                observedBackground = MaterialTheme.colorScheme.background
            }
        }

        composeRule.waitForIdle()
        // Device Light mode (notnight) should NOT use hardcoded SurfaceDark
        assertNotEquals(SurfaceDark, observedBackground)

        // Toggle OFF -> reverts to Classic CleanSMS Navy/Cyan Dark theme regardless of device Light mode
        viewModel.setUseDeviceMaterialTheme(false)
        composeRule.waitForIdle()

        assertEquals(SurfaceDark, observedBackground)
        assertEquals(CyanAccent, observedPrimary)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun settingsToggle_inSettingsScreen_togglesAndPersistsDeviceMaterialTheme() {
        val viewModel = CleanSmsViewModel(application)

        var observedBackground: Color? = null
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            CleanSMSTheme(
                useDeviceMaterialTheme = state.useDeviceMaterialTheme,
                themeManaged = true
            ) {
                observedBackground = MaterialTheme.colorScheme.background
                SettingsScreen(uiState = state, viewModel = viewModel)
            }
        }

        composeRule.waitForIdle()
        val switchNode = composeRule.onNodeWithTag("settings_device_material_theme_switch")
        switchNode.performScrollTo()
        switchNode.assertIsOn()
        assertNotEquals(SurfaceDark, observedBackground)

        // Turn OFF in Settings UI
        switchNode.performClick()
        composeRule.waitForIdle()

        switchNode.assertIsOff()
        assertFalse(viewModel.uiState.value.useDeviceMaterialTheme)
        assertFalse(AppUpgradeManager(application).isUseDeviceMaterialTheme())
        assertEquals(SurfaceDark, observedBackground)

        // Turn back ON in Settings UI
        switchNode.performClick()
        composeRule.waitForIdle()

        switchNode.assertIsOn()
        assertTrue(viewModel.uiState.value.useDeviceMaterialTheme)
        assertTrue(AppUpgradeManager(application).isUseDeviceMaterialTheme())
        assertNotEquals(SurfaceDark, observedBackground)
    }

    @Test
    fun messagesSettingsDialog_deviceMaterialThemeToggle_syncsWithViewModel() {
        val viewModel = CleanSmsViewModel(application)

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            CleanSMSTheme(
                useDeviceMaterialTheme = state.useDeviceMaterialTheme,
                themeManaged = true
            ) {
                MessagesScreen(
                    uiState = state,
                    viewModel = viewModel,
                    onPickMedia = {},
                    onRequestSendSmsPermission = {},
                    onRequestDefaultSmsRole = {}
                )
            }
        }

        composeRule.onNodeWithTag("messages_settings_btn").performClick()
        composeRule.waitForIdle()

        val dialogSwitch = composeRule.onNodeWithTag("messages_dialog_device_material_theme_switch")
        dialogSwitch.assertIsOn()

        dialogSwitch.performClick()
        composeRule.waitForIdle()

        dialogSwitch.assertIsOff()
        assertFalse(viewModel.uiState.value.useDeviceMaterialTheme)
        assertFalse(AppUpgradeManager(application).isUseDeviceMaterialTheme())
    }

    @Test
    @Config(qualifiers = "notnight")
    fun darkModeToggle_inSettings_updatesAppThemeAndPersistsInDataStore() {
        val viewModel = CleanSmsViewModel(application)
        var observedBgLuminance: Float? = null

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val effectiveDark = state.resolveIsDarkTheme(isSystemInDarkTheme())
            CleanSMSTheme(
                useDeviceMaterialTheme = state.useDeviceMaterialTheme,
                darkTheme = effectiveDark,
                themeManaged = true
            ) {
                observedBgLuminance = MaterialTheme.colorScheme.background.luminance()
                CleanSmsApp(viewModel = viewModel)
            }
        }

        composeRule.waitForIdle()
        // Navigate to Settings tab directly
        composeRule.onNodeWithTag("nav_tab_settings").performClick()
        composeRule.waitForIdle()

        val darkSwitch = composeRule.onNodeWithTag("settings_dark_mode_switch")
        darkSwitch.performScrollTo()
        darkSwitch.assertIsOff()
        assertTrue("Expected light theme initially under notnight", (observedBgLuminance ?: 0f) > 0.5f)

        // Enable Material 3 Dark Mode
        darkSwitch.performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            viewModel.uiState.value.darkModeOverride == true
        }
        composeRule.waitForIdle()
        darkSwitch.assertIsOn()
        assertTrue("Expected dark background luminance after enabling dark mode", (observedBgLuminance ?: 1f) < 0.25f)

        // Verify persisted in DataStore and restored on fresh ViewModel
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            runBlocking { ThemePreferencesRepository(application).darkModeEnabledFlow.first() == true }
        }
        val restartedViewModel = CleanSmsViewModel(application)
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            restartedViewModel.uiState.value.darkModeOverride == true
        }
        assertEquals(true, restartedViewModel.uiState.value.darkModeOverride)

        // Disable Material 3 Dark Mode
        darkSwitch.performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            viewModel.uiState.value.darkModeOverride == false
        }
        composeRule.waitForIdle()
        darkSwitch.assertIsOff()
        assertTrue("Expected light background luminance after disabling dark mode", (observedBgLuminance ?: 0f) > 0.5f)

        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            runBlocking { ThemePreferencesRepository(application).darkModeEnabledFlow.first() == false }
        }
    }

    @Test
    @Config(qualifiers = "night")
    fun messagesSettingsDialog_darkModeToggle_disablesDarkModeEvenInSystemNightAndPersists() {
        val viewModel = CleanSmsViewModel(application)
        var observedBgLuminance: Float? = null

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val effectiveDark = state.resolveIsDarkTheme(isSystemInDarkTheme())
            CleanSMSTheme(
                useDeviceMaterialTheme = state.useDeviceMaterialTheme,
                darkTheme = effectiveDark,
                themeManaged = true
            ) {
                observedBgLuminance = MaterialTheme.colorScheme.background.luminance()
                MessagesScreen(
                    uiState = state,
                    viewModel = viewModel,
                    onPickMedia = {},
                    onRequestSendSmsPermission = {},
                    onRequestDefaultSmsRole = {}
                )
            }
        }

        composeRule.onNodeWithTag("messages_settings_btn").performClick()
        composeRule.waitForIdle()

        val dialogDarkSwitch = composeRule.onNodeWithTag("messages_dialog_dark_mode_switch")
        dialogDarkSwitch.assertIsOn()
        assertTrue((observedBgLuminance ?: 1f) < 0.25f)

        dialogDarkSwitch.performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            viewModel.uiState.value.darkModeOverride == false
        }
        composeRule.waitForIdle()
        dialogDarkSwitch.assertIsOff()
        assertTrue("Expected light background after disabling dark mode", (observedBgLuminance ?: 0f) > 0.5f)
        composeRule.waitUntil(timeoutMillis = 5000L) {
            ShadowLooper.idleMainLooper()
            runBlocking { ThemePreferencesRepository(application).darkModeEnabledFlow.first() == false }
        }
    }
}
