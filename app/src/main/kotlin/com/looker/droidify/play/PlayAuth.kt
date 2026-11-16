package com.looker.droidify.play

import android.content.Context
import android.os.Build
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

/**
 * Anonymous Google Play login (no Google account needed).
 *
 * A "dispenser" server hands out a ready-made anonymous account (email + AAS token).
 * We cache that pair and build the gplayapi [AuthData] from it.
 * If one dispenser is down, the next one in [DISPENSERS] is tried.
 */
class PlayAuth(
    private val context: Context,
    private val http: OkHttpClient,
) {
    private val prefs = context.getSharedPreferences("play_auth", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var cached: AuthData? = null

    /** Returns a usable [AuthData]. Call with [refresh] = true after the server rejects the old one. */
    suspend fun get(refresh: Boolean = false): AuthData = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!refresh) cached?.let { return@withContext it }

            if (!refresh) {
                val email = prefs.getString(KEY_EMAIL, null)
                val token = prefs.getString(KEY_TOKEN, null)
                if (email != null && token != null) {
                    runCatching { buildAuth(email, token) }
                        .onSuccess { cached = it; return@withContext it }
                    // stored account is dead -> fall through and ask for a new one
                }
            }

            var lastError: Throwable? = null
            for (url in DISPENSERS) {
                try {
                    val (email, token) = requestAccount(url)
                    val data = buildAuth(email, token)
                    prefs.edit().putString(KEY_EMAIL, email).putString(KEY_TOKEN, token).apply()
                    cached = data
                    return@withContext data
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            throw IllegalStateException(
                "Anonymous login failed on all dispensers: ${lastError?.message}",
                lastError,
            )
        }
    }

    private fun buildAuth(email: String, token: String): AuthData =
        AuthHelper.build(
            email = email,
            token = token,
            tokenType = AuthHelper.Token.AAS,
            isAnonymous = true,
            properties = deviceProfile(),
            locale = Locale.getDefault(),
        )

    /** Same device description, in the java.util.Properties form gplayapi expects. */
    private fun deviceProfile(): Properties = Properties().apply {
        val json = deviceProperties()
        json.keys().forEach { key -> setProperty(key, json.getString(key)) }
        val metrics = context.resources.displayMetrics
        setProperty("Screen.Density", metrics.densityDpi.toString())
        setProperty("Screen.Width", metrics.widthPixels.toString())
        setProperty("Screen.Height", metrics.heightPixels.toString())
        setProperty("Screen.Layout", "2")
        setProperty("Client", "android-google")
        setProperty("CellOperator", "310")
        setProperty("SimOperator", "38")
        setProperty("Keyboard", "1")
        setProperty("Navigation", "1")
        setProperty("TouchScreen", "3")
        setProperty("HasFiveWayNavigation", "false")
        setProperty("HasHardKeyboard", "false")
        setProperty("GL.Version", "196610")
        setProperty("GSF.version", "203615037")
        setProperty("Vending.version", "82151710")
        setProperty("Vending.versionString", "21.5.17-21 [0] [PR] 326734551")
        setProperty(
            "SharedLibraries",
            "android.test.runner,com.android.future.usb.accessory,com.android.location.provider," +
                "com.android.media.remotedisplay,com.android.mediadrm.signer,javax.obex," +
                "org.apache.http.legacy,android.ext.services,android.ext.shared",
        )
        setProperty(
            "Features",
            "android.hardware.touchscreen,android.hardware.touchscreen.multitouch," +
                "android.hardware.wifi,android.hardware.location,android.hardware.location.network," +
                "android.hardware.camera,android.hardware.camera.autofocus,android.hardware.microphone," +
                "android.hardware.bluetooth,android.hardware.sensor.accelerometer," +
                "android.hardware.screen.portrait,android.hardware.screen.landscape," +
                "android.software.webview,android.software.live_wallpaper",
        )
    }

    private fun requestAccount(url: String): Pair<String, String> {
        val body = deviceProperties().toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Aurora Store")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} from $url" }
            val json = JSONObject(response.body.string())
            val email = json.optString("email")
            val token = json.optString("auth").ifEmpty { json.optString("aasToken") }
            check(email.isNotEmpty() && token.isNotEmpty()) { "Dispenser reply has no account" }
            return email to token
        }
    }

    /** Describes this phone to the dispenser so the account gets a matching device profile. */
    private fun deviceProperties() = JSONObject().apply {
        put("Build.HARDWARE", Build.HARDWARE)
        put("Build.RADIO", Build.getRadioVersion() ?: "unknown")
        put("Build.BOOTLOADER", Build.BOOTLOADER)
        put("Build.FINGERPRINT", Build.FINGERPRINT)
        put("Build.BRAND", Build.BRAND)
        put("Build.DEVICE", Build.DEVICE)
        put("Build.VERSION.SDK_INT", Build.VERSION.SDK_INT.toString())
        put("Build.VERSION.RELEASE", Build.VERSION.RELEASE)
        put("Build.MODEL", Build.MODEL)
        put("Build.MANUFACTURER", Build.MANUFACTURER)
        put("Build.PRODUCT", Build.PRODUCT)
        put("Build.ID", Build.ID)
        put("Platforms", Build.SUPPORTED_ABIS.joinToString(","))
        put("Locales", Locale.getDefault().toString())
        put("Roaming", "mobile-notroaming")
        put("TimeZone", java.util.TimeZone.getDefault().id)
    }

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_TOKEN = "token"

        // Public Aurora dispenser. Add more URLs here as fallbacks.
        val DISPENSERS = listOf(
            "https://auroraoss.com/api/auth",
        )
    }
}
