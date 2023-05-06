package com.example.ble_phone_central.manager

import android.content.Context
import android.util.Log
import com.example.ble_phone_central.model.BleLifecycleState
import com.example.ble_phone_central.service.BleCentralService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class BleManagerImpl constructor(
    private val context: Context
) : BleManager {
    override val bleLifecycleState: SharedFlow<BleLifecycleState> =
        BleCentralService.bleLifecycleStateShareFlow.asSharedFlow()
    override val bleIndicationData: SharedFlow<ByteArray> =
        BleCentralService.bleIndicationDataShareFlow.asSharedFlow()
    override val wifiDirectServerName: SharedFlow<String> =
        BleCentralService.wifiDirectServerNameShareFlow.asSharedFlow()

    override fun start() {
        Log.d("BleManagerImpl_Jdt", "start()")
        BleCentralService.sendIntentToServiceClass<Any>(
            context,
            BleCentralService.ACTION_BLE_SCAN_START
        )
    }

    override fun stop() {
        BleCentralService.sendIntentToServiceClass<Any>(
            context,
            BleCentralService.ACTION_BLE_STOP
        )
    }

    override fun sendData(data: ByteArray) {
        BleCentralService.sendIntentToServiceClass<ByteArray>(
            context,
            BleCentralService.ACTION_CHAR_REQUEST_WRITE,
            BleCentralService.EXTRA_CHAR_REQUEST_WRITE_DATA,
            data
        )
    }

    override fun requestReadWifiName() {
        BleCentralService.sendIntentToServiceClass<ByteArray>(
            context,
            BleCentralService.ACTION_CHAR_REQUEST_READ
        )
    }
}