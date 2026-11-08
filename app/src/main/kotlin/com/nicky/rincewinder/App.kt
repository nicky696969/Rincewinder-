package com.nicky.rincewinder

import android.app.Application
import com.nicky.rincewinder.body.BodyFinder
import com.nicky.rincewinder.data.JsonFileStore
import com.nicky.rincewinder.data.Prefs
import com.nicky.rincewinder.sync.Notif
import java.io.File

/** Ruční DI (ZADANI_F1 §5.1: Hilt není nutný). Jediná instance každé služby na proces. */
class App : Application() {
    lateinit var prefs: Prefs
    lateinit var store: JsonFileStore
    lateinit var finder: BodyFinder

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        store = JsonFileStore(File(filesDir, "files.json"))
        finder = BodyFinder(this, prefs)
        Notif.ensureChannels(this)
    }

    companion object {
        fun of(ctx: android.content.Context) = ctx.applicationContext as App
    }
}
