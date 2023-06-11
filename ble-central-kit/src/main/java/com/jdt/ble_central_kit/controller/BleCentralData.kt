package com.jdt.ble_central_kit.controller

import java.util.UUID

sealed class BleCentralData {

    data class ResponseReadRequestData(
        val serviceUUID: UUID, val charUUID: UUID, val data: ByteArray
    ) : BleCentralData()

    data class IndicationData(
        val serviceUUID: UUID, val charUUID: UUID, val data: ByteArray
    ) : BleCentralData()


    internal data class RequestSubscribeData(
        val serviceUUID: UUID, val charUUID: UUID, val desUuid: UUID
    ) : BleCentralData()
}