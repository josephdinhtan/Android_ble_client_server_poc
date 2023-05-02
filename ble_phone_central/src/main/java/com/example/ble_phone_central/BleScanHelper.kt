package com.example.ble_phone_central

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat

class BleScanHelper(
    context: Context,
    bleLifecycleStateChange: (BleLifecycleState) -> Unit
) {
    private val mContext = context
    private var isScanning = false


    @SuppressLint("MissingPermission")
    internal fun startScan() {
        Log.d(TAG, "startScan()")
        if (CentralBluetoothUtility.isBluetoothOn(mContext)) {
            if (isScanning) {
                Log.e(TAG, "Already scanning")
            } else {
                val serviceFilter = mScanFilter.serviceUuid?.uuid.toString()
                Log.d(TAG, "Starting BLE scan, filter: $serviceFilter")

                isScanning = true
                if (isBluetoothScanPermissionGranted) {
                    CentralBluetoothUtility.getBleScanner(mContext)
                        .startScan(mutableListOf(mScanFilter), scanSettingsSinceM, scanCallback)
                } else {
                    Log.e(TAG, "Bluetooth SCAN permission denied")
                }
            }
        } else {
            Log.e(TAG, "Bluetooth OFF")
        }
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

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!isBluetoothConnectPermissionGranted) {
                Log.e(TAG, "Bluetooth CONNECT permission denied")
                return
            }
            val name: String? = result.scanRecord?.deviceName ?: result.device.name
            Log.d(
                TAG,
                "Device found name=$name address= ${result.device?.address}"
            )
            safeStopBleScan()
            bleLifecycleStateChange(BleLifecycleState.Connecting)
            CentralBleService.sendIntentToServiceClass(
                mContext,
                CentralBleService.ACTION_BLE_DEVICE_FOUND,
                BluetoothDevice.EXTRA_DEVICE,
                result.device
            )
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            Log.d(TAG, "onBatchScanResults, ignoring")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.d(TAG, "onScanFailed errorCode=$errorCode")
            safeStopBleScan()
            bleLifecycleStateChange(BleLifecycleState.Disconnected)
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeStopBleScan() {
        if (!isScanning) {
            Log.d(TAG, "Already stopped")
            return
        }

        Log.d(TAG, "Stopping BLE scan")
        isScanning = false
        if (isBluetoothScanPermissionGranted) {
            CentralBluetoothUtility.getBleScanner(mContext).stopScan(scanCallback)
        } else {
            Log.e(TAG, "Bluetooth SCAN permission denied")
        }
    }

    private val mScanFilter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(UUIDTable.GATT_SERVICE_UUID))
        .build()

    private val scanSettingsSinceM = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
        .setReportDelay(0)
        .build()

    companion object {
        private const val TAG = "CentralBLEScanner_Jdt"
    }
}