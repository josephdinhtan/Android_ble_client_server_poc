package com.jdt.ble_central_lib.controller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import timber.log.Timber

internal object BluetoothUtility {
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

    fun getBleScanner(context: Context): BluetoothLeScanner {
        return getBluetoothManager(context).adapter.bluetoothLeScanner
    }

    fun getBleAdvertiser(context: Context): BluetoothLeAdvertiser {
        return getBluetoothManager(context).adapter.bluetoothLeAdvertiser
    }

    @SuppressLint("MissingPermission")
    fun openGattServer(
        context: Context, gattServerCallback: BluetoothGattServerCallback
    ): BluetoothGattServer {
        return getBluetoothManager(context).openGattServer(context, gattServerCallback)
    }

    fun setBluetoothDeviceName(context: Context, name: String) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.e("Permission denied BLUETOOTH_CONNECT")
        }
        getBluetoothManager(context).adapter.name = name
    }

    fun isBluetoothConnectPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }


    fun isBluetoothScanPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}