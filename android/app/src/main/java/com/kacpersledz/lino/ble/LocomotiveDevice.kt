package com.kacpersledz.lino.ble

data class LocomotiveDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

data class LocomotiveCommand(
    val speed: Int,      // 0-100%
    val direction: Int   // 0 = reverse, 1 = forward
) {
    fun toByteArray(): ByteArray {
        return byteArrayOf(speed.toByte(), direction.toByte())
    }

    companion object {
        fun stop() = LocomotiveCommand(0, 1)
    }
}
