package com.zerodea.bluekt

import androidx.activity.ComponentActivity

actual class BluetoothServer(private val activity: ComponentActivity, name: String) :
    BluetoothComponent(activity, ComponentType.Server(name)) {

    override fun onBluetoothEnabled() {
        serialSocket.startServer(activity)
    }

    actual fun startServer() {
        super.startComponent()
    }

    actual fun send(message: ByteArray) {
        serialSocket.write(message)
    }
}