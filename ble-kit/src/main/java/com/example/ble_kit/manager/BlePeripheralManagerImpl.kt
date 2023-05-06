package com.example.ble_kit.manager

import android.content.Context
import com.example.ble_kit.model.BleLifecycleState
import com.example.ble_kit.service.BlePeripheralService
import kotlinx.coroutines.flow.SharedFlow

class BlePeripheralManagerImpl(
    private val context: Context
) : BlePeripheralManager {
    override val bleLifecycleState: SharedFlow<BleLifecycleState> =
        BlePeripheralService.bleLifecycleStateShareFlow
    override val bleWriteRequestData: SharedFlow<ByteArray> =
        BlePeripheralService.bleWriteRequestDataShareFlow

    override fun start() {
        BlePeripheralService.sendIntentToServiceClass<Any>(
            context,
            BlePeripheralService.ACTION_BLE_ADVERTISING_START
        )
    }

    override fun stop() {
        BlePeripheralService.sendIntentToServiceClass<Any>(
            context,
            BlePeripheralService.ACTION_BLE_STOP
        )
    }

    override fun sendData(data: ByteArray) {
        BlePeripheralService.sendIntentToServiceClass<ByteArray>(
            context,
            BlePeripheralService.ACTION_NOTIFY_TO_SUBSCRIBED_DEVICES,
            BlePeripheralService.EXTRA_NOTIFY_TO_SUBSCRIBED_DEVICES_DATA,
            data
        )
    }
}