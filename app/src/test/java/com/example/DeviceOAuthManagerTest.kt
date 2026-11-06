package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.cloud.DeviceOAuthManager
import com.example.cloud.LocalAuthTokenStore
import com.example.data.local.AppDatabase
import com.example.data.local.CleanSmsDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeviceOAuthManagerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: CleanSmsDao
    private lateinit var localStore: LocalAuthTokenStore
    private lateinit var manager: DeviceOAuthManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cleanSmsDao()
        localStore = LocalAuthTokenStore(context, dao)
        manager = DeviceOAuthManager(context, dao, localStore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // REQUIREMENT 1: Secrets MUST NOT be pulled or stored without explicit user authorization
    @Test
    fun pullOAuthSecret_deniedOrUnauthorized_failsWithSecurityExceptionAndStoresNothing() = runBlocking {
        // Attempt pull with userConfirmedAuthorization = false
        val result = manager.pullAndStoreDeviceOAuthSecret(
            serviceId = DeviceOAuthManager.SERVICE_GOOGLE_DRIVE,
            userConfirmedAuthorization = false,
            selectedAccount = "shqsid@gmail.com"
        )

        // 1. Result must fail
        assertTrue("Result must be failure when user did not authorize", result.isFailure)
        assertTrue(
            "Exception should be SecurityException",
            result.exceptionOrNull() is SecurityException
        )

        // 2. Local storage must contain NOTHING
        assertFalse(
            "Service must NOT be authorized in local store",
            localStore.isAuthorized(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE)
        )
        assertNull(
            "No credential must exist in local store",
            localStore.getCredential(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE)
        )
        assertNull(
            "No credential must exist in database",
            dao.getCredentialByServiceId(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE)
        )
        assertEquals("Database must be empty", 0, dao.getAllCredentials().size)
    }

    // REQUIREMENT 2: When authorized, automatically pull device secret and keep locally
    @Test
    fun pullOAuthSecret_whenAuthorized_pullsDeviceSecretAndKeepsLocally() = runBlocking {
        val testAccount = "shqsid@gmail.com"

        // Authorize Google Drive
        val result = manager.pullAndStoreDeviceOAuthSecret(
            serviceId = DeviceOAuthManager.SERVICE_GOOGLE_DRIVE,
            userConfirmedAuthorization = true,
            selectedAccount = testAccount
        )

        // 1. Must succeed
        assertTrue("Pulling secret must succeed when authorized", result.isSuccess)
        val cred = result.getOrNull()
        assertNotNull(cred)

        // 2. Inspect credential details
        assertEquals(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE, cred?.serviceId)
        assertEquals("Google Drive", cred?.serviceName)
        assertEquals(testAccount, cred?.accountName)
        assertTrue("Access token must not be blank", !cred?.accessToken.isNullOrBlank())
        assertTrue("Token must be locally stored", cred?.isLocallyStored == true)

        // 3. Secret must be safely masked for UI display
        val masked = cred?.maskedToken ?: ""
        assertTrue("Masked token must hide raw secret", masked.contains("••••"))
        assertNotEquals("Masked token must not be equal to raw access token", cred?.accessToken, masked)

        // 4. Local storage verification
        assertTrue(
            "Local store must report service as authorized",
            localStore.isAuthorized(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE)
        )
        val storedCred = localStore.getCredential(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE)
        assertNotNull("Stored credential must be retrievable locally", storedCred)
        assertEquals(cred?.accessToken, storedCred?.accessToken)

        // 5. Room Database verification
        val dbEntity = dao.getCredentialByServiceId(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE)
        assertNotNull("Room DB must contain the stored entity", dbEntity)
        assertEquals(testAccount, dbEntity?.accountName)
        assertEquals(cred?.accessToken, dbEntity?.accessToken)
    }

    // REQUIREMENT 3: Multiple services (Dropbox & Google Drive) isolated locally
    @Test
    fun pullOAuthSecret_multipleServices_storedIndependently() = runBlocking {
        // Authorize Dropbox only
        val result = manager.pullAndStoreDeviceOAuthSecret(
            serviceId = DeviceOAuthManager.SERVICE_DROPBOX,
            userConfirmedAuthorization = true,
            selectedAccount = "shqsid@gmail.com"
        )
        assertTrue(result.isSuccess)

        // Dropbox is authorized, Google Drive is NOT
        assertTrue(manager.isServiceAuthorized(DeviceOAuthManager.SERVICE_DROPBOX))
        assertFalse(manager.isServiceAuthorized(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE))

        // Now authorize Google Drive
        val driveResult = manager.pullAndStoreDeviceOAuthSecret(
            serviceId = DeviceOAuthManager.SERVICE_GOOGLE_DRIVE,
            userConfirmedAuthorization = true,
            selectedAccount = "shqsid@gmail.com"
        )
        assertTrue(driveResult.isSuccess)

        // Both are now authorized locally
        assertTrue(manager.isServiceAuthorized(DeviceOAuthManager.SERVICE_DROPBOX))
        assertTrue(manager.isServiceAuthorized(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE))
        assertEquals(2, dao.getAllCredentials().size)
    }

    // REQUIREMENT 4: Revocation immediately purges secrets from local device
    @Test
    fun revokeAuthorization_purgesSecretFromLocalDeviceImmediately() = runBlocking {
        // 1. Authorize
        manager.pullAndStoreDeviceOAuthSecret(
            serviceId = DeviceOAuthManager.SERVICE_GOOGLE_DRIVE,
            userConfirmedAuthorization = true,
            selectedAccount = "shqsid@gmail.com"
        )
        assertTrue(manager.isServiceAuthorized(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE))

        // 2. Revoke
        val revokeResult = manager.revokeAuthorization(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE)
        assertTrue("Revocation must succeed", revokeResult.isSuccess)

        // 3. Confirm complete erasure from local storage and database
        assertFalse(
            "Service must NO LONGER be authorized",
            manager.isServiceAuthorized(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE)
        )
        assertNull(
            "Local store must not return credential",
            manager.getStoredCredential(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE)
        )
        assertNull(
            "Database entity must be deleted",
            dao.getCredentialByServiceId(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE)
        )
    }

    // REQUIREMENT 5: Purge all local secrets
    @Test
    fun purgeAllSecrets_clearsAllServicesCompletely() = runBlocking {
        // Authorize both
        manager.pullAndStoreDeviceOAuthSecret(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE, true, "shqsid@gmail.com")
        manager.pullAndStoreDeviceOAuthSecret(DeviceOAuthManager.SERVICE_DROPBOX, true, "shqsid@gmail.com")
        assertEquals(2, dao.getAllCredentials().size)

        // Purge all
        val purgeResult = manager.purgeAllSecrets()
        assertTrue(purgeResult.isSuccess)

        // Verify zero secrets remain
        assertFalse(manager.isServiceAuthorized(DeviceOAuthManager.SERVICE_GOOGLE_DRIVE))
        assertFalse(manager.isServiceAuthorized(DeviceOAuthManager.SERVICE_DROPBOX))
        assertEquals(0, dao.getAllCredentials().size)
    }

    // REQUIREMENT 6: Detect available device accounts
    @Test
    fun getDetectedDeviceAccounts_returnsDefaultOrSystemAccounts() {
        val accounts = manager.getDetectedDeviceAccounts()
        assertTrue("Should return at least default user account", accounts.isNotEmpty())
        assertTrue("Should contain shqsid@gmail.com", accounts.contains("shqsid@gmail.com"))
    }
}
