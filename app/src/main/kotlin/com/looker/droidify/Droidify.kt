package com.looker.droidify

import android.app.Application
import com.looker.droidify.play.PlayAuth
import com.looker.droidify.play.PlayClient
import com.looker.droidify.play.PlayInstaller
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class Droidify : Application() {

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val play: PlayClient by lazy { PlayClient(PlayAuth(this, http)) }

    val installer: PlayInstaller by lazy { PlayInstaller(this, http) }
}
