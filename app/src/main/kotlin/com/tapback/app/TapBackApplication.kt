package com.tapback.app

import android.app.Application
import com.tapback.app.di.TapBackContainer

class TapBackApplication : Application() {
    lateinit var container: TapBackContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = TapBackContainer(this)
    }
}
