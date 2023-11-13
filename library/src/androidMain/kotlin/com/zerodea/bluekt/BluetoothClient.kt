package com.zerodea.bluekt

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge

actual class BluetoothClient(
    private val activity: ComponentActivity,
) {
    private val bluetoothManager = activity.getSystemService(BluetoothManager::class.java)
    private val btAdapter = bluetoothManager.adapter

    private val serialSocket = SerialSocket()

    private val _bluetoothUpdates = MutableSharedFlow<BluetoothUpdate>(
        replay = 0,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    actual val bluetoothUpdates: Flow<BluetoothUpdate>
        get() = merge(_bluetoothUpdates, serialSocket.socketUpdates)

    private val selectBluetoothDevice =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data
                    ?.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
                    ?.let { device ->
                        _bluetoothUpdates.tryEmit(BluetoothUpdate.DeviceSelected)
                        connectToDevice(device)
                    } ?: _bluetoothUpdates.tryEmit(BluetoothUpdate.NoDeviceSelected)
            } else _bluetoothUpdates.tryEmit(BluetoothUpdate.NoDeviceSelected)
        }

    class EnableBluetoothContract : ActivityResultContract<Unit, Boolean>() {
        override fun createIntent(context: Context, input: Unit) =
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

        override fun parseResult(resultCode: Int, intent: Intent?) =
            resultCode == Activity.RESULT_OK
    }

    private val enableBluetooth =
        activity.registerForActivityResult(EnableBluetoothContract()) { enabled ->
            if (enabled) actualStartDiscovery()
            else _bluetoothUpdates.tryEmit(BluetoothUpdate.BluetoothNotEnabled)
        }

    private val _foundBluetoothDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val foundBluetoothDevices: StateFlow<List<BluetoothDevice>>
        get() = _foundBluetoothDevices

    private var btDiscoveryRetryAttempts = 0

    private val bluetoothConnectionReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?.let { device ->
                            _foundBluetoothDevices.tryEmit(
                                foundBluetoothDevices.value.plus(device).distinctBy { it.name })
                        }
                }

                else -> {
                    Log.d("Bluetooth", "Other action: ${intent?.action}")
                }
            }
        }
    }

    private val permissionHelper = PermissionHelper.registerPermission(
        activity,
        PermissionHelper.Companion.PermissionType.BLUETOOTH,
        onAccepted = { enableBluetooth.launch(Unit) },
        onRejected = { _bluetoothUpdates.tryEmit(BluetoothUpdate.PermissionsRejected(it)) },
    )

    actual fun startDiscovery() {
        if (PermissionHelper.areAllPermissionsGranted(
                activity,
                PermissionHelper.Companion.PermissionType.BLUETOOTH
            )
        ) {
            enableBluetooth.launch(Unit)
        } else permissionHelper.launch()
    }

    fun onDeviceSelected(device: BluetoothDevice) {
        connectToDevice(device)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        serialSocket.connect(activity, device)
    }

    actual fun send(message: ByteArray) {
        serialSocket.write(message)
    }

    actual fun onReceive() {

    }

    /**
     * This function actually starts the discovery, the public [startDiscovery] function has to
     * handle requesting the permissions if they are missing and enabling bluetooth if it is disabled
     * before we can actually start the discovery
     */
    @SuppressLint("MissingPermission")
    private fun actualStartDiscovery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pairingRequest = AssociationRequest.Builder().build()
            val deviceManager: CompanionDeviceManager =
                activity.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                deviceManager.associate(
                    pairingRequest,
                    { it.run() },
                    object : CompanionDeviceManager.Callback() {
                        override fun onAssociationPending(intentSender: IntentSender) {
                            super.onAssociationPending(intentSender)
                            IntentSenderRequest.Builder(intentSender).build()
                        }

                        override fun onAssociationCreated(associationInfo: AssociationInfo) {
                            super.onAssociationCreated(associationInfo)
                        }

                        override fun onFailure(error: CharSequence?) {
                            _bluetoothUpdates.tryEmit(BluetoothUpdate.NoDeviceSelected)
                        }
                    }
                )
            } else {
                deviceManager.associate(
                    pairingRequest,
                    object : CompanionDeviceManager.Callback() {
                        override fun onDeviceFound(intentSender: IntentSender) {
                            super.onDeviceFound(intentSender)
                            selectBluetoothDevice.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        }

                        override fun onFailure(error: CharSequence?) {
                            _bluetoothUpdates.tryEmit(BluetoothUpdate.NoDeviceSelected)
                        }
                    },
                    null,
                )
            }
        } else {
            btDiscoveryRetryAttempts = 0
            with(activity) {
                registerReceiver(
                    bluetoothConnectionReceiver,
                    IntentFilter(BluetoothDevice.ACTION_FOUND)
                )
                registerReceiver(
                    bluetoothConnectionReceiver,
                    IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                )
            }

            if (!btAdapter.startDiscovery()) _bluetoothUpdates.tryEmit(BluetoothUpdate.BluetoothNotEnabled)
        }
    }
}