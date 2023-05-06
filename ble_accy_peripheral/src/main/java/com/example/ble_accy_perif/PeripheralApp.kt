package com.example.ble_accy_perif

import android.app.Application
import timber.log.Timber

class PeripheralApp: Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(AppTree())
    }
}

class AppTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String {
        val className = super.createStackElementTag(element)?.split("$")?.get(0)
        return "Jdt $className:${element.lineNumber}#${element.methodName}"
    }
}