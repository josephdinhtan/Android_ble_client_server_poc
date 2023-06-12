package com.jdt.ble_peripheral_lib.manager

import com.jdt.ble_peripheral_lib.model.BleLifecycleState
import kotlinx.coroutines.flow.SharedFlow

interface BleCentralManager {
    val bleLifecycleState: SharedFlow<BleLifecycleState>
    val bleIndicationData: SharedFlow<ByteArray>
    val wifiDirectServerName: SharedFlow<String>

    fun start()
    fun stop()
    fun sendData(data: ByteArray)
    fun requestReadWifiName()
}