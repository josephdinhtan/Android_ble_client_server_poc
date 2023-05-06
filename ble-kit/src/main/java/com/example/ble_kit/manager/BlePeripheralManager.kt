package com.example.ble_kit.manager

import com.example.ble_kit.model.BleLifecycleState
import kotlinx.coroutines.flow.SharedFlow

interface BlePeripheralManager {
    val bleLifecycleState: SharedFlow<BleLifecycleState>
    val bleWriteRequestData: SharedFlow<ByteArray>
    fun start()
    fun stop()
    fun sendData(data: ByteArray)
}