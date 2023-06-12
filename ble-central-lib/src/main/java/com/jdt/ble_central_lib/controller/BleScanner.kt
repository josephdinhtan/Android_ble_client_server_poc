package com.jdt.ble_central_lib.controller

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.jdt.ble_central_lib.controller.callback.BleScanCallback
import timber.log.Timber
import java.util.UUID

internal class BleScanner(
    private val context: Context
) {
    private var isScanning = false
    private val bluetoothLeScanner = BluetoothUtility.getBleScanner(context)
    private val handler = Handler(Looper.getMainLooper())

    private var scanUuid: List<UUID>? = null

    @SuppressLint("MissingPermission")
    internal fun startLeScan(
        bleScanCallback: BleScanCallback,
        serviceFilterUUIDs: List<UUID>? = null,
        scanTimeout: Long?
    ) {
        Timber.d("startScan ")
        scanUuid = serviceFilterUUIDs
        if (isScanning) {
            Timber.e("Already scanning")
        } else {
            handler.postDelayed({
                safeStopBleScan(bleScanCallback)
            }, scanTimeout ?: DEFAULT_SCAN_PERIOD)
            isScanning = true
            bleScanCallback.onScanStarted(true)
            bluetoothLeScanner.startScan(
                getScanFilters(serviceFilterUUIDs),
                scanSettingsSinceM,
                getLeScanCallback(bleScanCallback)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLeScanCallback(bleScanCallback: BleScanCallback): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                val name: String? = result.scanRecord?.deviceName ?: result.device.name
                Timber.d("Device found name=" + name + " address= " + result.device?.address)
                bleScanCallback.onScanResult(result)
                scanUuid?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (result.isConnectable) {
                            safeStopBleScan(bleScanCallback)
                        }
                    } else {
                        safeStopBleScan(bleScanCallback)
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                Timber.d("onBatchScanResults, ignoring")
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Timber.d("onScanFailed errorCode=$errorCode")
                bleScanCallback.onScanFailed()
                safeStopBleScan(bleScanCallback)
            }
        }
    }

    internal fun stopScan(bleScanCallback: BleScanCallback) {
        safeStopBleScan(bleScanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun safeStopBleScan(bleScanCallback: BleScanCallback) {
        if (!isScanning) {
            Timber.d("Already stopped")
            return
        }

        Timber.d("Stopping BLE scan")
        isScanning = false
        bleScanCallback.onScanFinished()
        bluetoothLeScanner.stopScan(getLeScanCallback(bleScanCallback))
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
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT).setReportDelay(0).build()

    companion object {

        // Stops scanning after 12 seconds.
        private const val DEFAULT_SCAN_PERIOD: Long = 12000
    }
}