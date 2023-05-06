package com.example.ble_phone_central

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.UUID

class CentralGattManager(
    context: Context,
    device: BluetoothDevice,
    bleLifecycleStateChange: (BleLifecycleState) -> Unit
) {
    private val mContext = context
    private val mDevice = device
    private var mConnectionState = BluetoothAdapter.STATE_DISCONNECTED
    private var mGatt: BluetoothGatt? = null

    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            connectionStateChangeStatus: Int,
            newState: Int
        ) {
            val deviceAddress = gatt.device.address
            mConnectionState = newState

            if (connectionStateChangeStatus == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    mGatt = gatt
                    CentralBleService.sendIntentToServiceClass<Any>(
                        mContext,
                        CentralBleService.ACTION_GATT_CONNECTED
                    )
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from $deviceAddress")
                    setConnectedGattToNull()
                    gatt.close()
                    bleLifecycleStateChange(BleLifecycleState.Disconnected)
                    CentralBleService.sendIntentToServiceClass<Any>(
                        mContext,
                        CentralBleService.ACTION_GATT_DISCONNECTED
                    )
                }
            } else {
                Log.e(
                    TAG,
                    "ERROR: onConnectionStateChange status=$connectionStateChangeStatus deviceAddress=$deviceAddress, disconnecting"
                )

                setConnectedGattToNull()
                gatt.close()
                bleLifecycleStateChange(BleLifecycleState.Disconnected)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered services.count=${gatt.services.size} status=$status")
            for (gattService in gatt.services) {
                Log.d(TAG, "services: ${gattService.uuid.toString()}")
            }

            if (status == 129 /*GATT_INTERNAL_ERROR*/) {
                // it should be a rare case, this article recommends to disconnect:
                // https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07
                Log.d(TAG, "ERROR: status=129 (GATT_INTERNAL_ERROR), disconnecting")
                gatt.disconnect()
                return
            }

            val service = gatt.getService(UUIDTable.GATT_SERVICE_UUID) ?: run {
                Log.d(
                    TAG,
                    "ERROR: Service not found ${UUIDTable.GATT_SERVICE_UUID.toString()}, disconnecting"
                )
                gatt.disconnect()
                return
            }
            CentralBleService.sendIntentToServiceClass<Any>(
                mContext,
                CentralBleService.ACTION_GATT_SERVICE_DISCOVERED
            )
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            data: ByteArray,
            status: Int
        ) {
            if (characteristic.uuid == UUIDTable.GATT_CHAR_FOR_READ_UUID) {
                val strValue = data.toString(Charsets.UTF_8)
                val log = "onCharacteristicRead " + when (status) {
                    BluetoothGatt.GATT_SUCCESS -> "OK, value=\"$strValue\""
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                    else -> "error $status"
                }
                Log.d(TAG, log)
                CentralBleService.sendIntentToServiceClass(
                    mContext,
                    CentralBleService.ACTION_CHAR_READ_DONE,
                    CentralBleService.EXTRA_CHAR_READ_DATA,
                    data
                )
            } else {
                Log.e(TAG, "onCharacteristicRead unknown uuid $characteristic.uuid")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == UUIDTable.GATT_CHAR_FOR_WRITE_UUID) {
                val log: String = "onCharacteristicWrite " + when (status) {
                    BluetoothGatt.GATT_SUCCESS -> "OK"
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "not allowed"
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "invalid length"
                    else -> "error $status"
                }
                Log.d(TAG, log)
                CentralBleService.sendIntentToServiceClass(
                    mContext,
                    CentralBleService.ACTION_CHAR_WRITE_DONE,
                    CentralBleService.EXTRA_CHAR_WRITE_ACK,
                    status
                )
            } else {
                Log.e(TAG, "onCharacteristicWrite unknown uuid $characteristic.uuid")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            data: ByteArray
        ) {
            if (characteristic.uuid == UUIDTable.GATT_CHAR_FOR_INDICATE_UUID) {
                val strValue = data.toString(Charsets.UTF_8)
                Log.d(TAG, "onCharacteristicChanged value=\"$strValue\"")
                CentralBleService.sendIntentToServiceClass(
                    mContext,
                    CentralBleService.ACTION_RECEIVE_INDICATE,
                    CentralBleService.EXTRA_RECEIVE_INDICATE_DATA,
                    data
                )
            } else {
                Log.e(TAG, "onCharacteristicChanged unknown uuid $characteristic.uuid")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.characteristic.uuid == UUIDTable.GATT_CHAR_FOR_INDICATE_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "onDescriptorWrite: descriptor GATT_SUCCESS")
                    // subscription processed, consider connection is ready for use
                    bleLifecycleStateChange(BleLifecycleState.Connected)
                    CentralBleService.sendIntentToServiceClass<Any>(
                        mContext,
                        CentralBleService.ACTION_GATT_CHAR_INDICATION_SUBSCRIBED
                    )
                } else {
                    Log.e(
                        TAG,
                        "ERROR: onDescriptorWrite status=$status uuid=${descriptor.uuid} char=${descriptor.characteristic.uuid}"
                    )
                }
            } else {
                Log.e(TAG, "onDescriptorWrite unknown uuid $descriptor.characteristic.uuid")
            }
        }
    }

    internal fun connect(): BluetoothGatt {
        Log.d(TAG, "trigger connect Gatt")
        return mDevice.connectGatt(mContext, false, mGattCallback)
    }

    internal fun discoverServices(): Boolean {
        Log.d(TAG, "discoverServices")
        return mGatt?.discoverServices() ?: false
    }

    internal fun subscribeToCharacteristicIndication() {
        Log.d(TAG, "subscribeToIndications")
        val characteristicForIndicate = getGattServiceCharacterictis(UUIDTable.GATT_CHAR_FOR_INDICATE_UUID)
        characteristicForIndicate?.getDescriptor(UUIDTable.GATT_CCC_DESCRIPTOR_UUID)?.let { cccDescriptor ->
            if (mGatt?.setCharacteristicNotification(characteristicForIndicate, true) == false) {
                Log.e(
                    TAG,
                    "ERROR: setNotification(true) failed for ${characteristicForIndicate.uuid}"
                )
                return
            }
            Log.d(TAG, "writeDescriptor")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mGatt?.writeDescriptor(cccDescriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            } else {
                cccDescriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                mGatt?.writeDescriptor(cccDescriptor)
            }
        } ?: run {
            Log.w(TAG, "WARN: characteristic not found ${UUIDTable.GATT_CCC_DESCRIPTOR_UUID}")
        }
    }

    internal fun unsubscribeFromCharacteristic() {
        Log.d(TAG, "unsubscribeFromCharacteristic")
        val characteristicForIndicate = getGattServiceCharacterictis(UUIDTable.GATT_CHAR_FOR_INDICATE_UUID)
        characteristicForIndicate?.getDescriptor(UUIDTable.GATT_CCC_DESCRIPTOR_UUID)?.let { cccDescriptor ->
            if (mGatt?.setCharacteristicNotification(characteristicForIndicate, true) == false) {
                Log.e(
                    TAG,
                    "ERROR: setNotification(true) failed for ${characteristicForIndicate.uuid}"
                )
                return
            }
            Log.d(TAG, "writeDescriptor")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mGatt?.writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            } else {
                cccDescriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                mGatt?.writeDescriptor(cccDescriptor)
            }
        } ?: run {
            Log.w(TAG, "WARN: characteristic not found ${UUIDTable.GATT_CCC_DESCRIPTOR_UUID}")
        }
    }

    internal fun onRequestCharacteristicRead() {
        var gatt = mGatt ?: run {
            Log.e(TAG, "ERROR: read failed, no connected device")
            return
        }
        var characteristic = getGattServiceCharacterictis(UUIDTable.GATT_CHAR_FOR_READ_UUID) ?: run {
            Log.e(TAG, "ERROR: read failed, characteristic unavailable ${UUIDTable.GATT_CHAR_FOR_READ_UUID}")
            return
        }
        if (!characteristic.isReadable()) {
            Log.e(TAG, "ERROR: read failed, characteristic not readable ${UUIDTable.GATT_CHAR_FOR_READ_UUID}")
            return
        }
        gatt.readCharacteristic(characteristic)
    }

    fun onRequestCharacteristicWrite(data: ByteArray) {
        var gatt = mGatt ?: run {
            Log.e(TAG, "ERROR: write failed, no connected device")
            return
        }
        var characteristic = getGattServiceCharacterictis(UUIDTable.GATT_CHAR_FOR_WRITE_UUID) ?: run {
            Log.e(TAG, "ERROR: write failed, characteristic unavailable ${UUIDTable.GATT_CHAR_FOR_WRITE_UUID}")
            return
        }
        if (!characteristic.isWriteable()) {
            Log.e(
                TAG,
                "ERROR: write failed, characteristic not writeable ${UUIDTable.GATT_CHAR_FOR_WRITE_UUID}"
            )
            return
        }
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = data
        gatt.writeCharacteristic(characteristic)
    }

    internal fun close() {
        Log.d(TAG, "close()")
        if (mConnectionState == BluetoothAdapter.STATE_CONNECTED) {
            Log.d(TAG, "trigger close gatt")
            mGatt?.close()
        }
    }

    private fun setConnectedGattToNull() {
        mGatt = null
    }

    private fun writeCharacteristic(uuid: UUID, value: ByteArray): Boolean {
        val characteristic = getGattServiceCharacterictis(uuid)
        characteristic ?: run {
            Log.e(TAG, "No characteristic found for UUID: $uuid")
            return false
        }

        characteristic.writeType = when {
            characteristic.isWriteable() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            characteristic.isWriteableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }

            else -> {
                Log.e(TAG, "Invalid writeType")
                return false
            }
        }
        return true
    }

    private fun getGattServiceCharacterictis(uuid: UUID): BluetoothGattCharacteristic? {
        return mGatt?.getService(UUIDTable.GATT_SERVICE_UUID)?.getCharacteristic(uuid)
    }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i(TAG, "No service and characteristic available, call discoverServices() first?")
            return
        }

        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i(TAG, "\n Service ${service.uuid}\ncharacteristics:\n$characteristicsTable")
        }
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
        private const val TAG = "CentralGattManager_Jdt"

        private val ENABLE_BATTERY_SAVE = byteArrayOf(0x01)
        private val CHANGE_RESOLUTION_TO_HD = byteArrayOf(0x02)
        private val CHANGE_RESOLUTION_TO_FHD = byteArrayOf(0x02)
    }
}