package com.aibuilt.arin

import android.app.Application
import com.aibuilt.arin.data.AppContainer

class ArinApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.boot()
    }
}
