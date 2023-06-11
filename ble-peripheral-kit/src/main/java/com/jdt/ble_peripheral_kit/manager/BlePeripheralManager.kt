package com.jdt.ble_peripheral_kit.manager

import com.jdt.ble_peripheral_kit.model.BleLifecycleState
import kotlinx.coroutines.flow.SharedFlow

interface BlePeripheralManager {
    val bleLifecycleState: SharedFlow<BleLifecycleState>
    val bleWriteRequestData: SharedFlow<ByteArray>
    fun start()
    fun stop()
    fun sendData(data: ByteArray)
}