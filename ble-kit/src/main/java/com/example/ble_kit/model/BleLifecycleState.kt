package com.example.ble_kit.model

enum class BleLifecycleState {
    None,
    Advertising,
    Scanning,
    Connecting,
    Connected,
    ConnectedAndDiscovered,
    ConnectedAndSubscribed,
    Disconnected
}