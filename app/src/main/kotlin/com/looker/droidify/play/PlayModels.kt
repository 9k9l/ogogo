package com.looker.droidify.play

/** What the UI needs to know about one Google Play app. */
data class PlayApp(
    val packageName: String,
    val name: String,
    val developer: String,
    val iconUrl: String?,
    val versionName: String,
    val versionCode: Long,
    val offerType: Int,
    val sizeBytes: Long,
    val summary: String,
    val description: String,
    val changes: String,
)

/** One downloadable APK part (base APK or a split) with a signed, short-lived URL. */
data class PlayFile(
    val name: String,
    val url: String,
    val sizeBytes: Long,
)
