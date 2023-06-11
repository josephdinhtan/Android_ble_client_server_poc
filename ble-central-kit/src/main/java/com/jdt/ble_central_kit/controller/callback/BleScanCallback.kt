package com.jdt.ble_central_kit.controller.callback

import android.bluetooth.le.ScanCallback

abstract class BleScanCallback : ScanCallback() {
    abstract fun onScanStarted(success: Boolean)
    abstract fun onScanFinished()
}