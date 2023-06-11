package com.jdt.ble_central_kit.controller

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
import com.jdt.ble_central_kit.controller.BleCentralData.*
import com.jdt.ble_central_kit.controller.callback.BleGattCallback
import timber.log.Timber
import java.util.UUID

internal class GattManager(
    private val context: Context,
    private val bleGattCallback: BleGattCallback,
    private val device: BluetoothDevice,
    private val maxRequestRetryCount: Int,
) {
    private var mConnectionState = BluetoothAdapter.STATE_DISCONNECTED
    private var mBluetoothGatt: BluetoothGatt? = null

    private val mSubscribeDataQueue = mutableListOf<RequestSubscribeData>()
    private var mGattRequestDispatcher: GattRequestDispatcher? = null

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
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
                            mGattRequestDispatcher?.addDiscoverServicesRequest()
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Timber.d("Disconnected from $deviceAddress")
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

            /*
            gatt.getService(UUIDTable.GATT_GENERAL_SERVICE_UUID) ?: run {
                Timber.d(
                    "ERROR: Service not found ${UUIDTable.GATT_GENERAL_SERVICE_UUID.toString()}, disconnecting"
                )
                gatt.disconnect()
                return
            }*/
            mGattRequestDispatcher?.completedRequest()
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    onCharacteristicReadResult(
                        characteristic, characteristic.value, status
                    )
                }

                BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> {
                    Timber.e("ERROR: GATT_INSUFFICIENT_AUTHENTICATION")
                }
            }
            // TODO: consider do retry if got error
            mGattRequestDispatcher?.completedRequest()
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            data: ByteArray,
            status: Int
        ) {
            onCharacteristicReadResult(characteristic, data, status)
            // TODO: consider do retry if got error
            mGattRequestDispatcher?.completedRequest()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            val log: String = "onCharacteristicWrite " + when (status) {
                BluetoothGatt.GATT_SUCCESS -> "OK"
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "not allowed"
                BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "invalid length"
                else -> "error $status"
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
            onCharacteristicChangedResult(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, data: ByteArray
        ) {
            onCharacteristicChangedResult(characteristic, data)
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
            value: ByteArray
        ) {
            super.onDescriptorRead(gatt, descriptor, status, value)
        }

        @Deprecated("Deprecated in Java")
        override fun onDescriptorRead(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            if (mSubscribeDataQueue.isNotEmpty()) {
                val subscribeData = mSubscribeDataQueue.first()
                subscribeToCharacteristicIndication(
                    subscribeData.serviceUUID, subscribeData.charUUID, subscribeData.desUuid
                )
                mSubscribeDataQueue.removeFirst()
            } else {
                //bleLifecycleStateChange(BleCentralLifecycleState.AllSubscribed)
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("onDescriptorWrite: charUUID: ${descriptor.characteristic.uuid} descriptor GATT_SUCCESS")
                //stateMachine.tryEmit(BleCentralStateMachine.SubscribedSuccess(descriptor.characteristic.uuid))
            } else {
                Timber.e("ERROR: onDescriptorWrite status=" + status + " uuid=" + descriptor.uuid + " char=" + descriptor.characteristic.uuid)
            }
            // TODO: consider do retry if got error
            mGattRequestDispatcher?.completedRequest()
        }
    }

    private fun onCharacteristicChangedResult(
        characteristic: BluetoothGattCharacteristic, receiveData: ByteArray
    ) {
        val strValue = receiveData.toString(Charsets.UTF_8)
        Timber.d("onCharacteristicChanged value=" + "\"" + strValue + "\"")
/*        stateMachine.tryEmit(
            BleCentralStateMachine.IndicationCome(
                characteristic.service.uuid, characteristic.uuid, receiveData
            )
        )*/
    }

    private fun onCharacteristicReadResult(
        characteristic: BluetoothGattCharacteristic, receiveData: ByteArray, status: Int
    ) {
        val strValue = receiveData.toString(Charsets.UTF_8)
        val log = "onCharacteristicRead " + when (status) {
            BluetoothGatt.GATT_SUCCESS -> "OK, value=\"$strValue\""
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
            else -> "error $status"
        }
        Timber.d("onCharacteristicRead " + log)
/*        stateMachine.tryEmit(
            BleCentralStateMachine.ReadDone(
                characteristic.service.uuid, characteristic.uuid, receiveData
            )
        )*/
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
                                Timber.d("addDiscoverServicesRequest")
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

    internal fun connect(): BluetoothGatt {
        Timber.d("trigger connect Gatt")
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(bondStateReceiver, filter)

        mBluetoothGatt = device.connectGatt(
            context, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE
        )

        mGattRequestDispatcher = GattRequestDispatcher(mBluetoothGatt!!, maxRequestRetryCount)
        return mBluetoothGatt!!
    }

    internal fun subscribeToCharacteristicIndication(
        subscribedData: List<RequestSubscribeData>
    ) {
        mSubscribeDataQueue.clear()
        mSubscribeDataQueue.addAll(subscribedData)
        if (mSubscribeDataQueue.isNotEmpty()) {
            val subscribeData = mSubscribeDataQueue.first()
            subscribeToCharacteristicIndication(
                subscribeData.serviceUUID, subscribeData.charUUID, subscribeData.desUuid
            )
            mSubscribeDataQueue.removeFirst()
        } else {
//            bleLifecycleStateChange(BleCentralLifecycleState.AllSubscribed)
        }
    }

    @Suppress("DEPRECATION")
    internal fun subscribeToCharacteristicIndication(
        serviceUUID: UUID, charUUID: UUID, desUuid: UUID
    ) {
        Timber.d("subscribeToCharacteristicIndication charUUID: $charUUID")
        val characteristicForIndicate = getGattServiceCharacteristic(serviceUUID, charUUID)
        characteristicForIndicate?.getDescriptor(desUuid)?.let { cccDescriptor ->
            if (mBluetoothGatt?.setCharacteristicNotification(
                    characteristicForIndicate, true
                ) == false
            ) {
                Timber.e(
                    "ERROR: setNotification(true) failed for ${characteristicForIndicate.uuid}"
                )
                return
            }
            Timber.d("writeDescriptor")

            mGattRequestDispatcher?.addWriteDescriptorRequest(
                cccDescriptor,
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            )
        } ?: run {
            Timber.w("WARN: characteristic not found charUUID: $charUUID, desUuid: $desUuid")
        }
    }

    @Suppress("DEPRECATION")
    internal fun unsubscribeFromCharacteristic(
        serviceUUID: UUID, charUUID: UUID, desUuid: UUID
    ) {
        Timber.d("unsubscribeFromCharacteristic")
        val characteristicForIndicate = getGattServiceCharacteristic(serviceUUID, charUUID)
        characteristicForIndicate?.getDescriptor(desUuid)?.let { cccDescriptor ->
            if (mBluetoothGatt?.setCharacteristicNotification(
                    characteristicForIndicate, true
                ) == false
            ) {
                Timber.e(
                    "ERROR: setNotification(true) failed for ${characteristicForIndicate.uuid}"
                )
                return
            }
            Timber.d("writeDescriptor")
            mGattRequestDispatcher?.addWriteDescriptorRequest(
                cccDescriptor,
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            )
        } ?: run {
            Timber.w("WARN: characteristic not found $desUuid")
        }
    }

    internal fun requestCharacteristicRead(
        serviceUUID: UUID, charUUID: UUID
    ): Boolean {
        Timber.d("onRequestCharacteristicRead service: $serviceUUID, char: $charUUID")
        mBluetoothGatt ?: run {
            Timber.e("ERROR: read failed, no connected device mGatt = null")
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
        mGattRequestDispatcher?.addReadCharacteristicRequest(characteristic)
        return true
    }

    @Suppress("DEPRECATION")
    internal fun requestCharacteristicWrite(
        serviceUUID: UUID, charUUID: UUID, data: ByteArray
    ) {
        Timber.d("requestCharacteristicWrite data: ${data.toString(Charsets.UTF_8)}")
        mBluetoothGatt ?: run {
            Timber.e("ERROR: write failed, no connected device")
            return
        }
        var characteristic = getGattServiceCharacteristic(serviceUUID, charUUID) ?: run {
            Timber.e("ERROR: write failed, characteristic unavailable $charUUID")
            return
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
                return
            }
        }
        mGattRequestDispatcher?.addWriteCharacteristicRequest(characteristic, data, writeType)
    }

    internal fun disconnect() {
        Timber.d("trigger disconnect gatt")
        context.unregisterReceiver(bondStateReceiver)
        mBluetoothGatt?.disconnect()
        //mBluetoothGatt?.close() should call close when gatt disconnected
        //call close here will unregister bluetooth stack callback and never get disconnected
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

    companion object {
        private val ENABLE_BATTERY_SAVE = byteArrayOf(0x01)
        private val CHANGE_RESOLUTION_TO_HD = byteArrayOf(0x02)
        private val CHANGE_RESOLUTION_TO_FHD = byteArrayOf(0x02)
    }
}