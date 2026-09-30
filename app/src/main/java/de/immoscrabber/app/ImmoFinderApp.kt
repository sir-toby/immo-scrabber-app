package de.immoscrabber.app

import android.app.Application
import de.immoscrabber.app.core.AppContainer

class ImmoFinderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
