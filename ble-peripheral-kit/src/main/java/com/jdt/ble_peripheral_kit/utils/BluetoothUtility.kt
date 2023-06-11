package com.jdt.ble_peripheral_kit.utils

import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context

object BluetoothUtility {
    private var bluetoothManager: BluetoothManager? = null

    private fun getBluetoothManager(context: Context): BluetoothManager {
        if (bluetoothManager == null) {
            bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        }
        return bluetoothManager!!
    }

    fun isBluetoothOn(context: Context): Boolean {
        return getBluetoothManager(context).adapter.isEnabled
    }

    internal fun getBleScanner(context: Context): BluetoothLeScanner {
        return getBluetoothManager(context).adapter.bluetoothLeScanner
    }
    internal fun getBleAdvertiser(context: Context): BluetoothLeAdvertiser {
        return getBluetoothManager(context).adapter.bluetoothLeAdvertiser
    }

    internal fun openGattServer(
        context: Context,
        gattServerCallback: BluetoothGattServerCallback
    ): BluetoothGattServer {
        return getBluetoothManager(context).openGattServer(context, gattServerCallback)
    }

    const val TAG = "BluetoothUtility"
}