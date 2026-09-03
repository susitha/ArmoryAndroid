package com.cenango.fetchcicg

import android.app.Application
import com.cenango.fetchcicg.data.network.ArmoryApiService
import com.cenango.fetchcicg.data.network.RetrofitClient
import com.cenango.fetchcicg.data.session.SessionManager
import com.cenango.fetchcicg.data.storage.Storage
import com.cenango.fetchcicg.rfid.ChainwayRfidManager
import com.cenango.fetchcicg.rfid.RfidManager

class FetchCICGApplication : Application() {

    lateinit var sessionManager: SessionManager
        private set

    val api: ArmoryApiService by lazy { RetrofitClient(sessionManager).api }

    val rfidManager: RfidManager by lazy { ChainwayRfidManager(applicationContext) }

    val storage: Storage by lazy { Storage(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(applicationContext)
    }
}
