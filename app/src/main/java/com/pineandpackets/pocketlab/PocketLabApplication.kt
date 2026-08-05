package com.pineandpackets.pocketlab

import android.app.Application
import timber.log.Timber

class PocketLabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.i("PocketLab application initialized")
    }
}
