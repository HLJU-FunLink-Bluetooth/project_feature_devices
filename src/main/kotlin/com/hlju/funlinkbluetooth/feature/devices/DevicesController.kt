package com.hlju.funlinkbluetooth.feature.devices

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.hlju.funlinkbluetooth.core.model.ConnectionRole
import com.hlju.funlinkbluetooth.core.nearby.runtime.NearbySessionController
import com.hlju.funlinkbluetooth.core.preferences.SettingsRepository

class DevicesController(
    val sessionController: NearbySessionController,
    private val settingsRepository: SettingsRepository
) {

    val state get() = sessionController.state

    var roomNameInput by mutableStateOf(settingsRepository.getHostRoomName())
        private set
    var clientNameInput by mutableStateOf(settingsRepository.getClientConnectionName())
        private set

    var hasPermissions by mutableStateOf(false)
        private set
    var pendingStartHost by mutableStateOf(false)
        private set
    var pendingStartScan by mutableStateOf(false)
        private set
    var showRoleSwitchConfirmDialog by mutableStateOf(false)
        private set
    var pendingRoleSwitchTarget by mutableStateOf<ConnectionRole?>(null)
        private set

    fun updateRoomName(name: String) {
        roomNameInput = name
        settingsRepository.setHostRoomName(name)
    }

    fun updateClientName(name: String) {
        clientNameInput = name
        settingsRepository.setClientConnectionName(name)
    }

    fun checkPermissions(context: Context, permissions: Array<String>): Boolean {
        val granted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        hasPermissions = granted
        return granted
    }

    fun onPermissionsResult(granted: Boolean) {
        hasPermissions = granted
        if (!granted) {
            pendingStartHost = false
            pendingStartScan = false
            return
        }
        if (pendingStartHost) {
            pendingStartHost = false
            doStartHostBroadcast()
        } else if (pendingStartScan) {
            pendingStartScan = false
            doStartClientScan()
        }
    }

    fun startHostBroadcast() {
        val normalized = roomNameInput.trim()
        roomNameInput = normalized
        settingsRepository.setHostRoomName(normalized)
        if (normalized.isBlank()) return
        if (hasPermissions) {
            doStartHostBroadcast()
        } else {
            pendingStartHost = true
            pendingStartScan = false
        }
    }

    fun stopHostBroadcast() {
        sessionController.stopAdvertising()
    }

    fun startClientScan() {
        val normalized = clientNameInput.trim()
        clientNameInput = normalized
        settingsRepository.setClientConnectionName(normalized)
        if (normalized.isBlank()) {
            sessionController.onValidationError("请输入连接名")
            return
        }
        if (hasPermissions) {
            doStartClientScan()
        } else {
            pendingStartHost = false
            pendingStartScan = true
        }
    }

    fun stopClientScan() {
        sessionController.stopDiscovery()
    }

    fun requestRoleSwitch(targetRole: ConnectionRole) {
        if (targetRole == sessionController.state.value.role) return
        if (sessionController.state.value.connectedEndpoints.isNotEmpty()) {
            pendingRoleSwitchTarget = targetRole
            showRoleSwitchConfirmDialog = true
            return
        }
        applyRoleSwitch(targetRole)
    }

    fun confirmRoleSwitch() {
        val target = pendingRoleSwitchTarget ?: return
        showRoleSwitchConfirmDialog = false
        pendingRoleSwitchTarget = null
        applyRoleSwitch(target)
    }

    fun cancelRoleSwitch() {
        showRoleSwitchConfirmDialog = false
        pendingRoleSwitchTarget = null
    }

    fun rejectIncomingConnection(endpointId: String) {
        sessionController.rejectConnection(endpointId)
    }

    fun acceptIncomingConnection(endpointId: String) {
        sessionController.acceptConnection(endpointId)
    }

    fun requestConnectionToEndpoint(endpointId: String) {
        sessionController.requestConnection(endpointId)
    }

    fun stopDiscovery() {
        sessionController.stopDiscovery()
    }

    private fun doStartHostBroadcast() {
        if (sessionController.state.value.isDiscovering) {
            sessionController.stopDiscovery()
        }
        sessionController.startAdvertising(roomNameInput.trim())
    }

    private fun doStartClientScan() {
        sessionController.startDiscovery()
    }

    private fun applyRoleSwitch(targetRole: ConnectionRole) {
        sessionController.stopDiscovery()
        sessionController.stopAdvertising()
        sessionController.stopAllEndpoints()
        sessionController.setRole(targetRole)
        pendingStartHost = false
        pendingStartScan = false
    }
}
