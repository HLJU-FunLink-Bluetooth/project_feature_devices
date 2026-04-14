package com.hlju.funlinkbluetooth.feature.devices

import android.content.Context
import com.hlju.funlinkbluetooth.core.nearby.runtime.NearbySessionController
import com.hlju.funlinkbluetooth.core.nearby.runtime.NearbySessionState
import com.hlju.funlinkbluetooth.core.preferences.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DevicesControllerTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sessionController: NearbySessionController
    private lateinit var stateFlow: MutableStateFlow<NearbySessionState>

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("funlink_nearby_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settingsRepository = SettingsRepository(context)

        sessionController = mockk(relaxed = true)
        stateFlow = MutableStateFlow(NearbySessionState())
        every { sessionController.state } returns stateFlow
    }

    @Test
    fun init_readsPersistedNames() {
        settingsRepository.setHostRoomName("  host-room  ")
        settingsRepository.setClientConnectionName("  client-name  ")

        val controller = DevicesController(sessionController, settingsRepository)

        assertEquals("host-room", controller.roomNameInput)
        assertEquals("client-name", controller.clientNameInput)
    }

    @Test
    fun updateInput_persistsToSettingsRepository() {
        val controller = DevicesController(sessionController, settingsRepository)

        controller.updateRoomName(" room-value ")
        controller.updateClientName(" client-value ")

        assertEquals("room-value", settingsRepository.getHostRoomName())
        assertEquals("client-value", settingsRepository.getClientConnectionName())
    }

    @Test
    fun startActions_trimAndPersistValuesBeforeDispatch() {
        val controller = DevicesController(sessionController, settingsRepository)
        controller.onPermissionsResult(true)
        controller.updateRoomName("  room-a  ")
        controller.updateClientName("  client-a  ")

        controller.startHostBroadcast()
        controller.startClientScan()

        assertEquals("room-a", controller.roomNameInput)
        assertEquals("client-a", controller.clientNameInput)
        assertEquals("room-a", settingsRepository.getHostRoomName())
        assertEquals("client-a", settingsRepository.getClientConnectionName())
        verify { sessionController.startAdvertising("room-a") }
        verify { sessionController.startDiscovery() }
    }

    @Test
    fun startHostBroadcast_blankRoomName_reportsValidationError() {
        val controller = DevicesController(sessionController, settingsRepository)
        controller.onPermissionsResult(true)
        controller.updateRoomName("   ")

        controller.startHostBroadcast()

        verify { sessionController.onValidationError("请输入房间标识") }
        verify(exactly = 0) { sessionController.startAdvertising(any()) }
    }

    @Test
    fun startHostBroadcast_whileAdvertisingStartPending_ignoresRepeatedClick() {
        stateFlow.value = NearbySessionState(isStartingAdvertising = true)
        val controller = DevicesController(sessionController, settingsRepository)
        controller.onPermissionsResult(true)
        controller.updateRoomName("room-a")

        controller.startHostBroadcast()

        verify(exactly = 0) { sessionController.startAdvertising(any()) }
    }

    @Test
    fun startClientScan_whileDiscoveryStartPending_ignoresRepeatedClick() {
        stateFlow.value = NearbySessionState(isStartingDiscovery = true)
        val controller = DevicesController(sessionController, settingsRepository)
        controller.onPermissionsResult(true)
        controller.updateClientName("client-a")

        controller.startClientScan()

        verify(exactly = 0) { sessionController.startDiscovery() }
    }

    @Test
    fun deniedPermissions_clearPendingFlags_andAllowNextStart() {
        val controller = DevicesController(sessionController, settingsRepository)
        controller.updateRoomName("room-a")
        controller.updateClientName("client-a")

        controller.startHostBroadcast()
        controller.onPermissionsResult(false)
        assertFalse(controller.pendingStartHost)

        controller.onPermissionsResult(true)
        controller.startHostBroadcast()
        verify { sessionController.startAdvertising("room-a") }

        controller.onPermissionsResult(false)
        controller.startClientScan()
        controller.onPermissionsResult(false)
        assertFalse(controller.pendingStartScan)

        controller.onPermissionsResult(true)
        controller.startClientScan()
        verify { sessionController.startDiscovery() }
    }
}
