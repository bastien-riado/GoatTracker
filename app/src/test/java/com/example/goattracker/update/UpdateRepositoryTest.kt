package com.example.goattracker.update

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class UpdateRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        cacheDir = Files.createTempDirectory("update-test").toFile()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        cacheDir.deleteRecursively()
    }

    private fun repo(currentVersionCode: Long) = UpdateRepository(
        currentVersionCode = currentVersionCode,
        cacheDir = cacheDir,
        metadataUrl = server.url("/version.json").toString(),
    )

    private fun metadata(versionCode: Long) =
        """{"versionCode":$versionCode,"versionName":"1.0.$versionCode","apkUrl":"http://x/app.apk"}"""

    @Test
    fun `available when remote versionCode is higher`() = runTest {
        server.enqueue(MockResponse().setBody(metadata(5)))
        val result = repo(currentVersionCode = 3).checkForUpdate()
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(5L, (result as UpdateCheckResult.Available).info.versionCode)
    }

    @Test
    fun `up to date when versionCode equal`() = runTest {
        server.enqueue(MockResponse().setBody(metadata(3)))
        assertTrue(repo(3).checkForUpdate() is UpdateCheckResult.UpToDate)
    }

    @Test
    fun `up to date when remote is older`() = runTest {
        server.enqueue(MockResponse().setBody(metadata(2)))
        assertTrue(repo(3).checkForUpdate() is UpdateCheckResult.UpToDate)
    }

    @Test
    fun `failed on malformed json`() = runTest {
        server.enqueue(MockResponse().setBody("definitely not json"))
        assertTrue(repo(3).checkForUpdate() is UpdateCheckResult.Failed)
    }

    @Test
    fun `failed on http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(repo(3).checkForUpdate() is UpdateCheckResult.Failed)
    }

    @Test
    fun `failed when offline`() = runTest {
        server.shutdown() // nothing is listening -> connection refused
        assertTrue(repo(3).checkForUpdate() is UpdateCheckResult.Failed)
    }

    @Test
    fun `download writes file and passes checksum`() = runTest {
        val payload = "FAKE_APK_BYTES"
        server.enqueue(MockResponse().setBody(payload))
        val info = ReleaseInfo(
            versionCode = 5,
            versionName = "1.0.5",
            apkUrl = server.url("/app.apk").toString(),
            sha256 = sha256Hex(payload.toByteArray()),
        )
        val file = repo(3).downloadApk(info)
        assertTrue(file.exists())
        assertEquals(payload.toByteArray().size.toLong(), file.length())
    }

    @Test(expected = IllegalStateException::class)
    fun `download throws on checksum mismatch`() = runTest {
        server.enqueue(MockResponse().setBody("some bytes"))
        val info = ReleaseInfo(
            versionCode = 5,
            versionName = "1.0.5",
            apkUrl = server.url("/app.apk").toString(),
            sha256 = "deadbeef",
        )
        repo(3).downloadApk(info)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
