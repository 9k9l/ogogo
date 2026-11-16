package com.looker.droidify.play

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The ONLY file that talks to the gplayapi library.
 * If a library update changes a name or signature, this is the one place to fix.
 */
class PlayClient(private val auth: PlayAuth) {

    suspend fun search(query: String): List<PlayApp> = call { data ->
        SearchHelper(data)
            .searchResults(query, "")
            .streamClusters
            .values
            .flatMap { it.clusterAppList }
            .map { it.toPlayApp() }
            .distinctBy { it.packageName }
    }

    suspend fun details(packageName: String): PlayApp = call { data ->
        AppDetailsHelper(data).getAppByPackageName(packageName).toPlayApp()
    }

    /** Fresh download links. They expire quickly, so ask right before downloading. */
    suspend fun files(app: PlayApp): List<PlayFile> = call { data ->
        PurchaseHelper(data)
            .purchase(app.packageName, app.versionCode, app.offerType)
            .map { PlayFile(name = it.name, url = it.url, sizeBytes = it.size.toLong()) }
    }

    /** Runs [block] with the cached login; on any failure logs in again once and retries. */
    private suspend fun <T> call(block: (com.aurora.gplayapi.data.models.AuthData) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block(auth.get())
            } catch (first: Exception) {
                block(auth.get(refresh = true))
            }
        }

    private fun App.toPlayApp() = PlayApp(
        packageName = packageName,
        name = displayName,
        developer = developerName,
        iconUrl = iconArtwork.url.ifEmpty { null },
        versionName = versionName,
        versionCode = versionCode.toLong(),
        offerType = offerType,
        sizeBytes = size,
        summary = shortDescription,
        description = description,
        changes = changes,
    )
}
