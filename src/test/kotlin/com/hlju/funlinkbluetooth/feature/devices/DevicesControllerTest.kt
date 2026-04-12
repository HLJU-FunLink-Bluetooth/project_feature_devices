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

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("funlink_nearby_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settingsRepository = SettingsRepository(context)

        sessionController = mockk(relaxed = true)
        every { sessionController.state } returns MutableStateFlow(NearbySessionState())
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
}
