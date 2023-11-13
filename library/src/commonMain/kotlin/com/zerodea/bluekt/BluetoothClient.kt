package com.zerodea.bluekt

import kotlinx.coroutines.flow.Flow

expect class BluetoothClient {
    val bluetoothUpdates: Flow<BluetoothUpdate>

    fun startDiscovery()
    fun send(message: ByteArray)
    fun onReceive()
}