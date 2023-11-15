package com.zerodea.bluekt

sealed class BluetoothUpdate {
    class Message(val message: ByteArray) : BluetoothUpdate()
    class DeviceDiscovered(val device: BluetoothDevice) : BluetoothUpdate() {
        data class BluetoothDevice(val name: String, val macAddress: String, val uuids: List<String> = listOf())
    }

    data object NoDeviceSelected : BluetoothUpdate()
    data object DeviceSelected : BluetoothUpdate()
    data object BluetoothNotEnabled : BluetoothUpdate()
    class PermissionsRejected(val rejectedPermissions: List<String>) : BluetoothUpdate()
    data object DeviceConnected : BluetoothUpdate()
    data object DeviceDisconnected : BluetoothUpdate()
    data object DeviceNotFound : BluetoothUpdate()
}
