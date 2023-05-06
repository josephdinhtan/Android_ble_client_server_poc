package com.example.ble_phone_central

import android.app.Application
import timber.log.Timber

class CentralApp: Application() {
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