package com.example.ble_accy_perif

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import com.example.ble_accy_perif.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val BLUETOOTH_ALL_PERMISSIONS_REQUEST_CODE = 2


class MainActivity : AppCompatActivity() {
    private lateinit var activityBinding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        activityBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityBinding.root)

        Log.d(TAG, "onCreate()")
        activityBinding.switchAdvertising.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                prepareAndStartAdvertising()
            } else {
                PerifBleService.sendIntentToServiceClass<Any>(
                    this,
                    PerifBleService.ACTION_BLE_ADVERTISING_STOP
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun onTapSend(view: View) {
        val text = activityBinding.editTextCharForIndicate.text.toString()
        val data = text.toByteArray(Charsets.UTF_8)
        PerifBleService.sendIntentToServiceClass<ByteArray>(
            this,
            PerifBleService.ACTION_NOTIFY_TO_SUBSCRIBED_DEVICES,
            PerifBleService.EXTRA_NOTIFY_TO_SUBSCRIBED_DEVICES_DATA,
            data
        )
    }

    private fun prepareAndStartAdvertising() {
        ensureBluetoothCanBeUsed { isSuccess, _ ->
            runOnUiThread {
                if (isSuccess) {
                    PerifBleService.sendIntentToServiceClass<Any>(
                        this,
                        PerifBleService.ACTION_BLE_ADVERTISING_START
                    )
                }
            }
        }
    }

    enum class AskType {
        AskOnce,
        InsistUntilSuccess
    }

    private var activityResultHandlers = mutableMapOf<Int, (Int) -> Unit>()
    private var permissionResultHandlers =
        mutableMapOf<Int, (Array<out String>, IntArray) -> Unit>()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionResultHandlers[requestCode]?.let { handler ->
            handler(permissions, grantResults)
        } ?: run {
            Log.e(TAG, "Error: onRequestPermissionsResult requestCode=$requestCode not handled")
        }
    }

    private fun ensureBluetoothCanBeUsed(completion: (Boolean, String) -> Unit) {
        grantBluetoothPeripheralPermissions(AskType.AskOnce) { isGranted ->
            if (!isGranted) {
                completion(false, "Bluetooth permissions denied")
                return@grantBluetoothPeripheralPermissions
            }

            enableBluetooth(AskType.AskOnce) { isEnabled ->
                if (!isEnabled) {
                    completion(false, "Bluetooth OFF")
                    return@enableBluetooth
                }

                completion(true, "BLE ready for use")
            }
        }
    }

    private fun enableBluetooth(askType: AskType, completion: (Boolean) -> Unit) {
        if (PerifBluetoothUtility.isBluetoothOn(this)) {
            completion(true)
        } else {
            val intentString = BluetoothAdapter.ACTION_REQUEST_ENABLE
            val requestCode = ENABLE_BLUETOOTH_REQUEST_CODE

            // set activity result handler
            activityResultHandlers[requestCode] = { result ->
                Unit
                val isSuccess = result == Activity.RESULT_OK
                if (isSuccess || askType != AskType.InsistUntilSuccess) {
                    activityResultHandlers.remove(requestCode)
                    completion(isSuccess)
                } else {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.e(TAG, "Permission denied BLUETOOTH_CONNECT")
                    } else {
                        startActivityForResult(Intent(intentString), requestCode)
                    }
                }
            }

            // start activity for the request
            startActivityForResult(Intent(intentString), requestCode)
        }
    }

    private fun grantBluetoothPeripheralPermissions(
        askType: AskType,
        completion: (Boolean) -> Unit
    ) {
        val wantedPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            emptyArray()
        }

        if (wantedPermissions.isEmpty() || hasPermissions(wantedPermissions)) {
            completion(true)
        } else {
            runOnUiThread {
                val requestCode = BLUETOOTH_ALL_PERMISSIONS_REQUEST_CODE

                // set permission result handler
                permissionResultHandlers[requestCode] = { _ /*permissions*/, grantResults ->
                    val isSuccess = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                    if (isSuccess || askType != AskType.InsistUntilSuccess) {
                        permissionResultHandlers.remove(requestCode)
                        completion(isSuccess)
                    } else {
                        // request again
                        requestPermissionArray(wantedPermissions, requestCode)
                    }
                }

                requestPermissionArray(wantedPermissions, requestCode)
            }
        }
    }


    private fun Context.hasPermissions(permissions: Array<String>): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun Activity.requestPermissionArray(permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }


    companion object {
        const val TAG = "MainActivity_Jdt"
    }
}