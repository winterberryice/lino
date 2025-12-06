package com.kacpersledz.lino.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kacpersledz.lino.ble.BleManager
import com.kacpersledz.lino.ble.LocomotiveDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager.getInstance(application)

    val discoveredDevices: StateFlow<List<LocomotiveDevice>> = bleManager.discoveredDevices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isScanning: StateFlow<Boolean> = bleManager.isScanning
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun startScanning() {
        viewModelScope.launch {
            bleManager.startScanning()
        }
    }

    fun stopScanning() {
        viewModelScope.launch {
            bleManager.stopScanning()
        }
    }

    fun connectToDevice(device: LocomotiveDevice) {
        viewModelScope.launch {
            bleManager.connect(device)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stopScanning()
    }
}
