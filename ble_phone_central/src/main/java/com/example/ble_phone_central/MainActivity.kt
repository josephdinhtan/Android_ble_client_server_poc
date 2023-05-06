package com.example.ble_phone_central

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import com.example.ble_phone_central.databinding.ActivityMainBinding
import com.example.ble_phone_central.model.BleLifecycleState
import com.example.ble_phone_central.service.BleCentralService
import com.example.ble_phone_central.Helper.BluetoothUtility
import com.example.ble_phone_central.manager.BleManager
import com.example.ble_phone_central.manager.BleManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private const val LOCATION_PERMISSION_REQUEST_CODE = 2
private const val BLUETOOTH_ALL_PERMISSIONS_REQUEST_CODE = 3

class MainActivity : AppCompatActivity() {

    private lateinit var activityBinding: ActivityMainBinding

    private val mBleManager: BleManager by lazy {
        BleManagerImpl(this)
    }

    private var lifecycleState = BleLifecycleState.Disconnected
        set(value) {
            field = value
            runOnUiThread {
                activityBinding.textViewLifecycleState.text = "State: ${value.name}"
                if (value != BleLifecycleState.Connected) {
                    activityBinding.textViewSubscription.text =
                        getString(R.string.text_not_subscribed)
                }
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        activityBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityBinding.root)

        activityBinding.switchConnect.setOnCheckedChangeListener { _, isChecked ->
            when (isChecked) {
                true -> {
                    prepareAndStartBleScan()
                }

                else -> {}
            }
        }
        GlobalScope.launch(Dispatchers.Main) {
            mBleManager.bleLifecycleState.collect() { state ->
                activityBinding.textViewLifecycleState.text = state.toString()
            }
        }
        GlobalScope.launch(Dispatchers.Main) {
            mBleManager.wifiDirectServerName
                .collect {
                    activityBinding.textViewReadValue.text = it
                }
        }
        GlobalScope.launch(Dispatchers.Main) {
            mBleManager.bleIndicationData
                .collect {
                    activityBinding.textViewIndicateValue.text = it.toString(Charsets.UTF_8)
                }
        }
    }

    fun onTapRead(view: View) {
        mBleManager.requestReadWifiName()
    }

    fun onTapWrite(view: View) {
        mBleManager.sendData("Test Command".toByteArray())
    }

    private fun prepareAndStartBleScan() {
        ensureBluetoothCanBeUsed { isSuccess, message ->
            if (isSuccess) {
                Log.d(TAG, "Start Service and start SCAN")
                mBleManager.start()
            }
        }
    }

    //region Permissions and Settings management
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
            Log.e(TAG, "ERROR: onRequestPermissionsResult requestCode=$requestCode not handled")
        }
    }

    private fun ensureBluetoothCanBeUsed(completion: (Boolean, String) -> Unit) {
        grantBluetoothCentralPermissions(AskType.AskOnce) { isGranted ->
            if (!isGranted) {
                completion(false, "Bluetooth permissions denied")
                return@grantBluetoothCentralPermissions
            }

            enableBluetooth(AskType.AskOnce) { isEnabled ->
                if (!isEnabled) {
                    Log.d(TAG, "Bluetooth is OFF")
                    completion(false, "Bluetooth OFF")
                    return@enableBluetooth
                }

                grantLocationPermissionIfRequired(AskType.AskOnce) { isGranted ->
                    if (!isGranted) {
                        completion(false, "Location permission denied")
                        return@grantLocationPermissionIfRequired
                    }

                    completion(true, "Bluetooth ON, permissions OK, ready")
                }
            }
        }
    }

    private var bluetoothOnCallback: ((Boolean) -> Unit)? = null
    private fun enableBluetooth(askType: AskType, completion: (Boolean) -> Unit) {
        bluetoothOnCallback = completion
        if (BluetoothUtility.isBluetoothOn(this)) {
            completion(true)
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }
    }

    private var requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                bluetoothOnCallback?.let { it(true) }
            } else {
                bluetoothOnCallback?.let { it(false) }
            }
        }

    private fun grantLocationPermissionIfRequired(askType: AskType, completion: (Boolean) -> Unit) {
        val wantedPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // BLUETOOTH_SCAN permission has flag "neverForLocation", so location not needed
            completion(true)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || hasPermissions(wantedPermissions)) {
            completion(true)
        } else {
            runOnUiThread {
                val requestCode = LOCATION_PERMISSION_REQUEST_CODE

                // prepare motivation message
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Location permission required")
                builder.setMessage("BLE advertising requires location access, starting from Android 6.0")
                builder.setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissionArray(wantedPermissions, requestCode)
                }
                builder.setCancelable(false)

                // set permission result handler
                permissionResultHandlers[requestCode] = { permissions, grantResults ->
                    val isSuccess = grantResults.firstOrNull() != PackageManager.PERMISSION_DENIED
                    if (isSuccess || askType != AskType.InsistUntilSuccess) {
                        permissionResultHandlers.remove(requestCode)
                        completion(isSuccess)
                    } else {
                        // show motivation message again
                        builder.create().show()
                    }
                }

                // show motivation message
                builder.create().show()
            }
        }
    }

    private fun grantBluetoothCentralPermissions(askType: AskType, completion: (Boolean) -> Unit) {
        val wantedPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
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
        private const val TAG = "MainActivity_Jdt"
    }
}