package com.zerodea.bluekt

sealed class BluetoothUpdate {
    class Message(val message: ByteArray): BluetoothUpdate()
    data object NoDeviceSelected: BluetoothUpdate()
    data object DeviceSelected : BluetoothUpdate()
    data object BluetoothNotEnabled: BluetoothUpdate()
    class PermissionsRejected(val rejectedPermissions: List<String>): BluetoothUpdate()
    data object DeviceDisconnected : BluetoothUpdate()
}
