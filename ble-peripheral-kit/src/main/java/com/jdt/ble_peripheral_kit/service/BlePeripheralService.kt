package com.jdt.ble_peripheral_kit.service

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.jdt.ble_peripheral_kit.model.BleLifecycleState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.lang.Exception

internal class BlePeripheralService : Service() {

    private var mGattManager: GattServerManager? = null
    private lateinit var mBleAdvertiseHelper: BleAdvertiseHelper
    private var mBleLifecycleState = BleLifecycleState.Disconnected

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind()")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        mBleAdvertiseHelper = BleAdvertiseHelper(this, ::onBleLifecycleStateChange)
        mGattManager = GattServerManager(this, ::onBleLifecycleStateChange)
    }

    private fun onBleLifecycleStateChange(newState: BleLifecycleState) {
        mBleLifecycleState = newState
        _bleLifecycleStateShareFlow.tryEmit(newState)
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

            ACTION_BLE_STOP -> {
                mGattManager?.bleStopGattServer()
                mBleAdvertiseHelper.stopAdvertising()
            }

            ACTION_NOTIFY_TO_SUBSCRIBED_DEVICES -> {
                val testData = "Dummy notify data".toByteArray()
                val data =
                    intent.getByteArrayExtra(EXTRA_NOTIFY_TO_SUBSCRIBED_DEVICES_DATA) ?: testData
                Timber.d("Data sending: ${data.toString(Charsets.UTF_8)}")
                mGattManager?.onNotifyToSubscribedDevices(data)
            }

            ACTION_WRITE_REQUEST_COME -> {
                val data = intent.getByteArrayExtra(EXTRA_WRITE_REQUEST_COME_DATA)
                Timber.d("Write request: ${data?.toString(Charsets.UTF_8)}")
                data?.let { charWriteRequestDataShareFlow.tryEmit(it) }
            }
        }

        return START_STICKY
    }

    companion object {
        private const val TAG = "PeripheralBleService_Jdt"

        internal const val ACTION_BLE_ADVERTISING_START =
            "action.BLE_ADVERTISING_START"
        internal const val ACTION_BLE_STOP =
            "action.BLE_STOP"

        internal const val ACTION_NOTIFY_TO_SUBSCRIBED_DEVICES =
            "action.NOTIFY_TO_SUBSCRIBED_DEVICES"
        internal const val EXTRA_NOTIFY_TO_SUBSCRIBED_DEVICES_DATA =
            "extra.NOTIFY_TO_SUBSCRIBED_DEVICES"

        internal const val ACTION_WRITE_REQUEST_COME =
            "action.WRITE_REQUEST_COME"
        internal const val EXTRA_WRITE_REQUEST_COME_DATA =
            "extra.WRITE_REQUEST_COME_DATA"

        private val _bleLifecycleStateShareFlow = MutableSharedFlow<BleLifecycleState>(replay = 1)
        private val charWriteRequestDataShareFlow = MutableSharedFlow<ByteArray>(replay = 1)
        internal val bleLifecycleStateShareFlow = _bleLifecycleStateShareFlow.asSharedFlow()
        internal val bleWriteRequestDataShareFlow = charWriteRequestDataShareFlow.asSharedFlow()

        internal fun <T : Any> sendIntentToServiceClass(
            context: Context, action: String, extraName: String? = null, extraValue: T? = null
        ) {
            val intent = Intent(action).apply {
                component = ComponentName(
                    context.packageName, BlePeripheralService::class.java.name
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