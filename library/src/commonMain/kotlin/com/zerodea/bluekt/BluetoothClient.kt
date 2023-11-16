package com.zerodea.bluekt

import kotlinx.coroutines.flow.Flow

expect class BluetoothClient {
    val bluetoothUpdates: Flow<BluetoothUpdate>

    fun startDiscovery()
    fun onDeviceSelected(device: BluetoothUpdate.DeviceDiscovered.BluetoothDevice)
    fun send(message: ByteArray)
}