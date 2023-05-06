package com.example.ble_kit.model

enum class BleLifecycleState {
    None,
    Advertising,
    StoppedAdvertising,
    Scanning,
    Connecting,
    Connected,
    ConnectedAndDiscovered,
    ConnectedAndSubscribed,
    ConnectedAndUnsubscribed,
    Disconnected
}