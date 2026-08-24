package com.classcue.kexing.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class OfflineModelPack { MEDIUM, LARGE }

enum class ModelDownloadPhase {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    INSTALLING,
    READY,
    PAUSED,
    FAILED
}

data class ModelDownloadState(
    val pack: OfflineModelPack,
    val phase: ModelDownloadPhase,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val message: String = ""
) {
    val progress: Float
        get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toDouble() / totalBytes)
            .toFloat().coerceIn(0f, 1f)
}

private data class ModelPackageSpec(
    val pack: OfflineModelPack,
    val label: String,
    val directoryName: String,
    val archiveUrl: String,
    val expectedDownloadBytes: Long,
    val requiredFiles: List<String>
)

class ModelRepository(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "offline_models").apply { mkdirs() }
    // Keep partial archives in filesDir so Android cache cleanup does not erase resumable downloads.
    private val cache = File(appContext.filesDir, "model_packages").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("model_download_state", Context.MODE_PRIVATE)
    private val specs = mapOf(
        OfflineModelPack.MEDIUM to ModelPackageSpec(
            pack = OfflineModelPack.MEDIUM,
            label = "中档流式模型",
            directoryName = "sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30.tar.bz2",
            expectedDownloadBytes = 127L * 1024L * 1024L,
            requiredFiles = listOf("encoder.int8.onnx", "decoder.onnx", "joiner.int8.onnx", "tokens.txt")
        ),
        OfflineModelPack.LARGE to ModelPackageSpec(
            pack = OfflineModelPack.LARGE,
            label = "大档精修模型",
            directoryName = "sherpa-onnx-zipformer-ctc-zh-int8-2025-07-03",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-zipformer-ctc-zh-int8-2025-07-03.tar.bz2",
            expectedDownloadBytes = 287L * 1024L * 1024L,
            requiredFiles = listOf("model.int8.onnx", "tokens.txt")
        )
    )

    private val _states = MutableStateFlow(
        OfflineModelPack.entries.associateWith { pack -> diskState(spec(pack)) }
    )
    val states: StateFlow<Map<OfflineModelPack, ModelDownloadState>> = _states

    @Synchronized
    fun refresh() {
        val current = _states.value
        _states.value = OfflineModelPack.entries.associateWith { pack ->
            val existing = current[pack]
            if (existing?.phase in ACTIVE_PHASES) existing!! else diskState(spec(pack))
        }
    }

    @Synchronized
    fun isReady(pack: OfflineModelPack): Boolean = isValid(target(spec(pack)), spec(pack))

    @Synchronized
    fun installedDirectory(pack: OfflineModelPack): File? =
        target(spec(pack)).takeIf { isValid(it, spec(pack)) }

    fun queue(pack: OfflineModelPack): ModelDownloadState = update(pack) { current ->
        if (current.phase == ModelDownloadPhase.READY) current else current.copy(
            phase = ModelDownloadPhase.QUEUED,
            message = "等待后台下载"
        )
    }

    fun pause(pack: OfflineModelPack, message: String = "已暂停，可继续下载"): ModelDownloadState =
        update(pack) { current ->
            if (current.phase == ModelDownloadPhase.READY) current else current.copy(
                phase = ModelDownloadPhase.PAUSED,
                downloadedBytes = partial(spec(pack)).length(),
                bytesPerSecond = 0L,
                message = message
            )
        }

    fun fail(pack: OfflineModelPack, message: String): ModelDownloadState = update(pack) { current ->
        current.copy(
            phase = ModelDownloadPhase.FAILED,
            downloadedBytes = partial(spec(pack)).length(),
            bytesPerSecond = 0L,
            message = message
        )
    }

    @Synchronized
    fun delete(pack: OfflineModelPack): Boolean {
        val state = _states.value[pack]
        if (state?.phase in ACTIVE_PHASES) return false
        val spec = spec(pack)
        target(spec).deleteRecursively()
        staging(spec).deleteRecursively()
        archive(spec).delete()
        partial(spec).delete()
        prefs.edit().remove(totalKey(pack)).apply()
        update(pack) {
            ModelDownloadState(pack, ModelDownloadPhase.NOT_DOWNLOADED, message = "尚未下载")
        }
        return true
    }

    fun downloadAndInstall(
        pack: OfflineModelPack,
        shouldContinue: () -> Boolean,
        onProgress: (ModelDownloadState) -> Unit
    ): File {
        val spec = spec(pack)
        installedDirectory(pack)?.let {
            onProgress(update(pack) { current -> current.copy(phase = ModelDownloadPhase.READY, message = "已安装") })
            return it
        }
        check(root.usableSpace > spec.expectedDownloadBytes * 3L) {
            "存储空间不足，${spec.label}下载和解压需要至少 ${formatSize(spec.expectedDownloadBytes * 3L)} 可用空间"
        }
        checkContinue(shouldContinue)
        if (!archive(spec).isFile) download(spec, shouldContinue, onProgress)
        checkContinue(shouldContinue)
        onProgress(update(pack) { current ->
            current.copy(
                phase = ModelDownloadPhase.INSTALLING,
                bytesPerSecond = 0L,
                message = "下载完成，正在解压安装"
            )
        })
        try {
            install(spec, shouldContinue)
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Throwable) {
            archive(spec).delete()
            throw error
        }
        archive(spec).delete()
        prefs.edit().remove(totalKey(pack)).apply()
        val directory = target(spec)
        check(isValid(directory, spec)) { "${spec.label}安装失败" }
        onProgress(update(pack) {
            ModelDownloadState(
                pack = pack,
                phase = ModelDownloadPhase.READY,
                downloadedBytes = spec.expectedDownloadBytes,
                totalBytes = spec.expectedDownloadBytes,
                message = "已安装，可离线使用"
            )
        })
        return directory
    }

    private fun download(
        spec: ModelPackageSpec,
        shouldContinue: () -> Boolean,
        onProgress: (ModelDownloadState) -> Unit
    ) {
        val part = partial(spec)
        part.parentFile?.mkdirs()
        var received = part.length()
        val connection = openFollowingRedirects(spec.archiveUrl, received)
        try {
            val code = connection.responseCode
            val remoteTotal = parseTotal(connection.getHeaderField("Content-Range"))
            if (DownloadResumePolicy.isAlreadyComplete(received, code, remoteTotal)) {
                promoteCompletedArchive(spec)
                return
            }
            check(code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
                "模型下载失败：HTTP $code"
            }
            val append = DownloadResumePolicy.shouldAppend(received, code)
            if (!append) {
                received = 0L
                if (part.exists()) part.delete()
            }
            val contentLength = connection.contentLengthLong
            val hasKnownTotal = remoteTotal > 0L || contentLength > 0L
            val total = DownloadResumePolicy.resolvedTotal(
                existingBytes = received,
                append = append,
                contentLength = contentLength,
                rangeTotal = remoteTotal,
                expectedBytes = spec.expectedDownloadBytes
            )
            prefs.edit().putLong(totalKey(spec.pack), total).apply()
            var lastUpdateAt = System.currentTimeMillis()
            var lastUpdateBytes = received
            onProgress(update(spec.pack) {
                ModelDownloadState(
                    pack = spec.pack,
                    phase = ModelDownloadPhase.DOWNLOADING,
                    downloadedBytes = received,
                    totalBytes = total,
                    message = if (received > 0L) "正在从断点继续下载" else "正在后台下载"
                )
            })
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(part, append).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        checkContinue(shouldContinue)
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateAt >= 350L) {
                            val speed = ((received - lastUpdateBytes) * 1_000L / (now - lastUpdateAt).coerceAtLeast(1L))
                            lastUpdateAt = now
                            lastUpdateBytes = received
                            onProgress(update(spec.pack) { current ->
                                current.copy(
                                    phase = ModelDownloadPhase.DOWNLOADING,
                                    downloadedBytes = received,
                                    totalBytes = total,
                                    bytesPerSecond = speed,
                                    message = "正在后台下载"
                                )
                            })
                        }
                    }
                }
            }
            check(received > 1024L * 1024L) { "${spec.label}下载内容不完整" }
            check(!hasKnownTotal || received >= total) {
                "${spec.label}下载中断，已保留断点，请点击继续下载"
            }
            promoteCompletedArchive(spec)
        } finally {
            connection.disconnect()
        }
    }

    private fun promoteCompletedArchive(spec: ModelPackageSpec) {
        val part = partial(spec)
        val completed = archive(spec)
        if (completed.exists()) completed.delete()
        check(part.renameTo(completed)) { "无法保存已下载的模型包" }
    }

    private fun install(spec: ModelPackageSpec, shouldContinue: () -> Boolean) {
        val staging = staging(spec)
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()
        BZip2CompressorInputStream(BufferedInputStream(archive(spec).inputStream())).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                while (true) {
                    checkContinue(shouldContinue)
                    val entry = tar.nextTarEntry ?: break
                    val prefix = "${spec.directoryName}/"
                    val relative = entry.name.removePrefix("./").removePrefix(prefix)
                    if (relative.isBlank() || relative == entry.name) continue
                    val output = File(staging, relative)
                    check(output.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                        "模型压缩包路径非法"
                    }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else if (entry.isFile) {
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { tar.copyTo(it) }
                    }
                }
            }
        }
        check(isValid(staging, spec)) { "${spec.label}文件不完整" }
        val target = target(spec)
        if (target.exists()) target.deleteRecursively()
        if (!staging.renameTo(target)) {
            staging.copyRecursively(target, overwrite = true)
            staging.deleteRecursively()
        }
    }

    private fun openFollowingRedirects(rawUrl: String, offset: Long): HttpURLConnection {
        var current = URL(rawUrl)
        repeat(8) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 45_000
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "ClassCue-Android/0.1.3")
                if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
            }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK,
                HttpURLConnection.HTTP_PARTIAL,
                HTTP_RANGE_NOT_SATISFIABLE -> return connection
                in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("模型下载缺少重定向地址")
                    current = URL(current, location)
                    connection.disconnect()
                }
                else -> {
                    val code = connection.responseCode
                    connection.disconnect()
                    throw IllegalStateException("模型下载失败：HTTP $code")
                }
            }
        }
        throw IllegalStateException("模型下载重定向过多")
    }

    private fun diskState(spec: ModelPackageSpec): ModelDownloadState {
        if (isValid(target(spec), spec)) {
            return ModelDownloadState(
                spec.pack,
                ModelDownloadPhase.READY,
                spec.expectedDownloadBytes,
                spec.expectedDownloadBytes,
                message = "已安装，可离线使用"
            )
        }
        val downloaded = partial(spec).length()
        val total = prefs.getLong(totalKey(spec.pack), spec.expectedDownloadBytes)
        return if (downloaded > 0L || archive(spec).isFile) {
            ModelDownloadState(
                spec.pack,
                ModelDownloadPhase.PAUSED,
                downloaded,
                total,
                message = if (archive(spec).isFile) "下载完成，等待继续安装" else "下载已暂停，可从断点继续"
            )
        } else {
            ModelDownloadState(spec.pack, ModelDownloadPhase.NOT_DOWNLOADED, totalBytes = total, message = "尚未下载")
        }
    }

    @Synchronized
    private fun update(
        pack: OfflineModelPack,
        transform: (ModelDownloadState) -> ModelDownloadState
    ): ModelDownloadState {
        val current = _states.value[pack] ?: diskState(spec(pack))
        val updated = transform(current)
        _states.value = _states.value.toMutableMap().apply { put(pack, updated) }
        return updated
    }

    private fun spec(pack: OfflineModelPack): ModelPackageSpec = requireNotNull(specs[pack])
    private fun target(spec: ModelPackageSpec) = File(root, spec.directoryName)
    private fun staging(spec: ModelPackageSpec) = File(root, "${spec.directoryName}.preparing")
    private fun archive(spec: ModelPackageSpec) = File(cache, "${spec.directoryName}.tar.bz2")
    private fun partial(spec: ModelPackageSpec) = File(cache, "${spec.directoryName}.tar.bz2.part")
    private fun totalKey(pack: OfflineModelPack) = "${pack.name.lowercase()}_total"

    private fun isValid(directory: File, spec: ModelPackageSpec): Boolean =
        spec.requiredFiles.all { File(directory, it).isFile && File(directory, it).length() > 0L }

    private fun parseTotal(contentRange: String?): Long =
        contentRange?.substringAfterLast('/')?.toLongOrNull() ?: -1L

    private fun checkContinue(shouldContinue: () -> Boolean) {
        if (!shouldContinue() || Thread.currentThread().isInterrupted) throw InterruptedException()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
        else -> "%.0f MB".format(bytes / 1024.0 / 1024.0)
    }

    private companion object {
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        val ACTIVE_PHASES = setOf(
            ModelDownloadPhase.QUEUED,
            ModelDownloadPhase.DOWNLOADING,
            ModelDownloadPhase.INSTALLING
        )
    }
}
