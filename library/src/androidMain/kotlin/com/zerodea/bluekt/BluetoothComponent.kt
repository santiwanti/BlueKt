package com.zerodea.bluekt

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

abstract class BluetoothComponent(
    private val activity: ComponentActivity,
    type: ComponentType,
) {

    private val bluetoothManager = activity.getSystemService(BluetoothManager::class.java)
    protected val btAdapter: BluetoothAdapter? = bluetoothManager.adapter

    internal val serialSocket = SerialSocket(type, btAdapter)

    protected val internalBluetoothUpdates = MutableSharedFlow<BluetoothUpdate>(
        replay = 0,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val bluetoothUpdates: Flow<BluetoothUpdate>
        get() = merge(internalBluetoothUpdates, serialSocket.socketUpdates)

    class EnableBluetoothContract : ActivityResultContract<Unit, Boolean>() {
        override fun createIntent(context: Context, input: Unit) =
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

        override fun parseResult(resultCode: Int, intent: Intent?) =
            resultCode == Activity.RESULT_OK
    }

    private val permissionHelper = PermissionHelper.registerPermission(
        activity,
        PermissionHelper.Companion.PermissionType.BLUETOOTH_CLIENT,
        onAccepted = { enableBluetooth.launch(Unit) },
        onRejected = { internalBluetoothUpdates.tryEmit(BluetoothUpdate.PermissionsRejected(it)) },
    )

    private val enableBluetooth =
        activity.registerForActivityResult(EnableBluetoothContract()) { enabled ->
            if (enabled) onBluetoothEnabled()
            else internalBluetoothUpdates.tryEmit(BluetoothUpdate.BluetoothNotEnabled)
        }

    protected abstract fun onBluetoothEnabled()

    fun startComponent() {
        if (PermissionHelper.areAllPermissionsGranted(
                activity,
                PermissionHelper.Companion.PermissionType.BLUETOOTH_CLIENT
            )
        ) {
            enableBluetooth.launch(Unit)
        } else permissionHelper.launch()
    }
}