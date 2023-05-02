package com.example.ble_accy_perif

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.lang.Exception

class PerifBleService : Service() {

    private var mGattManager: PerifGattManager? = null
    private lateinit var mBleAdvertiseHelper: BleAdvertiseHelper

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind()")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        mBleAdvertiseHelper = BleAdvertiseHelper(this)
        mGattManager = PerifGattManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand()")
        if (intent == null) {
            Log.e(TAG, "Intent is null")
            return START_NOT_STICKY
        }

        val action = intent.action
        Log.d(TAG, "Intent action: $action")
        when (action) {
            ACTION_BLE_ADVERTISING_START -> {
                mGattManager?.bleStartGattServer()
                mBleAdvertiseHelper.startAdvertising()
            }

            ACTION_BLE_ADVERTISING_STOP -> {
                mGattManager?.bleStopGattServer()
                mBleAdvertiseHelper.stopAdvertising()
            }

            ACTION_NOTIFY_TO_SUBSCRIBED_DEVICES -> {
                val testData = "Dummy notify data".toByteArray()
                val data =
                    intent.getByteArrayExtra(EXTRA_NOTIFY_TO_SUBSCRIBED_DEVICES_DATA) ?: testData
                Log.d(TAG,"Data sending: ${data.toString(Charsets.UTF_8)}")
                mGattManager?.onNotifyToSubscribedDevices(data)
            }
        }

        return START_STICKY
    }

    companion object {
        private const val TAG = "PerifBleService_Jdt"

        const val BLE_SERVICE_PACKAGE = "com.example.ble_accy_perif"
        const val BLE_SERVICE_CLASS = "com.example.ble_accy_perif.PerifBleService"

        internal const val ACTION_BLE_ADVERTISING_START =
            "com.example.ble_accy_perif.action.BLE_ADVERTISING_START"
        internal const val ACTION_BLE_ADVERTISING_STOP =
            "com.example.ble_accy_perif.action.BLE_ADVERTISING_STOP"


        internal const val ACTION_NOTIFY_TO_SUBSCRIBED_DEVICES =
            "com.example.ble_accy_perif.action.NOTIFY_TO_SUBSCRIBED_DEVICES"
        internal const val EXTRA_NOTIFY_TO_SUBSCRIBED_DEVICES_DATA =
            "com.example.ble_accy_perif.extra.NOTIFY_TO_SUBSCRIBED_DEVICES"


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