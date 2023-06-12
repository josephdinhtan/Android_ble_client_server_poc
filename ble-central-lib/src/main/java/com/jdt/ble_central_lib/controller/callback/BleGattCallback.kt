package com.jdt.ble_central_lib.controller.callback

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService

interface BleGattCallback {
    fun onGattConnecting()
    fun onGattConnectFail()
    fun onGattConnected()
    fun onGattDisconnected()
    fun onServicesDiscovered(gattServices: List<BluetoothGattService>)
    fun onCharacteristicReadResult(
        characteristic: BluetoothGattCharacteristic, receiveData: ByteArray
    )
    fun onCharacteristicWriteResult(success: Boolean)
    fun onCharacteristicIndication(
        characteristic: BluetoothGattCharacteristic, receiveData: ByteArray
    )
    fun onDescriptorReadResult(
        descriptor: BluetoothGattDescriptor, result: BleGattDescriptorValue, success: Boolean
    )
    fun onDescriptorWriteResult(success: Boolean)
}