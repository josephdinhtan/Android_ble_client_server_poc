package com.example.ble_phone_central

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context

object CentralBluetoothUtility {
    private var bluetoothAdapter: BluetoothAdapter? = null

    private fun getBluetoothAdapter(context: Context): BluetoothAdapter {
        if (bluetoothAdapter == null) {
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
        }
        return bluetoothAdapter!!
    }

    internal fun isBluetoothOn(context: Context): Boolean {
        return getBluetoothAdapter(context).isEnabled
    }

    internal fun getBleScanner(context: Context): BluetoothLeScanner {
        return getBluetoothAdapter(context).bluetoothLeScanner
    }


    const val TAG = "BluetoothUtility"
}