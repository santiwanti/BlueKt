package com.zerodea.bluekt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors

internal class SerialSocket : Runnable {

    private val disconnectBroadcastReceiver: BroadcastReceiver

    private var context: Context? = null
    private var device: BluetoothDevice? = null
    private var socket: BluetoothSocket? = null
    private var connected: Boolean = false

    private val _socketUpdates = MutableSharedFlow<BluetoothUpdate>(
        replay = 0,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val socketUpdates: Flow<BluetoothUpdate>
        get() = _socketUpdates

    init {
        disconnectBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                disconnect() // disconnect now, else would be queued until UI re-attached
            }
        }
    }

    /**
     * connect-success and most connect-errors are returned asynchronously to listener
     */
    @Throws(IOException::class)
    fun connect(context: Context, device: BluetoothDevice) {
        if (connected || socket != null)
            throw IOException("already connected")
        this.context = context
        this.device = device
        context.registerReceiver(
            disconnectBroadcastReceiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        )
        // TODO use coroutines?
        Executors.newSingleThreadExecutor().submit(this)
    }

    @Throws(IOException::class)
    fun write(data: ByteArray) {
        if (!connected)
            throw IOException("not connected")
        socket?.outputStream?.write(data)
    }

    @SuppressLint("MissingPermission")
    override fun run() { // connect & read
        try {
            socket = device?.createRfcommSocketToServiceRecord(BLUETOOTH_SPP)
            socket?.connect()
        } catch (e: IOException) {
            disconnect()
            return
        }

        connected = true
        try {
            val buffer = ByteArray(1024)
            var len: Int

            while (true) {
                len = socket?.inputStream?.read(buffer) ?: continue
                val data = buffer.copyOf(len)
                _socketUpdates.tryEmit(BluetoothUpdate.Message(data))
            }
        } catch (e: IOException) {
            disconnect()
        }
    }

    private fun disconnect() {
        connected = false
        _socketUpdates.tryEmit(BluetoothUpdate.DeviceDisconnected)
        try {
            socket?.close()
        } catch (ignored: IOException) {
        }
        try {
            context?.unregisterReceiver(disconnectBroadcastReceiver)
        } catch (ignored: Exception) {
        }
        socket = null
    }

    companion object {
        private val BLUETOOTH_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}