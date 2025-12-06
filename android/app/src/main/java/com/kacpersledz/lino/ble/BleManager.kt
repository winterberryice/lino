package com.kacpersledz.lino.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleManager private constructor(private val context: Context) {

    companion object {
        private val SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
        private val CHARACTERISTIC_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
        private const val DEVICE_NAME_PREFIX = "F7_Loko_"

        @Volatile
        private var instance: BleManager? = null

        fun getInstance(context: Context): BleManager {
            return instance ?: synchronized(this) {
                instance ?: BleManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null

    private val _discoveredDevices = MutableStateFlow<List<LocomotiveDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<LocomotiveDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<LocomotiveDevice?>(null)
    val connectedDevice: StateFlow<LocomotiveDevice?> = _connectedDevice.asStateFlow()

    private val _isControlReady = MutableStateFlow(false)
    val isControlReady: StateFlow<Boolean> = _isControlReady.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown"

            // Filtrujemy po UUID FFE0 (tylko F7 lokomotywy)
            val locomotiveDevice = LocomotiveDevice(
                name = deviceName,
                address = device.address,
                rssi = result.rssi
            )

            val currentDevices = _discoveredDevices.value.toMutableList()
            val existingIndex = currentDevices.indexOfFirst { it.address == locomotiveDevice.address }

            if (existingIndex != -1) {
                currentDevices[existingIndex] = locomotiveDevice
            } else {
                currentDevices.add(locomotiveDevice)
            }

            _discoveredDevices.value = currentDevices.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _connectedDevice.value = null
                    _isControlReady.value = false
                    cleanup()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                controlCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

                // Update control ready state
                _isControlReady.value = controlCharacteristic != null
            } else {
                _isControlReady.value = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (bluetoothAdapter?.isEnabled != true) return

        _discoveredDevices.value = emptyList()

        // Filtrowanie po UUID serwisu FFE0 (tylko F7 lokomotywy)
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        _isScanning.value = true
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        bleScanner?.stopScan(scanCallback)
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun connect(device: LocomotiveDevice) {
        stopScanning()

        _connectionState.value = ConnectionState.CONNECTING
        _connectedDevice.value = device

        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        bluetoothGatt = bluetoothDevice?.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: LocomotiveCommand): Boolean {
        val characteristic = controlCharacteristic ?: return false
        val gatt = bluetoothGatt ?: return false

        if (_connectionState.value != ConnectionState.CONNECTED) return false

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                command.toByteArray(),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = command.toByteArray()
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    fun sendStop() {
        sendCommand(LocomotiveCommand.stop())
    }

    private fun cleanup() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        controlCharacteristic = null
    }

    fun release() {
        stopScanning()
        disconnect()
        cleanup()
    }
}
