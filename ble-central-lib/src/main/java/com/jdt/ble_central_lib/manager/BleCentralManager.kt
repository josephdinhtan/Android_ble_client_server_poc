package com.jdt.ble_central_lib.manager

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.jdt.ble_central_lib.controller.callback.BleGattCallback
import com.jdt.ble_central_lib.controller.callback.BleScanCallback
import java.util.UUID

interface BleCentralManager {
    fun initialize(context: Context)
    fun scan(callback: BleScanCallback, scanTimeOut: Long?, serviceFilterUUIDs: List<UUID>?)
    fun stopScan(callback: BleScanCallback)
    fun connect(callback: BleGattCallback, device: BluetoothDevice)
    fun disconnect()
    fun read(serviceUUID: UUID, charUUID: UUID): Boolean
    fun write(serviceUUID: UUID, charUUID: UUID, data: ByteArray): Boolean
    fun subscribeIndication(serviceUUID: UUID, charUUID: UUID, enable: Boolean): Boolean
    fun subscribeAllIndications(): Boolean
    fun isBluetoothEnabled(): Boolean
}