package com.example.ble_kit.service

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.ble_kit.model.BleLifecycleState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.lang.Exception

internal class BleCentralService : Service() {

    private var isScanning = false
    private var mGattManager: GattManager? = null
    private lateinit var mBleScanHelper: BleScanHelper
    private lateinit var mBluetoothDevice: BluetoothDevice

    private var mBleLifecycleState = BleLifecycleState.Disconnected

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("onBind()")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate()")

        mBleScanHelper = BleScanHelper(this, ::onBleLifecycleStateChange)

        bleStartLifecycle()
    }

    private fun onBleLifecycleStateChange(newState: BleLifecycleState) {
        mBleLifecycleState = newState
        _bleLifecycleStateShareFlow.tryEmit(newState)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("onDestroy()")
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
        Timber.d("onStartCommand()")
        if (intent == null) {
            Timber.e("Intent is null")
            return START_NOT_STICKY
        }

        val action = intent.action
        Timber.d("[STEP] - action: $action")
        Timber.d("BLE state: " + mBleLifecycleState)
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
                Timber.d("Gatt disconnected")
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
                Timber.d("ACK confirm subscribed service")
                Timber.d("----- READY TO USE -----")

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
                    Timber.d("Data got after read: $strValue")
                    _wifiDirectServerNameShareFlow.tryEmit(strValue)
                } ?: run {
                    Timber.d("Invalid data")
                }
            }

            ACTION_CHAR_REQUEST_WRITE -> {
                val data = intent.getByteArrayExtra(EXTRA_CHAR_REQUEST_WRITE_DATA)
                data?.let {
                    mGattManager?.onRequestCharacteristicWrite(data)
                } ?: run {
                    Timber.e("Invalid data")
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
                Timber.d("Write done: " + log)
            }

            ACTION_RECEIVE_INDICATE -> {
                val data = intent.getByteArrayExtra(EXTRA_RECEIVE_INDICATE_DATA)
                Timber.d("Indicate Data: " + data?.toString(Charsets.UTF_8))
                data?.let {
                    _bleIndicationDataShareFlow.tryEmit(data)
                } ?: run {
                    Timber.e("Data is NULL")
                }
            }
        }

        return START_STICKY
    }

    companion object {

        internal const val ACTION_BLE_SCAN_START = "action.SCAN_START"
        internal const val ACTION_BLE_STOP = "action.BLE_STOP"
        internal const val ACTION_BLE_DEVICE_FOUND = "action.BLE_DEVICE_FOUND"
        internal const val ACTION_GATT_CONNECTED = "action.GATT_CONNECTED"
        internal const val ACTION_GATT_DISCONNECTED = "action.GATT_DISCONNECTED"
        internal const val ACTION_GATT_SERVICE_DISCOVERED = "action.GATT_SERVICE_DISCOVERED"
        internal const val ACTION_GATT_CHAR_REQUEST_SUBSCRIBE =
            "action.GATT_CHAR_INDICATION_REQUEST_SUBSCRIBE"
        internal const val ACTION_GATT_CHAR_INDICATION_SUBSCRIBED =
            "action.GATT_CHAR_INDICATION_SERVICE_SUBSCRIBED"
        internal const val ACTION_GATT_CHAR_INDICATION_REQUEST_UNSUBSCRIBE =
            "action.GATT_CHAR_INDICATION_REQUEST_UNSUBSCRIBE"


        internal const val ACTION_CHAR_REQUEST_READ = "action.CHAR_REQUEST_READ"
        internal const val ACTION_CHAR_READ_DONE = "action.CHAR_READ_DONE"
        internal const val EXTRA_CHAR_READ_DATA = "extra.CHAR_READ_DATA"

        internal const val ACTION_CHAR_REQUEST_WRITE = "action.CHAR_REQUEST_WRITE"
        internal const val EXTRA_CHAR_REQUEST_WRITE_DATA = "extra.CHAR_REQUEST_WRITE_DATA"
        internal const val ACTION_CHAR_WRITE_DONE = "action.CHAR_WRITE_DONE"
        internal const val EXTRA_CHAR_WRITE_ACK = "extra.CHAR_WRITE_ACK"

        internal const val ACTION_RECEIVE_INDICATE = "action.RECEIVE_INDICATE"
        internal const val EXTRA_RECEIVE_INDICATE_DATA = "extra.RECEIVE_INDICATE_DATA"

        private val _bleLifecycleStateShareFlow = MutableSharedFlow<BleLifecycleState>(replay = 1)
        private val _bleIndicationDataShareFlow = MutableSharedFlow<ByteArray>(replay = 1)
        private val _wifiDirectServerNameShareFlow = MutableSharedFlow<String>(replay = 1)
        internal val bleLifecycleStateShareFlow = _bleLifecycleStateShareFlow.asSharedFlow()
        internal val bleIndicationDataShareFlow = _bleIndicationDataShareFlow.asSharedFlow()
        internal val wifiDirectServerNameShareFlow = _wifiDirectServerNameShareFlow.asSharedFlow()

        internal fun <T : Any> sendIntentToServiceClass(
            context: Context, action: String, extraName: String? = null, extraValue: T? = null
        ) {
            val intent = Intent(action).apply {
                component = ComponentName(
                    context.packageName, BleCentralService::class.java.name
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