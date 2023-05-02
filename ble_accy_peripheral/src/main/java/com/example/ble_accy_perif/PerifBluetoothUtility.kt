package com.example.ble_accy_perif

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context

object PerifBluetoothUtility {

    private var bluetoothManager: BluetoothManager? = null

    private fun getBluetoothManager(context: Context): BluetoothManager {
        if (bluetoothManager == null) {
            bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        }
        return bluetoothManager!!
    }

    internal fun isBluetoothOn(context: Context): Boolean {
        return getBluetoothManager(context).adapter.isEnabled
    }

    internal fun getBleAdvertiser(context: Context): BluetoothLeAdvertiser {
        return getBluetoothManager(context).adapter.bluetoothLeAdvertiser
    }

    @SuppressLint("MissingPermission")
    internal fun openGattServer(
        context: Context,
        gattServerCallback: BluetoothGattServerCallback
    ): BluetoothGattServer {
        return getBluetoothManager(context).openGattServer(context, gattServerCallback)
    }

    const val TAG = "PerifBluetoothUtility"
}