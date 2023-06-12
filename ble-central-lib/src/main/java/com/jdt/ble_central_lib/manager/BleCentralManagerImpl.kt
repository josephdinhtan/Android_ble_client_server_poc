package com.jdt.ble_central_lib.manager

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.jdt.ble_central_lib.controller.BleScanner
import com.jdt.ble_central_lib.controller.GattManager
import com.jdt.ble_central_lib.controller.callback.BleGattCallback
import com.jdt.ble_central_lib.controller.callback.BleScanCallback
import com.jdt.ble_central_lib.controller.BluetoothUtility
import timber.log.Timber
import java.util.UUID

class BleCentralManagerImpl : BleCentralManager {

    private lateinit var bleScanner: BleScanner
    private lateinit var gattManager: GattManager
    private lateinit var mContext: Context
    override fun initialize(context: Context) {
        mContext = context
        bleScanner = BleScanner(context)
        gattManager = GattManager(context)
    }

    override fun scan(
        callback: BleScanCallback, scanTimeOut: Long?, serviceFilterUUIDs: List<UUID>?
    ) {
        if (!isBluetoothEnabled()) {
            callback.onScanStarted(false)
            Timber.e("Bluetooth is OFF")
        } else {
            bleScanner.startLeScan(callback, serviceFilterUUIDs, scanTimeOut)
        }
    }

    override fun stopScan(callback: BleScanCallback) {
        bleScanner.stopScan(callback)
    }

    override fun connect(callback: BleGattCallback, device: BluetoothDevice) {
        if (!isBluetoothEnabled()) {
            Timber.e("Bluetooth is OFF")
            callback.onGattConnectFail()
        } else {
            gattManager.connect(callback, device)
        }
    }

    override fun disconnect() = gattManager.disconnect()

    override fun read(serviceUUID: UUID, charUUID: UUID) =
        gattManager.requestCharacteristicRead(serviceUUID, charUUID)

    override fun write(serviceUUID: UUID, charUUID: UUID, data: ByteArray) =
        gattManager.requestCharacteristicWrite(serviceUUID, charUUID, data)

    override fun subscribeIndication(serviceUUID: UUID, charUUID: UUID, enable: Boolean) =
        gattManager.registerDescriptor(serviceUUID, charUUID, enable)

    override fun subscribeAllIndications() = gattManager.registerSubscribeAll()

    override fun isBluetoothEnabled(): Boolean = BluetoothUtility.isBluetoothOn(mContext)
}