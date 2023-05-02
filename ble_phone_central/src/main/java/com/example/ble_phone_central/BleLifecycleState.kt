package com.example.ble_phone_central

enum class BleLifecycleState {
    Disconnected,
    Scanning,
    Connecting,
    ConnectedDiscovering,
    ConnectedSubscribing,
    Connected
}