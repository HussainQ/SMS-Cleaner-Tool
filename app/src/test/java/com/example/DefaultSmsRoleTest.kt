package com.example

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.model.SmsMessageItem
import com.example.telephony.TelephonyManagerHelper
import com.example.ui.CleanSmsViewModel
import com.example.ui.components.PermanentDeleteConfirmDialog
import com.example.ui.screens.TelephonyBanner
import com.example.ui.theme.CleanSMSTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultSmsRoleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Suppress("DEPRECATION")
    @Test
    fun telephonyHelperAndViewModel_whenNotDefaultSmsApp_detectsFalse() {
        val roleManager = context.getSystemService(RoleManager::class.java)
        val shadowRole = shadowOf(roleManager)
        shadowRole.addAvailableRole(RoleManager.ROLE_SMS)

        assertFalse(TelephonyManagerHelper.isDefaultSmsApp(context))
        val vm = CleanSmsViewModel(context.applicationContext as Application)
        assertFalse(vm.checkTelephonyState())
        assertFalse(vm.uiState.value.isDefaultSmsApp)
    }

    @Suppress("DEPRECATION")
    @Test
    fun telephonyHelperAndViewModel_whenHoldingSmsRole_detectsDefaultSmsAppTrue() {
        val roleManager = context.getSystemService(RoleManager::class.java)
        val shadowRole = shadowOf(roleManager)
        shadowRole.addAvailableRole(RoleManager.ROLE_SMS)

        val vm = CleanSmsViewModel(context.applicationContext as Application)
        assertFalse(vm.checkTelephonyState())
        assertFalse(vm.uiState.value.isDefaultSmsApp)

        // Grant ROLE_SMS and verify checkTelephonyState detects it and updates UiState
        shadowRole.addHeldRole(RoleManager.ROLE_SMS)
        assertTrue(TelephonyManagerHelper.isDefaultSmsApp(context))
        assertEquals(context.packageName, TelephonyManagerHelper.getDefaultSmsPackage(context))
        assertTrue(vm.checkTelephonyState())
        assertTrue(vm.uiState.value.isDefaultSmsApp)
    }

    @Test
    fun telephonyBanner_showsStatusIndicatorInsteadOfRequestButtonWhenDefault() {
        var isDefault by mutableStateOf(false)

        composeRule.setContent {
            CleanSMSTheme {
                TelephonyBanner(
                    isDefaultSmsApp = isDefault,
                    previousAppName = "Google Messages",
                    onRequestDefaultSmsRole = {},
                    onHandBackDefaultSmsRole = {}
                )
            }
        }

        // Not default -> shows request button, hides status indicator
        composeRule.onNodeWithTag("default_sms_needed_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("set_default_sms_button").assertIsDisplayed()
        composeRule.onNodeWithTag("default_sms_app_status_indicator").assertDoesNotExist()

        // Switch to default -> hides request button, shows 'Default SMS App' status indicator
        composeRule.runOnIdle { isDefault = true }
        composeRule.onNodeWithTag("default_sms_active_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("default_sms_app_status_indicator").assertIsDisplayed()
        composeRule.onNodeWithText("Default SMS App").assertIsDisplayed()
        composeRule.onNodeWithTag("hand_back_default_sms_button").assertIsDisplayed()
        composeRule.onNodeWithTag("set_default_sms_button").assertDoesNotExist()
    }

    @Test
    fun permanentDeleteDialog_reflectsDefaultSmsStatusInDeviceMode() {
        var isDefault by mutableStateOf(false)
        val sampleMessages = listOf(
            SmsMessageItem(id = "1", address = "PROMO", body = "Sample promo text", date = 1000L)
        )

        composeRule.setContent {
            CleanSMSTheme {
                PermanentDeleteConfirmDialog(
                    messages = sampleMessages,
                    isFromQuarantine = false,
                    testingRestoreEnabled = false,
                    isDemoMode = false,
                    isDefaultSmsApp = isDefault,
                    onRequestDefaultSmsRole = {},
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("delete_dialog_set_default_sms_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_dialog_default_sms_indicator").assertDoesNotExist()

        composeRule.runOnIdle { isDefault = true }
        composeRule.onNodeWithTag("delete_dialog_default_sms_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_dialog_set_default_sms_btn").assertDoesNotExist()
    }
}
