package com.example.ble_accy_perif

import android.Manifest
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat

class BleAdvertiseHelper(context: Context) {
    private val mContext = context
    private var isAdvertising = false


    internal fun startAdvertising() {
        isAdvertising = true
        PerifBluetoothUtility.getBleAdvertiser(mContext)
            .startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }

    internal fun stopAdvertising() {
        isAdvertising = false
        PerifBluetoothUtility.getBleAdvertiser(mContext).stopAdvertising(advertiseCallback)
    }

    private val isBluetoothScanPermissionGranted: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.checkSelfPermission(
                    mContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                TODO("VERSION.SDK_INT < S")
            }
        }
    private val isBluetoothConnectPermissionGranted: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.checkSelfPermission(
                    mContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                TODO("VERSION.SDK_INT < S")
            }
        }

    private val advertiseSettings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
        .setConnectable(true)
        .build()

    private val advertiseData = AdvertiseData.Builder()
        .setIncludeDeviceName(false) // don't include name, because if name size > 8 bytes, ADVERTISE_FAILED_DATA_TOO_LARGE
        .addServiceUuid(ParcelUuid(UUIDTable.GATT_SERVICE_UUID))
        //.addManufacturerData()
        .build()
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "Advertise start success\n${UUIDTable.GATT_SERVICE_UUID}")
        }

        override fun onStartFailure(errorCode: Int) {
            val desc = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "\nADVERTISE_FAILED_DATA_TOO_LARGE"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "\nADVERTISE_FAILED_TOO_MANY_ADVERTISERS"
                ADVERTISE_FAILED_ALREADY_STARTED -> "\nADVERTISE_FAILED_ALREADY_STARTED"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "\nADVERTISE_FAILED_INTERNAL_ERROR"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "\nADVERTISE_FAILED_FEATURE_UNSUPPORTED"
                else -> ""
            }
            Log.d(TAG, "Advertise start failed: errorCode=$errorCode $desc")
            isAdvertising = false
        }
    }

    companion object {
        private const val TAG = "BleAdvertiseHelper_Jdt"
    }
}