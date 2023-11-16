package com.zerodea.bluekt

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach

actual class BluetoothClient(
    private val activity: ComponentActivity,
) : BluetoothComponent(activity, ComponentType.Client) {
    private val selectBluetoothDevice =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data
                    ?.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
                    ?.let { device ->
                        internalBluetoothUpdates.tryEmit(BluetoothUpdate.DeviceSelected)
                        connectToDevice(device)
                    } ?: internalBluetoothUpdates.tryEmit(BluetoothUpdate.NoDeviceSelected)
            } else internalBluetoothUpdates.tryEmit(BluetoothUpdate.NoDeviceSelected)
        }

    private val foundBluetoothDevices = mutableListOf<BluetoothDevice>()
    private var btDiscoveryRetryAttempts = 0

    private val bluetoothConnectionReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?.let { device ->
                            foundBluetoothDevices.add(device)
                            internalBluetoothUpdates.tryEmit(
                                BluetoothUpdate.DeviceDiscovered(
                                    BluetoothUpdate.DeviceDiscovered.BluetoothDevice(
                                        device.name,
                                        device.address,
                                    )
                                )
                            )
                        }
                }

                else -> {
                    Log.d("Bluetooth", "Other action: ${intent?.action}")
                }
            }
        }
    }

    actual fun startDiscovery() {
        super.startComponent()
    }

    @SuppressLint("MissingPermission")
    actual fun onDeviceSelected(device: BluetoothUpdate.DeviceDiscovered.BluetoothDevice) {
        connectToDevice(foundBluetoothDevices.first { it.name == device.name && it.address == it.address})
    }

    private fun connectToDevice(device: BluetoothDevice) {
        serialSocket.connect(activity, device)
    }

    actual fun send(message: ByteArray) {
        serialSocket.write(message)
    }

    /**
     * This function actually starts the discovery, the public [startDiscovery] function has to
     * handle requesting the permissions if they are missing and enabling bluetooth if it is disabled
     * before we can actually start the discovery
     */
    @SuppressLint("MissingPermission")
    override fun onBluetoothEnabled() {
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
                            internalBluetoothUpdates.tryEmit(BluetoothUpdate.NoDeviceSelected)
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
                            internalBluetoothUpdates.tryEmit(BluetoothUpdate.NoDeviceSelected)
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

            if (btAdapter?.startDiscovery() != true) internalBluetoothUpdates.tryEmit(
                BluetoothUpdate.BluetoothNotEnabled
            )
        }
    }
}