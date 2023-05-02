package com.example.ble_accy_perif

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.Arrays
import java.util.UUID

class PerifGattManager(context: Context) {
    private val mContext = context
    private var mConnectionState = BluetoothAdapter.STATE_DISCONNECTED
    private var mGattServer: BluetoothGattServer? = null
    private val mSubscribedDevices = mutableSetOf<BluetoothDevice>()

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Central did connect")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Central did disconnect")
                mSubscribedDevices.remove(device)
            } else {
                Log.d(TAG, "No handle state: $newState")
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            Log.d(TAG, "onNotificationSent status=$status")
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            var log: String = "onCharacteristicRead requestId=$requestId, offset=$offset"
            if (characteristic.uuid == UUIDTable.GATT_CHAR_FOR_READ_UUID) {
                val strValue = "This is a dummy"
                if (ActivityCompat.checkSelfPermission(
                        mContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Permission denied BLUETOOTH_CONNECT")
                } else {
                    mGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        strValue.toByteArray(Charsets.UTF_8)
                    )
                    log += "\nresponse=success, value=\"$strValue\""
                    Log.d(TAG, log)
                }
            } else {
                mGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                log += "\nresponse=failure, unknown UUID\n${characteristic.uuid}"
                Log.d(TAG, log)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            var log: String =
                "onCharacteristicWrite offset=$offset responseNeeded=$responseNeeded preparedWrite=$preparedWrite"
            if (characteristic.uuid == UUIDTable.GATT_CHAR_FOR_WRITE_UUID) {
                var strValue = value?.toString(Charsets.UTF_8) ?: ""
                if (responseNeeded) {
                    if (ActivityCompat.checkSelfPermission(
                            mContext,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.e(
                            TAG,
                            "onCharacteristicWriteRequest() Permission denied BLUETOOTH_CONNECT"
                        )
                    } else {
                        mGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            strValue.toByteArray(Charsets.UTF_8)
                        )
                        log += "\nresponse=success, value=\"$strValue\""
                    }
                } else {
                    log += "\nresponse=notNeeded, value=\"$strValue\""
                }
            } else {
                if (responseNeeded) {
                    mGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null
                    )
                    log += "\nresponse=failure, unknown UUID\n${characteristic.uuid}"
                } else {
                    log += "\nresponse=notNeeded, unknown UUID\n${characteristic.uuid}"
                }
            }
            Log.d(TAG, log)
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            var log = "onDescriptorReadRequest"
            if (descriptor.uuid == UUIDTable.GATT_CCC_DESCRIPTOR_UUID) {
                val returnValue = if (mSubscribedDevices.contains(device)) {
                    log += " CCCD response=ENABLE_NOTIFICATION"
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    log += " CCCD response=DISABLE_NOTIFICATION"
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                if (ActivityCompat.checkSelfPermission(
                        mContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "onDescriptorReadRequest() Permission denied BLUETOOTH_CONNECT")
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                } else {
                    mGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        returnValue
                    )
                }
            } else {
                log += " unknown uuid=${descriptor.uuid}"
                mGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
            Log.d(TAG, log)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            var strLog = "onDescriptorWriteRequest responseNeeded: $responseNeeded"
            if (descriptor.uuid == UUIDTable.GATT_CCC_DESCRIPTOR_UUID) {
                var status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                if (descriptor.characteristic.uuid == UUIDTable.GATT_CHAR_FOR_INDICATE_UUID) {
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                        mSubscribedDevices.add(device)
                        status = BluetoothGatt.GATT_SUCCESS
                        strLog += ", subscribed"
                    } else if (Arrays.equals(
                            value,
                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        )
                    ) {
                        mSubscribedDevices.remove(device)
                        status = BluetoothGatt.GATT_SUCCESS
                        strLog += ", unsubscribed"
                    }
                }
                if (responseNeeded) {
                    if (ActivityCompat.checkSelfPermission(
                            mContext,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.e(TAG, "onDescriptorWriteRequest() Permission denied BLUETOOTH_CONNECT")
                    } else {
                        Log.d(TAG, "send Response")
                        if (mGattServer == null) Log.e(TAG, "mGattServer = NULL ")
                        mGattServer?.sendResponse(device, requestId, status, 0, null)
                    }
                }
            } else {
                strLog += " unknown uuid=${descriptor.uuid}"
                if (responseNeeded) {
                    mGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null
                    )
                }
            }
            Log.d(TAG, strLog)
        }
    }


    internal fun bleStartGattServer() {
        mGattServer = PerifBluetoothUtility.openGattServer(mContext, gattServerCallback)
        val service =
            BluetoothGattService(
                UUIDTable.GATT_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
        var charForRead = BluetoothGattCharacteristic(
            UUIDTable.GATT_CHAR_FOR_READ_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        var charForWrite = BluetoothGattCharacteristic(
            UUIDTable.GATT_CHAR_FOR_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        var charForIndicate = BluetoothGattCharacteristic(
            UUIDTable.GATT_CHAR_FOR_INDICATE_UUID,
            BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        var charConfigDescriptor = BluetoothGattDescriptor(
            UUIDTable.GATT_CCC_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        charForIndicate.addDescriptor(charConfigDescriptor)

        service.addCharacteristic(charForRead)
        service.addCharacteristic(charForWrite)
        service.addCharacteristic(charForIndicate)

        val result = mGattServer!!.addService(service)
        Log.d(
            TAG,
            "addService " + when (result) {
                true -> "OK"
                false -> "fail"
            }
        )
    }

    internal fun bleStopGattServer() {
        if (ActivityCompat.checkSelfPermission(
                mContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "bleStopGattServer() Permission denied BLUETOOTH_CONNECT")
            return
        }
        mGattServer?.close()
        mGattServer = null
        Log.d(TAG, "gattServer closed")
    }

    internal fun onNotifyToSubscribedDevices(data: ByteArray) {
        val charForIndicate = getGattServiceCharacterictis(UUIDTable.GATT_CHAR_FOR_INDICATE_UUID)
        charForIndicate?.let {
            for (device in mSubscribedDevices) {
                Log.d(
                    TAG,
                    "sending indication: \"${data.toString(Charsets.UTF_8)}\" to BT device: ${device.name}, address: ${device.address}"
                )
                if (ActivityCompat.checkSelfPermission(
                        mContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Permission denied BLUETOOTH_CONNECT")
                } else {
                    mGattServer?.notifyCharacteristicChanged(device, it, true, data)
                }
            }
        }
    }

    private fun getGattServiceCharacterictis(uuid: UUID): BluetoothGattCharacteristic? {
        return mGattServer?.getService(UUIDTable.GATT_SERVICE_UUID)?.getCharacteristic(uuid)
    }

    private fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    private fun BluetoothGattCharacteristic.isWriteable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    private fun BluetoothGattCharacteristic.isWriteableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    private fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    private fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    companion object {
        private const val TAG = "PerifGattManager_Jdt"

        private val ENABLE_BATTERY_SAVE = byteArrayOf(0x01)
        private val CHANGE_RESOLUTION_TO_HD = byteArrayOf(0x02)
        private val CHANGE_RESOLUTION_TO_FHD = byteArrayOf(0x02)
    }
}