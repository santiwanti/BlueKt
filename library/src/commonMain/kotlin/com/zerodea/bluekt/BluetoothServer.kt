package com.zerodea.bluekt

import kotlinx.coroutines.flow.Flow

expect class BluetoothServer {
    val bluetoothUpdates: Flow<BluetoothUpdate>

    fun startServer()
    fun send(message: ByteArray)
}