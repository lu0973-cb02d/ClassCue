package com.classcue.kexing.engine

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.security.MessageDigest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real download/verify/install code against a local HTTP server that supports
 * range requests, including fallback between sources and resuming a partial file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ModelRepositoryDownloadTest {
    private lateinit var server: MockWebServer
    private val routes = mutableMapOf<String, ByteArray>()
    private val failing = mutableSetOf<String>()
    private val requests = mutableListOf<String>()

    private val directory = "test-model"
    private val encoder = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val tokens = "<blk> 0\n张 1\n伟 2\n".toByteArray()
    private lateinit var archive: ByteArray

    @Before
    fun setUp() {
        archive = tarBz2(mapOf("encoder.int8.onnx" to encoder, "tokens.txt" to tokens, "README.md" to "x".toByteArray()))
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                val range = request.getHeader("Range")
                synchronized(requests) { requests += "$path ${range.orEmpty()}".trim() }
                val body = routes[path] ?: return MockResponse().setResponseCode(404)
                if (path in failing) return MockResponse().setResponseCode(503)
                val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L
                if (start >= body.size) {
                    return MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${body.size}")
                }
                val slice = Buffer().write(body.copyOfRange(start.toInt(), body.size))
                return if (range != null) {
                    MockResponse().setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-${body.size - 1}/${body.size}")
                        .setBody(slice)
                } else {
                    MockResponse().setResponseCode(200).setBody(slice)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun base() = server.url("/").toString()

    private fun spec(
        archiveBytes: ByteArray = archive,
        encoderPin: ByteArray = encoder
    ) = ModelPackageSpec(
        pack = OfflineModelPack.MEDIUM,
        label = "测试模型",
        directoryName = directory,
        archiveUrl = base() + "release/$directory.tar.bz2",
        archive = ArchiveChecksum(archiveBytes.size.toLong(), sha256(archiveBytes)),
        files = listOf(
            ModelFile("encoder.int8.onnx", ArchiveChecksum(encoderPin.size.toLong(), sha256(encoderPin))),
            ModelFile("tokens.txt", ArchiveChecksum(tokens.size.toLong(), sha256(tokens)))
        ),
        mirrorRepo = "mirror/$directory",
        mirrorBase = base()
    )

    private fun serveMirror() {
        routes["/mirror/$directory/resolve/main/encoder.int8.onnx"] = encoder
        routes["/mirror/$directory/resolve/main/tokens.txt"] = tokens
    }

    private fun repository(spec: ModelPackageSpec) =
        ModelRepository(ApplicationProvider.getApplicationContext(), { spec })

    private fun installed(repository: ModelRepository): File = requireNotNull(repository.installedDirectory(OfflineModelPack.MEDIUM))

    @Test
    fun installsFromTheOfficialArchive() {
        routes["/release/$directory.tar.bz2"] = archive
        val repository = repository(spec())

        repository.downloadAndInstall(OfflineModelPack.MEDIUM, { true }, {}, ModelDownloadSource.GITHUB)

        assertArrayEquals(encoder, File(installed(repository), "encoder.int8.onnx").readBytes())
        assertEquals(ModelDownloadPhase.READY, repository.states.value.getValue(OfflineModelPack.MEDIUM).phase)
    }

    @Test
    fun fallsBackToTheMirrorWhenGithubFails() {
        routes["/release/$directory.tar.bz2"] = archive
        failing += "/release/$directory.tar.bz2"
        serveMirror()
        val repository = repository(spec())
        val messages = mutableListOf<String>()

        repository.downloadAndInstall(OfflineModelPack.MEDIUM, { true }, { messages += it.message }, ModelDownloadSource.GITHUB)

        assertArrayEquals(tokens, File(installed(repository), "tokens.txt").readBytes())
        assertTrue(messages.any { it.contains("改用国内镜像") })
        // The mirror only needs the runtime files; nothing is fetched as an archive.
        assertTrue(requests.none { it.startsWith("/mirror") && it.contains("README") })
    }

    @Test
    fun mirrorFileWithWrongContentIsRejectedAndDeleted() {
        serveMirror()
        val tampered = encoder.copyOf().also { it[10] = (it[10] + 1).toByte() }
        routes["/mirror/$directory/resolve/main/encoder.int8.onnx"] = tampered
        val repository = repository(spec())

        val error = assertThrows(IllegalStateException::class.java) {
            repository.downloadAndInstall(OfflineModelPack.MEDIUM, { true }, {}, ModelDownloadSource.MIRROR)
        }

        // Both sources are reported; the mirror's integrity failure comes first.
        assertTrue(error.message.orEmpty(), error.message.orEmpty().startsWith("国内镜像：测试模型 encoder.int8.onnx 校验失败"))
        assertFalse(repository.isReady(OfflineModelPack.MEDIUM))
        val staging = File(ApplicationProvider.getApplicationContext<Application>().filesDir, "offline_models/$directory.mirror")
        assertFalse(File(staging, "encoder.int8.onnx").exists())
        assertFalse(File(staging, "encoder.int8.onnx.part").exists())
    }

    @Test
    fun resumesAPartialMirrorFileWithARangeRequest() {
        serveMirror()
        val staging = File(ApplicationProvider.getApplicationContext<Application>().filesDir, "offline_models/$directory.mirror")
        staging.mkdirs()
        File(staging, "encoder.int8.onnx.part").writeBytes(encoder.copyOfRange(0, 120_000))
        val repository = repository(spec())

        repository.downloadAndInstall(OfflineModelPack.MEDIUM, { true }, {}, ModelDownloadSource.MIRROR)

        assertTrue(requests.contains("/mirror/$directory/resolve/main/encoder.int8.onnx bytes=120000-"))
        assertArrayEquals(encoder, File(installed(repository), "encoder.int8.onnx").readBytes())
    }

    @Test
    fun serverAnnouncingADifferentSizeStopsBeforeDownloading() {
        routes["/release/$directory.tar.bz2"] = archive + ByteArray(10)
        val repository = repository(spec())

        val error = assertThrows(IllegalStateException::class.java) {
            repository.downloadAndInstall(OfflineModelPack.MEDIUM, { true }, {}, ModelDownloadSource.GITHUB)
        }
        // Both sources fail here (the mirror is not served); the archive error is reported first.
        assertNotNull(error.message)
        assertFalse(repository.isReady(OfflineModelPack.MEDIUM))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun tarBz2(entries: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        BZip2CompressorOutputStream(bytes).use { bzip ->
            TarArchiveOutputStream(bzip).use { tar ->
                entries.forEach { (name, data) ->
                    val entry = TarArchiveEntry("$directory/$name").apply { size = data.size.toLong() }
                    tar.putArchiveEntry(entry)
                    tar.write(data)
                    tar.closeArchiveEntry()
                }
            }
        }
        return bytes.toByteArray()
    }
}
