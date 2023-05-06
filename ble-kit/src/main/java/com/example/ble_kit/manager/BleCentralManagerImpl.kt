package com.example.ble_kit.manager

import android.content.Context
import android.util.Log
import com.example.ble_kit.model.BleLifecycleState
import com.example.ble_kit.service.BleCentralService
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class BleCentralManagerImpl constructor(
    private val context: Context
) : BleCentralManager {
    override val bleLifecycleState: SharedFlow<BleLifecycleState> =
        BleCentralService.bleLifecycleStateShareFlow
    override val bleIndicationData: SharedFlow<ByteArray> =
        BleCentralService.bleIndicationDataShareFlow
    override val wifiDirectServerName: SharedFlow<String> =
        BleCentralService.wifiDirectServerNameShareFlow

    override fun start() {
        Log.d("BleCentralManagerImpl_Jdt", "start()")
        BleCentralService.sendIntentToServiceClass<Any>(
            context, BleCentralService.ACTION_BLE_SCAN_START
        )
    }

    override fun stop() {
        BleCentralService.sendIntentToServiceClass<Any>(
            context, BleCentralService.ACTION_BLE_STOP
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
            context, BleCentralService.ACTION_CHAR_REQUEST_READ
        )
    }
}