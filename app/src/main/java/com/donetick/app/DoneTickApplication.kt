package com.donetick.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for DoneTick app with Hilt dependency injection
 */
@HiltAndroidApp
class DoneTickApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
}
