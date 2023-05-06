package com.example.ble_phone_central.manager

import com.example.ble_phone_central.model.BleLifecycleState
import kotlinx.coroutines.flow.SharedFlow

interface BleManager {
    val bleLifecycleState: SharedFlow<BleLifecycleState>
    val bleIndicationData: SharedFlow<ByteArray>
    val wifiDirectServerName: SharedFlow<String>

    fun start()
    fun stop()
    fun sendData(data: ByteArray)
    fun requestReadWifiName()
}