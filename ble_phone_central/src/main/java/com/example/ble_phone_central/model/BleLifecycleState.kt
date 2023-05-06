package com.example.ble_phone_central.model

enum class BleLifecycleState {
    none,
    Scanning,
    Connecting,
    Connected,
    ConnectedAndDiscovered,
    ConnectedAndSubscribed,
    Disconnected
}