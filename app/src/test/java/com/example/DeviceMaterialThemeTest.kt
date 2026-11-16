package com.example

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ui.CleanSmsViewModel
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.RulesAndCloudScreen
import com.example.ui.theme.CleanSMSTheme
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.SurfaceDark
import com.example.update.AppUpgradeManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun settingsToggle_inRulesAndCloudScreen_togglesAndPersistsDeviceMaterialTheme() {
        val viewModel = CleanSmsViewModel(application)

        var observedBackground: Color? = null
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            CleanSMSTheme(
                useDeviceMaterialTheme = state.useDeviceMaterialTheme,
                themeManaged = true
            ) {
                observedBackground = MaterialTheme.colorScheme.background
                RulesAndCloudScreen(uiState = state, viewModel = viewModel)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rules_cloud_tab_cloud").performClick()
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
}
