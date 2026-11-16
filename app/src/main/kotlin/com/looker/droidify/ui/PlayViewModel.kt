package com.looker.droidify.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.looker.droidify.Droidify
import com.looker.droidify.play.PlayApp
import kotlinx.coroutines.launch

class PlayViewModel(application: Application) : AndroidViewModel(application) {

    private val droidify get() = getApplication<Droidify>()

    var query by mutableStateOf("")
    var results by mutableStateOf<List<PlayApp>>(emptyList())
    var searching by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var selected by mutableStateOf<PlayApp?>(null)
        private set

    /** null = idle, below 0 = getting links, 0..1 = downloading, 1 = handing over to the system installer. */
    var installProgress by mutableStateOf<Float?>(null)
        private set
    var installMessage by mutableStateOf<String?>(null)
        private set

    fun search() {
        val text = query.trim()
        if (text.isEmpty() || searching) return
        searching = true
        error = null
        viewModelScope.launch {
            try {
                results = droidify.play.search(text)
                if (results.isEmpty()) error = "No results"
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
            } finally {
                searching = false
            }
        }
    }

    fun open(app: PlayApp) {
        selected = app
        installMessage = null
        // Search results are short; load the full page (description, changelog) in the background.
        viewModelScope.launch {
            try {
                val full = droidify.play.details(app.packageName)
                if (selected?.packageName == app.packageName) selected = full
            } catch (_: Throwable) {
                // keep the short version
            }
        }
    }

    fun close() {
        if (installProgress == null) selected = null
    }

    fun install() {
        val app = selected ?: return
        if (installProgress != null) return
        installProgress = -1f
        installMessage = null
        viewModelScope.launch {
            try {
                val files = droidify.play.files(app)
                installProgress = 0f
                val failure = droidify.installer.install(app.packageName, files) {
                    installProgress = it
                }
                installMessage = failure ?: "Installed"
            } catch (t: Throwable) {
                installMessage = t.message ?: t.javaClass.simpleName
            } finally {
                installProgress = null
            }
        }
    }
}
