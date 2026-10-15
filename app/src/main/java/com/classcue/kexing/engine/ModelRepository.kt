package com.classcue.kexing.engine

import android.content.Context
import com.classcue.kexing.BuildConfig
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

class ModelRepository(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "offline_models").apply { mkdirs() }
    // Keep partial archives in filesDir so Android cache cleanup does not erase resumable downloads.
    private val cache = File(appContext.filesDir, "model_packages").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("model_download_state", Context.MODE_PRIVATE)

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
        val requiredSpace = spec.archiveBytes * 3L
        check(root.usableSpace > requiredSpace) {
            "存储空间不足，${spec.label}下载和解压需要至少 ${formatSize(requiredSpace)} 可用空间"
        }
        checkContinue(shouldContinue)
        if (!archive(spec).isFile) download(spec, shouldContinue, onProgress)
        checkContinue(shouldContinue)
        onProgress(update(pack) { current ->
            current.copy(
                phase = ModelDownloadPhase.INSTALLING,
                bytesPerSecond = 0L,
                message = "下载完成，正在校验模型包完整性"
            )
        })
        verifyArchive(spec, shouldContinue)
        onProgress(update(pack) { current ->
            current.copy(
                phase = ModelDownloadPhase.INSTALLING,
                bytesPerSecond = 0L,
                message = "校验通过，正在解压安装"
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
                downloadedBytes = spec.archiveBytes,
                totalBytes = spec.archiveBytes,
                message = "已安装，可离线使用"
            )
        })
        return directory
    }

    private fun verifyArchive(spec: ModelPackageSpec, shouldContinue: () -> Boolean) {
        val result = ModelArchiveIntegrity.verify(archive(spec), spec.archive, shouldContinue)
        if (result == ArchiveVerification.Verified) return
        discardDownload(spec)
        throw IllegalStateException(integrityFailureMessage(spec, result))
    }

    private fun integrityFailureMessage(spec: ModelPackageSpec, result: ArchiveVerification): String =
        when (result) {
            is ArchiveVerification.SizeMismatch ->
                "${spec.label}校验失败：文件大小 ${result.actualBytes} 字节，与固定值 " +
                    "${result.expectedBytes} 字节不符，已删除，请重新下载"
            is ArchiveVerification.DigestMismatch ->
                "${spec.label}校验失败：SHA-256 与固定值不一致，文件可能损坏或被替换，已删除，请重新下载"
            ArchiveVerification.Verified -> "${spec.label}已通过完整性校验"
        }

    /** Removes every downloaded byte for [spec] so the next attempt starts from a clean slate. */
    private fun discardDownload(spec: ModelPackageSpec) {
        archive(spec).delete()
        partial(spec).delete()
        prefs.edit().remove(totalKey(spec.pack)).apply()
    }

    private fun download(
        spec: ModelPackageSpec,
        shouldContinue: () -> Boolean,
        onProgress: (ModelDownloadState) -> Unit
    ) {
        val part = partial(spec)
        part.parentFile?.mkdirs()
        when (DownloadResumePolicy.partialAction(part.length(), spec.archiveBytes)) {
            PartialDownloadAction.COMPLETE -> {
                promoteCompletedArchive(spec)
                return
            }
            PartialDownloadAction.RESTART -> if (part.exists()) part.delete()
            PartialDownloadAction.RESUME -> Unit
        }
        var received = part.length()
        val connection = openFollowingRedirects(spec.archiveUrl, received)
        try {
            val code = connection.responseCode
            val remoteTotal = parseTotal(connection.getHeaderField("Content-Range"))
            val contentLength = connection.contentLengthLong
            val advertisedTotal = DownloadResumePolicy.advertisedTotal(code, contentLength, remoteTotal)
            if (DownloadResumePolicy.conflictsWithPinnedSize(advertisedTotal, spec.archiveBytes)) {
                discardDownload(spec)
                throw IllegalStateException(
                    "${spec.label}的上游文件大小（$advertisedTotal 字节）与本版本固定值" +
                        "（${spec.archiveBytes} 字节）不一致，已停止下载，避免安装被替换的模型"
                )
            }
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
            val hasKnownTotal = remoteTotal > 0L || contentLength > 0L
            val total = DownloadResumePolicy.resolvedTotal(
                existingBytes = received,
                append = append,
                contentLength = contentLength,
                rangeTotal = remoteTotal,
                expectedBytes = spec.archiveBytes
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
            var exceededPinnedSize = false
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(part, append).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        checkContinue(shouldContinue)
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (received + count > spec.archiveBytes) {
                            exceededPinnedSize = true
                            break
                        }
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
            if (exceededPinnedSize) {
                discardDownload(spec)
                throw IllegalStateException("${spec.label}下载内容超过固定大小，已丢弃，请重新下载")
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
                setRequestProperty("User-Agent", "ClassCue-Android/${BuildConfig.VERSION_NAME}")
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
                spec.archiveBytes,
                spec.archiveBytes,
                message = "已安装，可离线使用"
            )
        }
        val downloaded = partial(spec).length()
        val total = prefs.getLong(totalKey(spec.pack), spec.archiveBytes)
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

    private fun spec(pack: OfflineModelPack): ModelPackageSpec = OfflineModelCatalog.spec(pack)
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
