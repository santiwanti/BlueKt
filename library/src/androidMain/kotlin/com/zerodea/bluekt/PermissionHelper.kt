package com.zerodea.bluekt

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class PermissionHelper private constructor(
    private val activity: ComponentActivity,
    private val permissionType: PermissionType,
    private val onAccepted: () -> Unit,
    private val onRejected: (List<String>) -> Unit
) {
    private var startTime: Long = 0L

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.all { it.value }) {
                // Permissions are granted. Continue the action or workflow in your app.
                onAccepted.invoke()
            } else {
                // Starting in Android 11 the permissions can be automatically denied by the system
                //  This usually takes around 100ms while the process for a user to manually deny
                //  permissions takes at least 1s therefore checking if the permission was denied in
                //  less than 250ms helps us identify if the user didn't even see the permission
                //  request.
                val permissionExecutionTime = System.currentTimeMillis() - startTime
                if (permissionExecutionTime < PERMISSION_SYSTEM_EXECUTION_TIMEOUT) {
                    showSettingsDialog(activity, permissionType, this.onRejected)
                } else {
                    this.onRejected.invoke(grants.filter { !it.value }.map { it.key })
                }
            }
        }

    fun launch() {
        with(activity) {
            if (permissionType.getPermissionsFor(Build.VERSION.SDK_INT)
                    .any { shouldShowRequestPermissionRationale(it) }
            ) {
                showReasonDialog(this, permissionType, onReject = onRejected, onAccept = onAccepted)
                return
            }
        }

        // Ask for the first time or after disabled manually from settings
        // No rationale, no do not ask again
        requestPermissions()
    }

    private fun requestPermissions() {
        startTime = System.currentTimeMillis()
        permissionLauncher.launch(
            permissionType.getPermissionsFor(Build.VERSION.SDK_INT).toTypedArray()
        )
    }

    companion object {
        private const val PERMISSION_SYSTEM_EXECUTION_TIMEOUT = 250L

        enum class PermissionType {
            // We check for the API version in the permissionState function
            @SuppressLint("InlinedApi")
            BLUETOOTH {
                override fun getPermissionsFor(version: Int): List<String> {
                    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        listOf(
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                    } else {
                        listOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ).apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                plus(Manifest.permission.FOREGROUND_SERVICE)
                            }
                        }
                    }
                }
            };

            abstract fun getPermissionsFor(version: Int): List<String>
        }

        fun areAllPermissionsGranted(context: Context, permissionType: PermissionType): Boolean =
            permissionType.getPermissionsFor(Build.VERSION.SDK_INT)
                .all {
                    ContextCompat.checkSelfPermission(
                        context,
                        it
                    ) == PackageManager.PERMISSION_GRANTED
                }

        fun getMissingPermissions(context: Context, permissionType: PermissionType): List<String> {
            return permissionType.getPermissionsFor(Build.VERSION.SDK_INT)
                .filter {
                    ContextCompat.checkSelfPermission(
                        context,
                        it
                    ) == PackageManager.PERMISSION_DENIED
                }
        }

        fun registerPermission(
            activity: ComponentActivity,
            permissionType: PermissionType,
            onAccepted: () -> Unit,
            onRejected: (List<String>) -> Unit,
        ): PermissionHelper {
            return PermissionHelper(
                activity,
                permissionType,
                onAccepted,
                onRejected
            )
        }
    }

    /**
     * Permission reason dialog.
     * This will be shown when permission needs to be asked WITHOUT user interaction.
     * User knows why the permission is needed and then it will be asked.
     */
    private fun showReasonDialog(
        context: Context,
        permissionType: PermissionType,
        onAccept: () -> Unit,
        onReject: (List<String>) -> Unit
    ): AlertDialog.Builder {
        return AlertDialog.Builder(context)
            .setTitle(getTitleResId(permissionType))
            .setMessage(getMessageResId(permissionType))
            .setPositiveButton(R.string.main_yes) { _, _ ->
                onAccept()
            }
            .setNegativeButton(R.string.main_no) { _, _ ->
                onReject(getMissingPermissions(context, permissionType))
            }.setCancelable(false)
    }

    /**
     * Permission settings dialog.
     * This will be shown when permission need to be asked WITH user interaction after permanent denial.
     * User already knows why the permission is needed and then we ask to open settings
     */
    private fun showSettingsDialog(
        context: Context,
        permissionType: PermissionType,
        onReject: (List<String>) -> Unit
    ): AlertDialog.Builder {
        return AlertDialog.Builder(context)
            .setTitle(getTitleResId(permissionType))
            .setMessage(R.string.settings_permission_message)
            .setPositiveButton(R.string.button_open_settings) { _, _ ->
                openAppSettings(context)
            }
            .setOnDismissListener {
                onReject(getMissingPermissions(context, permissionType))
            }
    }

    private fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @StringRes
    private fun getTitleResId(permissionType: PermissionType): Int {
        return when (permissionType) {
            PermissionType.BLUETOOTH -> R.string.bluetooth_permission_title
        }
    }

    @StringRes
    private fun getMessageResId(permissionType: PermissionType): Int {
        return when (permissionType) {
            PermissionType.BLUETOOTH -> R.string.bluetooth_permission_message
        }
    }

    @StringRes
    private fun getRationaleResId(permissionType: PermissionType): Int {
        return when (permissionType) {
            PermissionType.BLUETOOTH -> R.string.bluetooth_permission_rationale
        }
    }
}
