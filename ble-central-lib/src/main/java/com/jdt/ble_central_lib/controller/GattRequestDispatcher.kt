package com.jdt.ble_central_lib.controller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import java.util.LinkedList
import java.util.Queue

@SuppressLint("MissingPermission")
class GattRequestDispatcher(
    private val bluetoothGatt: BluetoothGatt, private val maxRequestRetryCount: Int
) {
    private val requestQueue: Queue<Runnable> = LinkedList()
    private var requestUnderProcessing = false
    private var retryCount = 0
    private var isRetrying = false

    private val bleHandler = Handler(Looper.getMainLooper())

    internal fun addDiscoverServicesRequest(): Boolean {
        val result = requestQueue.add(Runnable {
            if (!bluetoothGatt.discoverServices()) {
                Timber.e("ERROR: discoverServices failed")
                completedRequest();
            } else {
                Timber.d("bluetoothGatt.discoverServices() running...")
                retryCount++;
            }
        })
        if (result) {
            nextRequest()
        } else {
            Timber.e("ERROR: Could not enqueue read characteristic request")
        }
        return result
    }

    @Suppress("DEPRECATION")
    internal fun addWriteDescriptorRequest(
        cccDescriptor: BluetoothGattDescriptor, value: ByteArray
    ): Boolean {
        val result = requestQueue.add(Runnable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt.writeDescriptor(
                    cccDescriptor, value
                )
            } else {
                cccDescriptor.value = value
                bluetoothGatt.writeDescriptor(cccDescriptor)
            }
        })
        if (result) {
            nextRequest()
        } else {
            Timber.e("ERROR: Could not enqueue WRITE characteristic request")
        }
        return result
    }

    internal fun addReadCharacteristicRequest(characteristic: BluetoothGattCharacteristic): Boolean {
        val result = requestQueue.add(Runnable {
            if (!bluetoothGatt.readCharacteristic(characteristic)) {
                Timber.e("ERROR: readCharacteristic failed for characteristic: $characteristic")
                completedRequest();
            } else {
                retryCount++;
            }
        })
        if (result) {
            nextRequest()
        } else {
            Timber.e("ERROR: Could not enqueue READ characteristic request")
        }
        return result
    }

    internal fun addWriteCharacteristicRequest(
        characteristic: BluetoothGattCharacteristic, data: ByteArray, writeType: Int
    ): Boolean {

        val result = requestQueue.add(Runnable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = bluetoothGatt.writeCharacteristic(
                    characteristic, data, writeType
                )
                Timber.d("result: ${getBluetoothStatusCodeErrorStr(result)}")
                if (result == BluetoothStatusCodes.SUCCESS) {
                    completedRequest();
                } else {
                    retryCount++;
                }
            } else {
                characteristic.writeType = writeType
                characteristic.value = data
                if (!bluetoothGatt.writeCharacteristic(characteristic)) {
                    completedRequest();
                } else {
                    retryCount++;
                }
            }
        })
        if (result) {
            nextRequest()
        } else {
            Timber.e("ERROR: Could not enqueue WRITE characteristic request")
        }
        return result
    }


    /**
     * when a request is complete,
     * After onCharacteristicRead/Write call back should call this fun
     */
    internal fun completedRequest() {
        requestUnderProcessing = false
        isRetrying = false
        requestQueue.poll()
        nextRequest()
    }

    // TODO: consider call retryRequest when got data callback fail or unexpected
    /**
     * If bonding was triggered by a read/write, we must retry it
     */
    internal fun retryRequest() {
        requestUnderProcessing = false
        val currentRequest = requestQueue.peek()
        if (currentRequest != null) {
            if (retryCount >= maxRequestRetryCount) {
                // Max retries reached, give up on this one and proceed
                Timber.e("Max number of tries reached, give up")
                retryCount = 0
                requestQueue.poll()
            } else {
                isRetrying = true
            }
        }
        nextRequest()
    }

    internal fun cleanUp() {
        requestQueue.clear()
        requestUnderProcessing = false
    }

    private fun nextRequest() {
        // If there is still a request being executed then bail out
        if (requestUnderProcessing) {
            return
        }

        // Execute the next request in the queue
        if (requestQueue.size > 0) {
            val currentRequest = requestQueue.peek()
            requestUnderProcessing = true
            retryCount = 0
            bleHandler.post(Runnable {
                try {
                    currentRequest?.run()
                } catch (ex: Exception) {
                    Timber.e(
                        "ERROR: Request exception for device $ex"
                    )
                }
            })
        }
    }

    private fun getBluetoothStatusCodeErrorStr(errorCode: Int): String {
        return when (errorCode) {
            BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED -> "ERROR_BLUETOOTH_NOT_ALLOWED"
            BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED -> "ERROR_BLUETOOTH_NOT_ENABLED"
            BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED -> "ERROR_DEVICE_NOT_BONDED"
            BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED -> "ERROR_GATT_WRITE_NOT_ALLOWED"
            BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> "ERROR_GATT_WRITE_REQUEST_BUSY"
            BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION -> "ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION"
            BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND -> "ERROR_PROFILE_SERVICE_NOT_BOUND"
            BluetoothStatusCodes.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
            BluetoothStatusCodes.FEATURE_NOT_SUPPORTED -> "FEATURE_NOT_SUPPORTED"
            BluetoothStatusCodes.FEATURE_SUPPORTED -> "FEATURE_SUPPORTED"
            BluetoothStatusCodes.SUCCESS -> "SUCCESS"
            else -> "ERROR_UNKNOWN"
        }
    }
}