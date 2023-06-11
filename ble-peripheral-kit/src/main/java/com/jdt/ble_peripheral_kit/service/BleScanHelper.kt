package com.jdt.ble_peripheral_kit.service

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
import com.jdt.ble_peripheral_kit.definition.UUIDTable
import com.jdt.ble_peripheral_kit.model.BleLifecycleState
import com.jdt.ble_peripheral_kit.utils.BluetoothUtility
import timber.log.Timber

internal class BleScanHelper(
    private val context: Context, private val bleLifecycleStateChange: (BleLifecycleState) -> Unit
) {
    private var isScanning = false


    @SuppressLint("MissingPermission")
    internal fun startScan() {
        Timber.d("startScan()")
        if (BluetoothUtility.isBluetoothOn(context)) {
            if (isScanning) {
                Log.e(TAG, "Already scanning")
            } else {
                val serviceFilter = mScanFilter.serviceUuid?.uuid.toString()
                Log.d(TAG, "Starting BLE scan, filter: $serviceFilter")
                bleLifecycleStateChange(BleLifecycleState.Scanning)
                isScanning = true
                if (isBluetoothScanPermissionGranted) {
                    BluetoothUtility.getBleScanner(context)
                        .startScan(mutableListOf(mScanFilter), scanSettingsSinceM, scanCallback)
                } else {
                    Log.e(TAG, "Bluetooth SCAN permission denied")
                }
            }
        } else {
            Log.e(TAG, "Bluetooth OFF")
        }
    }

    internal fun stopScan() {
        safeStopBleScan()
    }

    private val isBluetoothScanPermissionGranted: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                TODO("VERSION.SDK_INT < S")
            }
        }
    private val isBluetoothConnectPermissionGranted: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT
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
                TAG, "Device found name=$name address= ${result.device?.address}"
            )
            safeStopBleScan()
            bleLifecycleStateChange(BleLifecycleState.Connecting)
            BleCentralService.sendIntentToServiceClass(
                context,
                BleCentralService.ACTION_BLE_DEVICE_FOUND,
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
            BluetoothUtility.getBleScanner(context).stopScan(scanCallback)
        } else {
            Log.e(TAG, "Bluetooth SCAN permission denied")
        }
    }

    private val mScanFilter =
        ScanFilter.Builder().setServiceUuid(ParcelUuid(UUIDTable.GATT_SERVICE_UUID)).build()

    private val scanSettingsSinceM =
        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT).setReportDelay(0).build()

    companion object {
        private const val TAG = "CentralBLEScanner_Jdt"
    }
}