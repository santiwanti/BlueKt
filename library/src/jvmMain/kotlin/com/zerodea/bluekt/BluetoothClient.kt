package com.zerodea.bluekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.bluetooth.*
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnection
import javax.microedition.io.StreamConnectionNotifier


actual class BluetoothClient {
    private val _bluetoothUpdates = MutableSharedFlow<BluetoothUpdate>(
        replay = 0,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    actual val bluetoothUpdates: Flow<BluetoothUpdate>
        get() = _bluetoothUpdates

    private val foundBluetoothDevices = mutableListOf<RemoteDevice>()

    private var scn: StreamConnectionNotifier? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val discoveryAgent = LocalDevice.getLocalDevice().discoveryAgent

    private var foundServiceRecords = arrayOf<ServiceRecord>()
    private var serviceSearchMutex = Mutex(locked = true)

    private val listener: DiscoveryListener = object : DiscoveryListener {
        override fun deviceDiscovered(btDevice: RemoteDevice, cod: DeviceClass) {
            println("Device " + btDevice.bluetoothAddress + " found")
            val name: String = try {
                btDevice.getFriendlyName(true)
            } catch (cantGetDeviceName: IOException) {
                println("couldn't retrieve device name")
                // TODO i18n remove hardcoded value
                "unknown"
            }
            _bluetoothUpdates.tryEmit(
                BluetoothUpdate.DeviceDiscovered(
                    BluetoothUpdate.DeviceDiscovered.BluetoothDevice(
                        name,
                        btDevice.bluetoothAddress
                    )
                )
            )
        }

        override fun inquiryCompleted(discType: Int) {
            println("Device Inquiry completed!")
        }

        override fun serviceSearchCompleted(transID: Int, respCode: Int) {

        }

        override fun servicesDiscovered(transID: Int, servRecord: Array<ServiceRecord>) {
            foundServiceRecords = servRecord
            serviceSearchMutex.unlock()
        }
    }

    actual fun startDiscovery() {
        val started = discoveryAgent.startInquiry(DiscoveryAgent.GIAC, listener)
        if (!started) _bluetoothUpdates.tryEmit(BluetoothUpdate.NoDeviceSelected)
    }

    actual fun onDeviceSelected(device: BluetoothUpdate.DeviceDiscovered.BluetoothDevice) {
        val btDevice = foundBluetoothDevices.first { it.bluetoothAddress == device.macAddress }
        discoveryAgent.searchServices(
            intArrayOf(),
            device.uuids.map { UUID(it.toLong()) }.toTypedArray(),
            btDevice,
            listener,
        )
        CoroutineScope(Dispatchers.Default).launch {
            serviceSearchMutex.lock()
            if (foundServiceRecords.isEmpty()) {
                _bluetoothUpdates.emit(BluetoothUpdate.DeviceNotFound)
            }
            val security = 0 + if (btDevice.isAuthenticated) 1 else 0 + if (btDevice.isEncrypted) 1 else 0
            val connUrl = foundServiceRecords.first().getConnectionURL(security, false)
            val streamConnection = Connector.open(connUrl) as StreamConnection
            inputStream = streamConnection.openDataInputStream()
            outputStream = streamConnection.openDataOutputStream()

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

    actual fun onReceive() {
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