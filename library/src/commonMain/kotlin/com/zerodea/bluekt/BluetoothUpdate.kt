package com.zerodea.bluekt

sealed class BluetoothUpdate {
    class Message(val message: ByteArray): BluetoothUpdate()
    class DeviceDiscovered(val device: BluetoothDevice): BluetoothUpdate(){
        data class BluetoothDevice(val name: String, val macAddress: String)
    }
    data object NoDeviceSelected: BluetoothUpdate()
    data object DeviceSelected : BluetoothUpdate()
    data object BluetoothNotEnabled: BluetoothUpdate()
    class PermissionsRejected(val rejectedPermissions: List<String>): BluetoothUpdate()
    data object DeviceDisconnected : BluetoothUpdate()
}
