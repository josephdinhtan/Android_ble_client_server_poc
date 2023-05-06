package com.example.ble_phone_central.service

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.ble_phone_central.Helper.BleScanHelper
import com.example.ble_phone_central.Helper.GattManager
import com.example.ble_phone_central.model.BleLifecycleState
import kotlinx.coroutines.flow.MutableSharedFlow
import java.lang.Exception

class BleCentralService : Service() {

    private var isScanning = false
    private var mGattManager: GattManager? = null
    private lateinit var mBleScanHelper: BleScanHelper
    private lateinit var mBluetoothDevice: BluetoothDevice

    private var mBleLifecycleState = BleLifecycleState.Disconnected

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind()")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        mBleScanHelper = BleScanHelper(this, ::onBleLifecycleStateChange)

        bleStartLifecycle()
    }

    private fun onBleLifecycleStateChange(newState: BleLifecycleState) {
        mBleLifecycleState = newState
        bleLifecycleStateShareFlow.tryEmit(newState)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
        bleEndLifecycle()
    }

    private fun bleStartLifecycle() {
        isScanning = false
        mGattManager = null
        mBleLifecycleState = BleLifecycleState.Disconnected
    }

    private fun bleEndLifecycle() {
        isScanning = false
        mGattManager?.close()
        mGattManager = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand()")
        if (intent == null) {
            Log.e(TAG, "Intent is null")
            return START_NOT_STICKY
        }

        val action = intent.action
        Log.d(TAG, "[STEP] - action: $action")
        Log.d(TAG, "BLE state: $mBleLifecycleState")
        when (action) {
            ACTION_BLE_SCAN_START -> {
                mBleScanHelper.startScan()
            }

            ACTION_BLE_STOP -> {
                mBleScanHelper.stopScan()
                mGattManager?.close()
            }

            ACTION_BLE_DEVICE_FOUND -> {
                mBluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                mGattManager = GattManager(this, mBluetoothDevice, ::onBleLifecycleStateChange)
                mGattManager!!.connect()
            }

            ACTION_GATT_CONNECTED -> {
                mGattManager?.discoverServices()
            }

            ACTION_GATT_SERVICE_DISCOVERED -> {
                // TODO: consider check whether service list match or not
                sendIntentToServiceClass<Any>(this, ACTION_GATT_CHAR_REQUEST_SUBSCRIBE)
            }

            ACTION_GATT_DISCONNECTED -> {
                Log.d(TAG, "Gatt disconnected")
                // TODO: need react after Gatt disconnected, consider retry and reconnect
            }

            ACTION_GATT_CHAR_REQUEST_SUBSCRIBE -> {
                mGattManager?.subscribeToCharacteristicIndication()
            }

            ACTION_GATT_CHAR_INDICATION_REQUEST_UNSUBSCRIBE -> {
                mGattManager?.unsubscribeFromCharacteristic()
            }

            ACTION_GATT_CHAR_INDICATION_SUBSCRIBED -> {
                // subscription processed, consider connection is ready for use
                Log.d(TAG, "ACK confirm subscribed service")
                Log.d(TAG, "----- READY TO USE -----")

                // TODO: consider make specific UUIDs for Wifi-direct-info
                // Read for Wifi name
                sendIntentToServiceClass<Any>(this, ACTION_CHAR_REQUEST_READ)
            }

            ACTION_CHAR_REQUEST_READ -> {
                mGattManager?.onRequestCharacteristicRead()
            }

            ACTION_CHAR_READ_DONE -> {
                val data = intent.getByteArrayExtra(EXTRA_CHAR_READ_DATA)
                data?.let {
                    val strValue = data.toString(Charsets.UTF_8)
                    Log.d(TAG, "Data got after read: $strValue")
                    wifiDirectServerNameShareFlow.tryEmit(strValue)
                } ?: run {
                    Log.d(TAG, "Invalid data")
                }
            }

            ACTION_CHAR_REQUEST_WRITE -> {
                val data = intent.getByteArrayExtra(EXTRA_CHAR_REQUEST_WRITE_DATA)
                data?.let {
                    mGattManager?.onRequestCharacteristicWrite(data)
                } ?: run {
                    Log.e(TAG, "Invalid data")
                }
            }

            ACTION_CHAR_WRITE_DONE -> {
                val status = intent.getIntExtra(
                    EXTRA_CHAR_WRITE_ACK, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                )
                val log: String = "onCharacteristicWrite " + when (status) {
                    BluetoothGatt.GATT_SUCCESS -> "OK"
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "not allowed"
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "invalid length"
                    else -> "error $status"
                }
                Log.d(TAG, "Write done: $log")
            }

            ACTION_RECEIVE_INDICATE -> {
                val data = intent.getByteArrayExtra(EXTRA_RECEIVE_INDICATE_DATA)
                Log.d(TAG, "Indicate Data: ${data?.toString(Charsets.UTF_8)}")
                data?.let {
                    bleIndicationDataShareFlow.tryEmit(data)
                } ?: run {
                    Log.e(TAG, "Data is NULL")
                }
            }
        }

        return START_STICKY
    }

    companion object {
        private const val TAG = "BleCentralService_Jdt"

        const val BLE_SERVICE_PACKAGE = "com.example.ble_phone_central"
        const val BLE_SERVICE_CLASS = "com.example.ble_phone_central.service.BleCentralService"

        internal const val ACTION_BLE_SCAN_START = "com.example.ble_phone_central.action.SCAN_START"
        internal const val ACTION_BLE_STOP = "com.example.ble_phone_central.action.BLE_STOP"
        internal const val ACTION_BLE_DEVICE_FOUND =
            "com.example.ble_phone_central.action.BLE_DEVICE_FOUND"
        internal const val ACTION_GATT_CONNECTED =
            "com.example.ble_phone_central.action.GATT_CONNECTED"
        internal const val ACTION_GATT_DISCONNECTED =
            "com.example.ble_phone_central.action.GATT_DISCONNECTED"
        internal const val ACTION_GATT_SERVICE_DISCOVERED =
            "com.example.ble_phone_central.action.GATT_SERVICE_DISCOVERED"
        internal const val ACTION_GATT_CHAR_REQUEST_SUBSCRIBE =
            "com.example.ble_phone_central.action.GATT_CHAR_INDICATION_REQUEST_SUBSCRIBE"
        internal const val ACTION_GATT_CHAR_INDICATION_SUBSCRIBED =
            "com.example.ble_phone_central.action.GATT_CHAR_INDICATION_SERVICE_SUBSCRIBED"
        internal const val ACTION_GATT_CHAR_INDICATION_REQUEST_UNSUBSCRIBE =
            "com.example.ble_phone_central.action.GATT_CHAR_INDICATION_REQUEST_UNSUBSCRIBE"


        internal const val ACTION_CHAR_REQUEST_READ =
            "com.example.ble_phone_central.action.CHAR_REQUEST_READ"
        internal const val ACTION_CHAR_READ_DONE =
            "com.example.ble_phone_central.action.CHAR_READ_DONE"
        internal const val EXTRA_CHAR_READ_DATA =
            "com.example.ble_phone_central.extra.CHAR_READ_DATA"

        internal const val ACTION_CHAR_REQUEST_WRITE =
            "com.example.ble_phone_central.action.CHAR_REQUEST_WRITE"
        internal const val EXTRA_CHAR_REQUEST_WRITE_DATA =
            "com.example.ble_phone_central.extra.CHAR_REQUEST_WRITE_DATA"
        internal const val ACTION_CHAR_WRITE_DONE =
            "com.example.ble_phone_central.action.CHAR_WRITE_DONE"
        internal const val EXTRA_CHAR_WRITE_ACK =
            "com.example.ble_phone_central.extra.CHAR_WRITE_ACK"

        internal const val ACTION_RECEIVE_INDICATE =
            "com.example.ble_phone_central.action.RECEIVE_INDICATE"
        internal const val EXTRA_RECEIVE_INDICATE_DATA =
            "com.example.ble_phone_central.extra.RECEIVE_INDICATE_DATA"

        internal val bleLifecycleStateShareFlow = MutableSharedFlow<BleLifecycleState>(replay = 1)
        internal val bleIndicationDataShareFlow = MutableSharedFlow<ByteArray>(replay = 1)
        internal val wifiDirectServerNameShareFlow = MutableSharedFlow<String>(replay = 1)

        internal fun <T : Any> sendIntentToServiceClass(
            context: Context, action: String, extraName: String? = null, extraValue: T? = null
        ) {
            val intent = Intent(action).apply {
                component = ComponentName(
                    BLE_SERVICE_PACKAGE, BLE_SERVICE_CLASS
                )
            }
            extraName?.let {
                when (extraValue) {
                    is BluetoothDevice -> intent.putExtra(extraName, extraValue)
                    is ByteArray -> intent.putExtra(extraName, extraValue)
                    else -> throw Exception("Unexcepted dataType")
                }
            }

            context.startService(intent)
        }
    }
}