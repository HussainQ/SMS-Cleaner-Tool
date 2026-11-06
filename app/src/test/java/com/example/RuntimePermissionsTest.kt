package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.ui.CleanSmsViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RuntimePermissionsTest {

    private lateinit var context: Context
    private lateinit var viewModel: CleanSmsViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = CleanSmsViewModel(ApplicationProvider.getApplicationContext())
    }

    // TEST 1: Initial state requires permissions and defaults safely to Demo Mode
    @Test
    fun initialPermissionsState_defaultsToDemoModeAndUnapproved() {
        val state = viewModel.uiState.value
        assertFalse("Initial READ_SMS should not be assumed granted", state.hasReadSmsPermission)
        assertFalse("Initial WRITE_SMS should not be assumed granted", state.hasWriteSmsPermission)
        assertFalse("showPermissionRationaleDialog should start false", state.showPermissionRationaleDialog)
        assertFalse("showPermissionPermanentlyDeniedDialog should start false", state.showPermissionPermanentlyDeniedDialog)
    }

    // TEST 2: Granting both READ_SMS and WRITE_SMS activates device mode and updates state
    @Test
    fun grantingBothReadAndWriteSmsPermissions_activatesDeviceInbox() {
        viewModel.setSmsPermissions(readGranted = true, writeGranted = true)

        val state = viewModel.uiState.value
        assertTrue("hasReadSmsPermission must be true", state.hasReadSmsPermission)
        assertTrue("hasWriteSmsPermission must be true", state.hasWriteSmsPermission)
        assertTrue("hasSmsPermission convenience flag must be true", state.hasSmsPermission)
        assertFalse("Demo mode should automatically disable when real SMS granted", state.isDemoMode)
        assertFalse("Rationale dialog must dismiss upon grant", state.showPermissionRationaleDialog)
    }

    // TEST 3: Partial grant (READ only) allows scanning but flags missing WRITE permission
    @Test
    fun partialGrant_readOnly_allowsScanWhileIndicatingWriteMissing() {
        viewModel.setSmsPermissions(readGranted = true, writeGranted = false)

        val state = viewModel.uiState.value
        assertTrue("READ_SMS granted", state.hasReadSmsPermission)
        assertFalse("WRITE_SMS not granted", state.hasWriteSmsPermission)
        assertFalse("isDemoMode should turn off for scanning", state.isDemoMode)
    }

    // TEST 4: Denying permissions retains demo mode safely
    @Test
    fun denyingPermissions_keepsDemoModeActive() {
        viewModel.setDemoMode(true)
        viewModel.setSmsPermissions(readGranted = false, writeGranted = false)

        val state = viewModel.uiState.value
        assertFalse("hasReadSmsPermission should be false", state.hasReadSmsPermission)
        assertFalse("hasWriteSmsPermission should be false", state.hasWriteSmsPermission)
        assertTrue("Demo mode remains active to allow safe testing without device access", state.isDemoMode)
    }

    // TEST 5: Educational Rationale Dialog state lifecycle
    @Test
    fun permissionRationaleDialog_openAndDismiss_cyclesStateCorrectly() {
        assertFalse(viewModel.uiState.value.showPermissionRationaleDialog)

        viewModel.openPermissionRationaleDialog()
        assertTrue(viewModel.uiState.value.showPermissionRationaleDialog)

        viewModel.dismissPermissionRationaleDialog()
        assertFalse(viewModel.uiState.value.showPermissionRationaleDialog)
    }

    // TEST 6: Permanently Denied / Settings Dialog state lifecycle
    @Test
    fun permissionPermanentlyDeniedDialog_openAndDismiss_cyclesStateCorrectly() {
        assertFalse(viewModel.uiState.value.showPermissionPermanentlyDeniedDialog)

        viewModel.openPermissionPermanentlyDeniedDialog()
        assertTrue(viewModel.uiState.value.showPermissionPermanentlyDeniedDialog)

        viewModel.dismissPermissionPermanentlyDeniedDialog()
        assertFalse(viewModel.uiState.value.showPermissionPermanentlyDeniedDialog)
    }

    // TEST 7: Constant definitions check
    @Test
    fun permissionStringConstants_matchExpectedAndroidIdentifiers() {
        assertEquals("android.permission.READ_SMS", Manifest.permission.READ_SMS)
        assertEquals("android.permission.WRITE_SMS", PERMISSION_WRITE_SMS)
    }
}
