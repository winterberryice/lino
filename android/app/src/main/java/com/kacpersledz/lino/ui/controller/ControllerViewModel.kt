package com.kacpersledz.lino.ui.controller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kacpersledz.lino.ble.BleManager
import com.kacpersledz.lino.ble.ConnectionState
import com.kacpersledz.lino.ble.LocomotiveCommand
import com.kacpersledz.lino.ble.LocomotiveDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ControllerViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager.getInstance(application)

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionState.DISCONNECTED
        )

    val connectedDevice: StateFlow<LocomotiveDevice?> = bleManager.connectedDevice
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isControlReady: StateFlow<Boolean> = bleManager.isControlReady
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _speed = MutableStateFlow(0)
    val speed: StateFlow<Int> = _speed.asStateFlow()

    private val _isForward = MutableStateFlow(true)
    val isForward: StateFlow<Boolean> = _isForward.asStateFlow()

    fun setSpeed(newSpeed: Int) {
        _speed.value = newSpeed.coerceIn(0, 100)
        sendCurrentCommand()
    }

    fun setDirection(forward: Boolean) {
        _isForward.value = forward
        sendCurrentCommand()
    }

    fun toggleDirection() {
        _isForward.value = !_isForward.value
        sendCurrentCommand()
    }

    fun stop() {
        _speed.value = 0
        viewModelScope.launch {
            bleManager.sendStop()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bleManager.disconnect()
        }
    }

    private fun sendCurrentCommand() {
        viewModelScope.launch {
            val command = LocomotiveCommand(
                speed = _speed.value,
                direction = if (_isForward.value) 1 else 0
            )
            bleManager.sendCommand(command)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Don't release the singleton, just stop sending commands
        // Connection will be maintained or handled by user action
    }
}
