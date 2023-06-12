package com.jdt.ble_central_lib.controller

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.jdt.ble_central_lib.controller.BleCentralData.*
import com.jdt.ble_central_lib.controller.callback.BleGattCallback
import com.jdt.ble_central_lib.controller.callback.BleGattDescriptorValue
import timber.log.Timber
import java.util.UUID

internal class GattManager(
    private val context: Context,
    private val maxRequestRetryCount: Int = 3,
) {
    private var mConnectionState = BluetoothAdapter.STATE_DISCONNECTED
    private var mBluetoothGatt: BluetoothGatt? = null

    private var mGattRequestDispatcher: GattRequestDispatcher? = null

    private fun getBluetoothGattCallback(bleGattCallback: BleGattCallback): BluetoothGattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt, status: Int, newState: Int
            ) {
                val deviceAddress = gatt.device.address
                mConnectionState = newState
                val bondStateStr = when (gatt.device.bondState) {
                    BluetoothDevice.BOND_NONE -> "BOND_NONE"
                    BluetoothDevice.BOND_BONDING -> "BOND_BONDING"
                    BluetoothDevice.BOND_BONDED -> "BOND_BONDED"
                    else -> "UNKNOW"
                }
                Timber.e("onConnectionStateChange() bondstate: $bondStateStr")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (gatt.device.bondState == BluetoothDevice.BOND_NONE || gatt.device.bondState == BluetoothDevice.BOND_BONDED) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                bleGattCallback.onGattConnected()
                                mGattRequestDispatcher?.addDiscoverServicesRequest()
                            }

                            BluetoothProfile.STATE_DISCONNECTED -> {
                                Timber.d("Disconnected from $deviceAddress")
                                bleGattCallback.onGattDisconnected()
                                closeGattAndcleanUp()
                            }
                        }
                    } else if (gatt.device.bondState == BluetoothDevice.BOND_BONDING) {
                        Timber.e("ERROR: BOND_BONDING waiting for bonding to complete")
                    }
                } else {
                    Timber.e("ERROR: onConnectionStateChange status=" + status + " deviceAddress=" + deviceAddress + ", disconnecting")
                    closeGattAndcleanUp()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Timber.d("onServicesDiscovered services.count=" + gatt.services.size + " status=" + status)
                for (gattService in gatt.services) {
                    Timber.d("services: " + gattService.uuid.toString())
                }

                if (status == 129 /*GATT_INTERNAL_ERROR*/) {
                    // it should be a rare case, this article recommends to disconnect:
                    // https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07
                    Timber.e("ERROR: status=129 (GATT_INTERNAL_ERROR), disconnecting")
                    gatt.disconnect()
                    return
                }
                bleGattCallback.onServicesDiscovered(gatt.services)
                mGattRequestDispatcher?.completedRequest()
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
            ) {
                onCharacteristicReadResult(
                    characteristic, characteristic.value, status, bleGattCallback
                )
                // TODO: consider do retry if got error
                mGattRequestDispatcher?.completedRequest()
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                data: ByteArray,
                status: Int
            ) {
                onCharacteristicReadResult(characteristic, data, status, bleGattCallback)
                // TODO: consider do retry if got error
                mGattRequestDispatcher?.completedRequest()
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
            ) {
                val log: String = "onCharacteristicWrite " + when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        bleGattCallback.onCharacteristicWriteResult(true)
                        "GATT_SUCCESS"
                    }

                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        bleGattCallback.onCharacteristicWriteResult(false)
                        "GATT_WRITE_NOT_PERMITTED"
                    }

                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        bleGattCallback.onCharacteristicWriteResult(false)
                        "GATT_INVALID_ATTRIBUTE_LENGTH"
                    }

                    else -> {
                        bleGattCallback.onCharacteristicWriteResult(false)
                        "UNKNOW $status"
                    }
                }
                Timber.d(log)
                // TODO: consider do retry if got error
                mGattRequestDispatcher?.completedRequest()
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
            ) {
                onCharacteristicChangedResult(characteristic, characteristic.value, bleGattCallback)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, data: ByteArray
            ) {
                onCharacteristicChangedResult(characteristic, data, bleGattCallback)
            }

            override fun onDescriptorRead(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
                value: ByteArray
            ) {
                onDescriptorReadResult(gatt, descriptor, status, value, bleGattCallback)
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onDescriptorRead(
                gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int
            ) {
                gatt?.let {
                    descriptor?.let {
                        onDescriptorReadResult(
                            gatt, descriptor, status, descriptor.value, bleGattCallback
                        )
                    }
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Timber.d("onDescriptorWrite: charUUID: ${descriptor.characteristic.uuid} descriptor GATT_SUCCESS")
                    bleGattCallback.onDescriptorWriteResult(true)
                } else {
                    // TODO: consider do retry if got error
                    Timber.e("ERROR: onDescriptorWrite status=" + status + " uuid=" + descriptor.uuid + " char=" + descriptor.characteristic.uuid)
                    bleGattCallback.onDescriptorWriteResult(false)
                }
                mGattRequestDispatcher?.completedRequest()
            }
        }

    private fun onCharacteristicChangedResult(
        characteristic: BluetoothGattCharacteristic,
        receiveData: ByteArray,
        bleGattCallback: BleGattCallback
    ) {
        val strValue = receiveData.toString(Charsets.UTF_8)
        Timber.d("onCharacteristicChanged value=" + "\"" + strValue + "\"")
        bleGattCallback.onCharacteristicIndication(characteristic, receiveData)
    }

    private fun onCharacteristicReadResult(
        characteristic: BluetoothGattCharacteristic,
        receiveData: ByteArray,
        status: Int,
        bleGattCallback: BleGattCallback
    ) {
        val strValue = receiveData.toString(Charsets.UTF_8)
        val log = "onCharacteristicRead " + when (status) {
            BluetoothGatt.GATT_SUCCESS -> "OK, value=\"$strValue\""
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
            else -> "error $status"
        }
        Timber.d("onCharacteristicRead " + log)
        bleGattCallback.onCharacteristicReadResult(characteristic, receiveData)
    }

    private fun onDescriptorReadResult(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray,
        bleGattCallback: BleGattCallback
    ) {
        val responseValue =
            if (value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                BleGattDescriptorValue.ENABLE_INDICATION_VALUE
            } else if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                BleGattDescriptorValue.ENABLE_NOTIFICATION_VALUE
            } else {
                BleGattDescriptorValue.DISABLE_NOTIFICATION_VALUE
            }
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                bleGattCallback.onDescriptorReadResult(descriptor, responseValue, true)
            }

            else -> {
                bleGattCallback.onDescriptorReadResult(descriptor, responseValue, false)
            }
        }
    }

    val bondStateReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: run { return }
                Timber.d("bondStateReceiver action: $action")
                if (mBluetoothGatt == null) {
                    Timber.e("bondStateReceiver mBluetoothGatt = null")
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val devices = intent.getParcelableArrayExtra(
                        BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                    )
                    var isFound = false
                    devices?.forEach { device ->
                        Timber.d("bondStateReceiver device: ${device?.address}")
                        if (device.address.equals(mBluetoothGatt!!.device.address)) {
                            isFound = true
                        }
                    }
                    if (!isFound) {
                        Timber.e("bondStateReceiver bluetoothDevice not match")
                        return
                    }
                } else {
                    val device = intent.getParcelableExtra<BluetoothDevice>(
                        BluetoothDevice.EXTRA_DEVICE
                    )
                    Timber.d("bondStateReceiver device: ${device?.address}")
                    if (device?.address.equals(mBluetoothGatt!!.device.address) == false) {
                        Timber.e("bondStateReceiver device?.address: ${device?.address} not match: ${mBluetoothGatt!!.device.address}")
                        return
                    }
                }

                if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                    val bondState =
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val previousBondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR
                    )
                    when (bondState) {
                        BluetoothDevice.BOND_BONDING -> {
                            Timber.e("bondStateReceiver bondstate: BOND_BONDING")
                        }

                        BluetoothDevice.BOND_BONDED -> {
                            Timber.e("bondStateReceiver bondstate: BOND_BONDED")
                            if (mBluetoothGatt?.services!!.isEmpty()) {
                                Timber.d("addDiscoverServicesRequest after device BONDED")
                                mGattRequestDispatcher?.addDiscoverServicesRequest()
                            }
                        }

                        BluetoothDevice.BOND_NONE -> {
                            if (previousBondState == BluetoothDevice.BOND_BONDING) {
                                Timber.e("bondStateReceiver bondstate: BOND_NONE, Bonding failed")
                                mGattRequestDispatcher?.addDiscoverServicesRequest()
                            } else {
                                Timber.e("bondStateReceiver bondstate: BOND_NONE, Bond lost")
                            }
                        }
                    }
                }
            }
        }
    }

    internal fun connect(bleGattCallback: BleGattCallback, device: BluetoothDevice): BluetoothGatt {
        Timber.d("trigger connect Gatt")
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(bondStateReceiver, filter)

        bleGattCallback.onGattConnecting()
        mBluetoothGatt = device.connectGatt(
            context, false, getBluetoothGattCallback(bleGattCallback), BluetoothDevice.TRANSPORT_LE
        )

        mGattRequestDispatcher = GattRequestDispatcher(mBluetoothGatt!!, maxRequestRetryCount)
        return mBluetoothGatt!!
    }

    internal fun registerSubscribeAll(): Boolean {
        if (!(mBluetoothGatt?.services?.isNotEmpty() ?: false)) {
            return false
        }
        var result = true
        mBluetoothGatt?.services?.forEach { service ->
            service.characteristics.forEach { characteristic ->
                if (!registerDescriptor(
                        service.uuid, characteristic.uuid, true
                    )
                ) {
                    result = false
                }
            }
        }
        return result
    }

    @Suppress("DEPRECATION")
    internal fun registerDescriptor(
        serviceUUID: UUID, charUUID: UUID, enable: Boolean
    ): Boolean {
        Timber.d("subscribeToCharacteristicIndication charUUID: $charUUID")
        mBluetoothGatt ?: run {
            Timber.e("ERROR: read failed, no connected device mBluetoothGatt = null")
            return false
        }
        val characteristicForIndicate = getGattServiceCharacteristic(serviceUUID, charUUID) ?: run {
            Timber.e("ERROR: registerIndication failed, characteristic unavailable: $charUUID")
            return false
        }
        if (mBluetoothGatt!!.setCharacteristicNotification(characteristicForIndicate, true)) {
            Timber.e("ERROR: setNotification failed for ${characteristicForIndicate.uuid}")
            return false
        }
        val value = when (enable) {
            true -> {
                if (characteristicForIndicate.isIndicatable()) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else if (characteristicForIndicate.isNotifiable()) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    Timber.e("ERROR: registerDescriptor failed, characteristic not Indicatable and Notifiable $charUUID")
                    return false
                }
            }

            false -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }
        var result = true
        characteristicForIndicate.descriptors.forEach { cccDescriptor ->
            if (!(mGattRequestDispatcher?.addWriteDescriptorRequest(cccDescriptor, value)
                    ?: false)
            ) {
                result = false
            }
        }
        return result
    }

    internal fun requestCharacteristicRead(
        serviceUUID: UUID, charUUID: UUID
    ): Boolean {
        Timber.d("onRequestCharacteristicRead service: $serviceUUID, char: $charUUID")
        mBluetoothGatt ?: run {
            Timber.e("ERROR: read failed, no connected device mBluetoothGatt = null")
            return false
        }
        var characteristic = getGattServiceCharacteristic(serviceUUID, charUUID) ?: run {
            Timber.e(
                "ERROR: read failed, characteristic unavailable $charUUID"
            )
            return false
        }
        if (!characteristic.isReadable()) {
            Timber.e("ERROR: read failed, characteristic not readable $charUUID")
            return false
        }
        return mGattRequestDispatcher?.addReadCharacteristicRequest(characteristic) ?: false
    }

    @Suppress("DEPRECATION")
    internal fun requestCharacteristicWrite(
        serviceUUID: UUID, charUUID: UUID, data: ByteArray
    ): Boolean {
        Timber.d("requestCharacteristicWrite data: ${data.toString(Charsets.UTF_8)}")
        mBluetoothGatt ?: run {
            Timber.e("ERROR: write failed, no connected device")
            return false
        }
        var characteristic = getGattServiceCharacteristic(serviceUUID, charUUID) ?: run {
            Timber.e("ERROR: write failed, characteristic unavailable $charUUID")
            return false
        }
        val writeType = when {
            characteristic.isWriteable() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            characteristic.isWriteableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }

            else -> {
                Timber.e("ERROR: write failed, characteristic not writeable $charUUID")
                return false
            }
        }
        return mGattRequestDispatcher?.addWriteCharacteristicRequest(
            characteristic, data, writeType
        ) ?: false
    }

    internal fun disconnect() {
        Timber.d("trigger disconnect gatt")
        context.unregisterReceiver(bondStateReceiver)
        mBluetoothGatt?.disconnect()
        //mBluetoothGatt?.close() should call close when gatt disconnected callback
        //call close here will unregister bluetooth stack callback and never get disconnected event
    }

    private fun closeGattAndcleanUp() {
        Timber.d("closeGattAndcleanUp")
        mBluetoothGatt?.close()
        mGattRequestDispatcher?.cleanUp()
        mBluetoothGatt = null
        mGattRequestDispatcher = null
    }

    private fun getGattServiceCharacteristic(
        serviceUUID: UUID, charUUID: UUID
    ): BluetoothGattCharacteristic? {
        return mBluetoothGatt?.getService(serviceUUID)?.getCharacteristic(charUUID)
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
}