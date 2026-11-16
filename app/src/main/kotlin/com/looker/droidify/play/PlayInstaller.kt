package com.looker.droidify.play

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Downloads every APK part of an app and installs them together in one session. */
class PlayInstaller(
    private val context: Context,
    private val http: OkHttpClient,
) {

    /** [onProgress] gets 0f..1f while downloading. Returns null on success, else an error text. */
    suspend fun install(
        packageName: String,
        files: List<PlayFile>,
        onProgress: (Float) -> Unit,
    ): String? {
        val apks = files.filter { it.name.endsWith(".apk") }
        if (apks.isEmpty()) return "No APK files were returned by Google Play"

        val dir = File(context.cacheDir, "apks/$packageName").apply {
            deleteRecursively()
            mkdirs()
        }
        return try {
            val downloaded = download(apks, dir, onProgress)
            commit(packageName, downloaded)
        } catch (t: Throwable) {
            t.message ?: t.javaClass.simpleName
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun download(
        apks: List<PlayFile>,
        dir: File,
        onProgress: (Float) -> Unit,
    ): List<File> = withContext(Dispatchers.IO) {
        val total = apks.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var done = 0L
        apks.map { part ->
            val target = File(dir, part.name)
            http.newCall(Request.Builder().url(part.url).build()).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress((done.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
            }
            target
        }
    }

    private suspend fun commit(packageName: String, apks: List<File>): String? {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)
        val sessionId = installer.createSession(params)

        withContext(Dispatchers.IO) {
            installer.openSession(sessionId).use { session ->
                apks.forEach { apk ->
                    apk.inputStream().use { input ->
                        session.openWrite(apk.name, 0, apk.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setPackage(context.packageName)
                val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        }

        // Wait until the system reports the final result for OUR session.
        val result = InstallResultReceiver.results.first { it.sessionId == sessionId }
        return if (result.status == PackageInstaller.STATUS_SUCCESS) null
        else result.message ?: "Install failed (code ${result.status})"
    }
}

/** Receives install progress from the system installer. */
class InstallResultReceiver : BroadcastReceiver() {

    data class Result(val sessionId: Int, val status: Int, val message: String?)

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // The system wants the user to confirm: show its dialog.
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            confirm?.let(context::startActivity)
            return
        }
        results.tryEmit(
            Result(sessionId, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)),
        )
    }

    companion object {
        // replay = 1 so a result that arrives just before we start listening is not lost.
        val results = MutableSharedFlow<Result>(replay = 1, extraBufferCapacity = 8)
    }
}
