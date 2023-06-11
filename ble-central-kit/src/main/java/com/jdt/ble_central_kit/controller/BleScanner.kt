package com.jdt.ble_central_kit.controller

import android.annotation.SuppressLint
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.jdt.ble_central_kit.controller.callback.BleScanCallback
import com.jdt.ble_central_kit.utils.BluetoothUtility
import timber.log.Timber
import java.util.UUID

internal class BleScanner(
    private val context: Context,
    private val bleScanCallback: BleScanCallback,
    private val scanTimeout: Long = DEFAULT_SCAN_PERIOD
) {
    private var isScanning = false
    private val bluetoothLeScanner = BluetoothUtility.getBleScanner(context)
    private val handler = Handler(Looper.getMainLooper())

    private var scanUuid: List<UUID>? = null

    internal fun reScan() {
        Timber.d("reScan")
        startLeScan(scanUuid)
    }

    @SuppressLint("MissingPermission")
    internal fun startLeScan(filterserviceUUIDs: List<UUID>? = null) {
        Timber.d("startScan ")
        scanUuid = filterserviceUUIDs
        if (BluetoothUtility.isBluetoothOn(context)) {
            if (isScanning) {
                Timber.e("Already scanning")
            } else {
                handler.postDelayed({
                    safeStopBleScan()
                }, scanTimeout)
                isScanning = true
                if (!BluetoothUtility.isBluetoothScanPermissionGranted(context)) {
                    Timber.w("Bluetooth SCAN permission denied")
                }
                bluetoothLeScanner.startScan(
                    getScanFilters(filterserviceUUIDs),
                    scanSettingsSinceM,
                    bleScanCallback
                )
            }
        } else {
            Timber.e("Bluetooth OFF")
        }
    }

    internal fun stopScan() {
        safeStopBleScan()
    }

    @SuppressLint("MissingPermission")
    private fun safeStopBleScan() {
        if (!isScanning) {
            Timber.d("Already stopped")
            return
        }

        Timber.d("Stopping BLE scan")
        isScanning = false
        bleScanCallback.onScanFinished()
        bluetoothLeScanner.stopScan(bleScanCallback)
    }

    private fun getScanFilter(serviceUUID: UUID): ScanFilter {
        return ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUUID)).build()
    }

    private fun getScanFilters(serviceUUIDs: List<UUID>?): List<ScanFilter> {
        val scanFilterList = mutableListOf<ScanFilter>()
        serviceUUIDs?.forEach { serviceUUID ->
            scanFilterList.add(getScanFilter(serviceUUID))
        }
        return scanFilterList
    }

    private val scanSettingsSinceM =
        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0)
            .build()

    companion object {

        // Stops scanning after 11 seconds.
        private const val DEFAULT_SCAN_PERIOD: Long = 11000
    }
}