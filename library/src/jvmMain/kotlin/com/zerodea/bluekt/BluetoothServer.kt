package com.zerodea.bluekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.bluetooth.RemoteDevice
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnectionNotifier

actual class BluetoothServer(private val name: String, private val serviceUuid: UUID) {
    private val _bluetoothUpdates = MutableSharedFlow<BluetoothUpdate>(
        replay = 0,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    actual val bluetoothUpdates: Flow<BluetoothUpdate>
        get() = _bluetoothUpdates

    private var scn: StreamConnectionNotifier? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    actual fun startServer() {
        CoroutineScope(Dispatchers.IO).launch {
            val connURL = "btspp://localhost:$serviceUuid;name=$name"
            scn = Connector.open(connURL) as StreamConnectionNotifier
            val sc = scn?.acceptAndOpen()
            val remoteDevice = RemoteDevice.getRemoteDevice(sc)

            _bluetoothUpdates.emit(BluetoothUpdate.DeviceConnected)

            inputStream = sc?.openDataInputStream()
            outputStream = sc?.openDataOutputStream()
            while (true) {
                delay(500)
                try {
                    inputStream?.let {
                        val inputArray = ByteArray(1024)
                        val read = it.read(inputArray)
                        if (read > 0) {
                            _bluetoothUpdates.emit(BluetoothUpdate.Message(inputArray))
                        }
                    } ?: run {
                        disconnect()
                    }
                } catch (e: IOException) {
                    disconnect()
                }
            }
        }
    }

    actual fun send(message: ByteArray) {
        outputStream?.write(message)
    }

    private fun disconnect() {
        scn?.close()
        inputStream?.close()
        outputStream?.close()
        inputStream = null
        outputStream = null
        _bluetoothUpdates.tryEmit(BluetoothUpdate.DeviceDisconnected)
    }
}
