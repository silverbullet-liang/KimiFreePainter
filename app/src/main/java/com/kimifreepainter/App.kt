package com.kimifreepainter

import android.app.Application
import com.kimifreepainter.data.api.ApiClient
import com.kimifreepainter.data.auth.AuthManager
import com.kimifreepainter.data.repo.PaintEngine

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        AuthManager.init(this)
        ApiClient.tokens = AuthManager
        engine = PaintEngine(this).also { it.start() }
    }

    companion object {
        lateinit var engine: PaintEngine
            private set
    }
}
