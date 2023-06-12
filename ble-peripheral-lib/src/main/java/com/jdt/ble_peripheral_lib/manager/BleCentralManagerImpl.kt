package com.jdt.ble_peripheral_lib.manager

import android.content.Context
import android.util.Log
import com.jdt.ble_peripheral_lib.model.BleLifecycleState
import com.jdt.ble_peripheral_lib.service.BleCentralService
import kotlinx.coroutines.flow.SharedFlow

class BleCentralManagerImpl (
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