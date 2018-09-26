package com.beacon.check.ibeaconscan

import android.app.Application

class App : Application() {
    companion object {
        lateinit var app: Application
            private set
    }

    override fun onCreate() {
        super.onCreate()
        app = this
    }
}