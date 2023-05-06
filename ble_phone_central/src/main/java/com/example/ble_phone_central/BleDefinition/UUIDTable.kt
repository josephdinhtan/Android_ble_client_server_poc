package com.example.ble_phone_central.BleDefinition

import java.util.UUID

object UUIDTable {

    internal val GATT_SERVICE_UUID = UUID.fromString("25AE1441-05D3-4C5B-8281-93D4E07420CF")
    internal val GATT_CHAR_FOR_READ_UUID = UUID.fromString("25AE1442-05D3-4C5B-8281-93D4E07420CF")
    internal val GATT_CHAR_FOR_WRITE_UUID = UUID.fromString("25AE1443-05D3-4C5B-8281-93D4E07420CF")
    internal val GATT_CHAR_FOR_INDICATE_UUID = UUID.fromString("25AE1444-05D3-4C5B-8281-93D4E07420CF")

    internal val GATT_CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
