package com.example.goattracker.update

import kotlinx.serialization.Serializable

/**
 * Remote update metadata published by the CI pipeline as `version.json` on the GitHub Release,
 * fetched from a fixed URL (`/releases/latest/download/version.json`).
 *
 * [versionCode] is the comparison key (monotonic). [versionName] is display-only. [sha256] (lower-case
 * hex of the APK) is optional but, when present, is verified after download before the installer runs.
 */
@Serializable
data class ReleaseInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String? = null,
    val notes: String? = null,
)
