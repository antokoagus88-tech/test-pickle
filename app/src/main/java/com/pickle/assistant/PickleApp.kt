package com.pickle.assistant

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import com.pickle.assistant.config.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class PickleApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val dataStore by lazy { preferencesDataStore(name = "pickle_prefs") }

    val preferencesRepository by lazy { PreferencesRepository(dataStore) }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
        }
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun getContext(): Context = PickleApp.instance!!
        private var instance: PickleApp? = null
            @Suppress("UNUSED_PROPERTY")
            set(value) { field = value }

        init {
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        instance = this
    }
}