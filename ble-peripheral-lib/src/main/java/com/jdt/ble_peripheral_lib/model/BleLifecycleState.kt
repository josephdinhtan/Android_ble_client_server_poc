package com.jdt.ble_peripheral_lib.model

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