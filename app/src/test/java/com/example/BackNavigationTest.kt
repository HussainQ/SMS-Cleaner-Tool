package com.example

import android.app.Application
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import com.example.ui.CleanSmsViewModel
import com.example.ui.theme.CleanSMSTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun viewModel_tabBackStack_tracksOrderOfClicksAndPopsInReverse() {
        val vm = CleanSmsViewModel(app)
        assertEquals(NavigationTab.MESSAGES, vm.uiState.value.currentTab)
        assertEquals(listOf(NavigationTab.MESSAGES), vm.uiState.value.tabBackStack)

        // Click Cleanup -> Settings -> Vault -> Cleanup (re-focusing moves Cleanup to top)
        vm.navigateToTab(NavigationTab.CLEANUP)
        vm.navigateToTab(NavigationTab.SETTINGS)
        vm.navigateToTab(NavigationTab.VAULT)
        vm.navigateToTab(NavigationTab.CLEANUP)

        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)
        assertEquals(
            listOf(NavigationTab.MESSAGES, NavigationTab.SETTINGS, NavigationTab.VAULT, NavigationTab.CLEANUP),
            vm.uiState.value.tabBackStack
        )

        // Back 1 -> Vault
        assertTrue(vm.handleBackPress())
        assertEquals(NavigationTab.VAULT, vm.uiState.value.currentTab)

        // Back 2 -> Settings
        assertTrue(vm.handleBackPress())
        assertEquals(NavigationTab.SETTINGS, vm.uiState.value.currentTab)

        // Back 3 -> Messages root
        assertTrue(vm.handleBackPress())
        assertEquals(NavigationTab.MESSAGES, vm.uiState.value.currentTab)

        // Back 4 at root -> returns false so system closes the app
        assertFalse(vm.handleBackPress())
        assertEquals(listOf(NavigationTab.MESSAGES), vm.uiState.value.tabBackStack)
    }

    @Test
    fun viewModel_handleBackPress_respectsOverlaySearchCategorySubTabHierarchy() {
        val vm = CleanSmsViewModel(app)
        vm.navigateToCleanupReview()
        vm.setCategoryTab(SmsCategory.OTP)
        vm.setSearchQuery("verify")
        vm.showMessageDetails(
            SmsMessageItem(
                id = "msg-1",
                address = "Auth",
                body = "Your verify code is 123456",
                date = 1_700_000_000_000L
            )
        )

        // 1. Highest priority: Window overlay closes first without altering search/category/tab
        assertTrue(vm.handleBackPress())
        assertNull(vm.uiState.value.detailMessage)
        assertEquals("verify", vm.uiState.value.searchQuery)
        assertEquals(SmsCategory.OTP, vm.uiState.value.selectedCategoryTab)
        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)
        assertEquals(1, vm.uiState.value.cleanupSubTab)

        // 2. Active search clears next without changing category/sub-tab
        assertTrue(vm.handleBackPress())
        assertEquals("", vm.uiState.value.searchQuery)
        assertEquals(SmsCategory.OTP, vm.uiState.value.selectedCategoryTab)
        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)
        assertEquals(1, vm.uiState.value.cleanupSubTab)

        // 3. Secondary category filter resets to ALL before leaving Review sub-tab
        assertTrue(vm.handleBackPress())
        assertEquals(SmsCategory.ALL, vm.uiState.value.selectedCategoryTab)
        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)
        assertEquals(1, vm.uiState.value.cleanupSubTab)

        // 4. Review sub-tab steps back to Scan sub-tab (0)
        assertTrue(vm.handleBackPress())
        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)
        assertEquals(0, vm.uiState.value.cleanupSubTab)

        // 5. Now Cleanup tab pops back to Messages landing root
        assertTrue(vm.handleBackPress())
        assertEquals(NavigationTab.MESSAGES, vm.uiState.value.currentTab)

        // 6. At Messages root with no overlays/sub-levels -> allow exit
        assertFalse(vm.handleBackPress())
    }

    @Test
    fun viewModel_handleBackPress_stepsThroughCleanupRulesAndVaultSubTabsBeforePoppingTab() {
        val vm = CleanSmsViewModel(app)
        vm.navigateToTab(NavigationTab.VAULT)
        vm.setVaultSubTab(1) // Deleted Test
        vm.navigateToTab(NavigationTab.CLEANUP)
        vm.setCleanupSubTab(2) // Rules

        // Cleanup (Rules) -> Cleanup (Scan)
        assertTrue(vm.handleBackPress())
        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)
        assertEquals(0, vm.uiState.value.cleanupSubTab)

        // Pops Cleanup -> Vault (Deleted Test)
        assertTrue(vm.handleBackPress())
        assertEquals(NavigationTab.VAULT, vm.uiState.value.currentTab)
        assertEquals(1, vm.uiState.value.vaultSubTab)

        // Resets Vault (Deleted Test) -> Vault (Quarantine)
        assertTrue(vm.handleBackPress())
        assertEquals(NavigationTab.VAULT, vm.uiState.value.currentTab)
        assertEquals(0, vm.uiState.value.vaultSubTab)

        // Pops Vault -> Messages root
        assertTrue(vm.handleBackPress())
        assertEquals(NavigationTab.MESSAGES, vm.uiState.value.currentTab)
    }

    @Test
    fun composeUi_backHandlers_dismissOverlaysSubLevelsAndTabOrderBeforeAllowingExit() {
        val vm = CleanSmsViewModel(app)
        var dispatcherOwner: androidx.activity.OnBackPressedDispatcherOwner? = null

        composeRule.setContent {
            dispatcherOwner = LocalOnBackPressedDispatcherOwner.current
            CleanSMSTheme {
                CleanSmsApp(viewModel = vm)
            }
        }

        // At Messages root with no modals: BackHandler should be disabled so OS can exit
        composeRule.runOnIdle {
            assertFalse(dispatcherOwner!!.onBackPressedDispatcher.hasEnabledCallbacks())
        }

        // Click order: Messages -> Vault (Deleted Test) -> Cleanup > Review (OTP + search) -> Settings -> App Updates dialog
        composeRule.onNodeWithTag("nav_tab_vault").performClick()
        composeRule.onNodeWithTag("vault_tab_deleted_test").performClick()

        composeRule.onNodeWithTag("nav_tab_cleanup").performClick()
        composeRule.onNodeWithTag("cleanup_tab_review").performClick()
        composeRule.onNodeWithTag("chip_otp").performClick()
        composeRule.onNodeWithTag("preview_search_input").performTextInput("1234")
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_tab_settings").performClick()
        composeRule.onNodeWithTag("cloud_pane_updates_card")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(vm.uiState.value.showUpdatesDialog)
            assertTrue(dispatcherOwner!!.onBackPressedDispatcher.hasEnabledCallbacks())
        }

        // 1. Dismiss Updates overlay first
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertFalse(vm.uiState.value.showUpdatesDialog)
        assertEquals(NavigationTab.SETTINGS, vm.uiState.value.currentTab)

        // 2. Pop top-level tab Settings -> Cleanup (Review)
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)
        assertEquals(1, vm.uiState.value.cleanupSubTab)
        assertEquals("1234", vm.uiState.value.searchQuery)
        assertEquals(SmsCategory.OTP, vm.uiState.value.selectedCategoryTab)

        // 3. Clear Review search query before resetting category chip
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals("", vm.uiState.value.searchQuery)
        assertEquals(SmsCategory.OTP, vm.uiState.value.selectedCategoryTab)
        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)

        // 4. Reset Review category filter OTP -> All
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(SmsCategory.ALL, vm.uiState.value.selectedCategoryTab)
        assertEquals(1, vm.uiState.value.cleanupSubTab)

        // 5. Step back Cleanup Review -> Scan sub-tab
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)
        assertEquals(0, vm.uiState.value.cleanupSubTab)

        // 6. Pop Cleanup -> Vault (still on Deleted Test sub-tab)
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(NavigationTab.VAULT, vm.uiState.value.currentTab)
        assertEquals(1, vm.uiState.value.vaultSubTab)

        // 7. Reset Vault sub-tab Deleted Test -> Quarantine
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(NavigationTab.VAULT, vm.uiState.value.currentTab)
        assertEquals(0, vm.uiState.value.vaultSubTab)

        // 8. Pop Vault -> Messages root; BackHandler now disables to allow system exit
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(NavigationTab.MESSAGES, vm.uiState.value.currentTab)
        composeRule.onNodeWithTag("messages_search_input").assertIsDisplayed()
        assertFalse(dispatcherOwner!!.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun composeUi_openAddRuleSheet_dismissesOnBackBeforeSubTabAndTabPop() {
        val vm = CleanSmsViewModel(app)
        var dispatcherOwner: androidx.activity.OnBackPressedDispatcherOwner? = null

        composeRule.setContent {
            dispatcherOwner = LocalOnBackPressedDispatcherOwner.current
            CleanSMSTheme {
                CleanSmsApp(viewModel = vm)
            }
        }

        // Navigate to Cleanup -> Rules and open New Rule bottom sheet
        composeRule.onNodeWithTag("nav_tab_cleanup").performClick()
        composeRule.onNodeWithTag("cleanup_tab_rules").performClick()
        composeRule.onNodeWithTag("add_rule_fab_button")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(vm.uiState.value.showAddCustomRuleSheet)
            assertTrue(dispatcherOwner!!.onBackPressedDispatcher.hasEnabledCallbacks())
        }

        // Back 1: closes AddCustomRuleSheet without leaving Cleanup > Rules
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertFalse(vm.uiState.value.showAddCustomRuleSheet)
        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)
        assertEquals(2, vm.uiState.value.cleanupSubTab)

        // Back 2: steps Cleanup > Rules -> Cleanup > Scan
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(NavigationTab.CLEANUP, vm.uiState.value.currentTab)
        assertEquals(0, vm.uiState.value.cleanupSubTab)

        // Back 3: pops Cleanup -> Messages root and disables callbacks
        composeRule.runOnIdle { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(NavigationTab.MESSAGES, vm.uiState.value.currentTab)
        assertFalse(dispatcherOwner!!.onBackPressedDispatcher.hasEnabledCallbacks())
    }
}
