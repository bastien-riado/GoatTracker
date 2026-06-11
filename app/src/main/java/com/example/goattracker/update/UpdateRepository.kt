package com.example.goattracker.update

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Outcome of a silent update check. Failures are returned, never thrown, so the caller can stay quiet. */
sealed interface UpdateCheckResult {
    data class UpToDate(val info: ReleaseInfo) : UpdateCheckResult
    data class Available(val info: ReleaseInfo) : UpdateCheckResult
    data class Failed(val cause: Throwable) : UpdateCheckResult
}

/**
 * Talks to the remote release metadata and downloads the APK. Deliberately free of `android.*` so the
 * comparison + checksum logic is unit-testable on the JVM (inject a [client] backed by MockWebServer
 * and a temp [cacheDir]).
 *
 * All work runs on [dispatcher] (IO). The check swallows every error into [UpdateCheckResult.Failed];
 * the download is cancellation-cooperative (honours coroutine cancellation between read chunks).
 */
class UpdateRepository(
    private val currentVersionCode: Long,
    private val cacheDir: File,
    private val metadataUrl: String = DEFAULT_METADATA_URL,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(dispatcher) {
        runCatching {
            val request = Request.Builder().url(metadataUrl).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = response.body?.string() ?: error("empty body")
                json.decodeFromString<ReleaseInfo>(body)
            }
        }.fold(
            onSuccess = { info ->
                if (info.versionCode > currentVersionCode) UpdateCheckResult.Available(info)
                else UpdateCheckResult.UpToDate(info)
            },
            onFailure = { UpdateCheckResult.Failed(it) },
        )
    }

    /**
     * Streams the APK into `cacheDir/updates/`, reporting [0f,1f] progress when the server sends a
     * content length. Verifies [ReleaseInfo.sha256] (when present) before returning; a mismatch deletes
     * the file and throws. Throws on any IO/HTTP error — the caller treats failure as "abort silently".
     */
    suspend fun downloadApk(info: ReleaseInfo, onProgress: (Float) -> Unit = {}): File =
        withContext(dispatcher) {
            val dir = File(cacheDir, "updates").apply { mkdirs() }
            // Drop any stale APK from a previous attempt so the cache can't grow unbounded.
            dir.listFiles()?.forEach { it.delete() }
            val outFile = File(dir, "goattracker-${info.versionCode}.apk")

            val request = Request.Builder().url(info.apkUrl).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = response.body ?: error("empty body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            coroutineContext.ensureActive() // cooperative cancellation
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }

            val expected = info.sha256?.lowercase()?.takeIf { it.isNotBlank() }
            if (expected != null) {
                val actual = outFile.sha256Hex()
                if (actual != expected) {
                    outFile.delete()
                    error("Checksum mismatch: expected=$expected actual=$actual")
                }
            }
            outFile
        }

    companion object {
        const val DEFAULT_METADATA_URL =
            "https://github.com/bastien-riado/GoatTracker/releases/latest/download/version.json"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // OkHttp follows http->https and cross-host redirects by default, which the GitHub
            // /latest/download/ -> objects.githubusercontent.com hop requires.
            .build()

        internal fun File.sha256Hex(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            inputStream().use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
