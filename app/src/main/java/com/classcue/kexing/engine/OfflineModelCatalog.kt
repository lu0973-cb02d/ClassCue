package com.classcue.kexing.engine

/** Where model packs are fetched from. Both sources are checked against the same pinned hashes. */
enum class ModelDownloadSource { GITHUB, MIRROR }

/** One required model file with its pinned size and SHA-256. */
internal data class ModelFile(val name: String, val checksum: ArchiveChecksum)

internal data class ModelPackageSpec(
    val pack: OfflineModelPack,
    val label: String,
    val directoryName: String,
    val archiveUrl: String,
    val archive: ArchiveChecksum,
    /** Files needed at runtime; the mirror downloads exactly these, one by one. */
    val files: List<ModelFile>,
    /** Hugging Face repository holding the same files, served in China by hf-mirror.com. */
    val mirrorRepo: String,
    val mirrorBase: String = OfflineModelCatalog.MIRROR_BASE
) {
    val archiveBytes: Long get() = archive.sizeBytes
    val requiredFiles: List<String> get() = files.map { it.name }
    val mirrorBytes: Long get() = files.sumOf { it.checksum.sizeBytes }
}

/**
 * Downloadable sherpa-onnx packs. Archive size and SHA-256 were computed from the official
 * k2-fsa/sherpa-onnx `asr-models` release assets on 2026-10-15; the per-file values were
 * computed from the extracted archives and match the Hugging Face copies byte for byte. If
 * upstream ever replaces a file under the same name, the client refuses to install it until a
 * new ClassCue release re-pins the values.
 */
internal object OfflineModelCatalog {
    private const val OFFICIAL_RELEASE_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
    const val MIRROR_BASE = "https://hf-mirror.com/"

    val specs: Map<OfflineModelPack, ModelPackageSpec> = listOf(
        ModelPackageSpec(
            pack = OfflineModelPack.MEDIUM,
            label = "中档流式模型",
            directoryName = "sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30",
            archiveUrl = OFFICIAL_RELEASE_BASE + "sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30.tar.bz2",
            archive = ArchiveChecksum(
                sizeBytes = 132_634_597L,
                sha256 = "5a2832047ea1f97dd0dc595b816c230c4bafad65cfc0341fa57517cadc50afd0"
            ),
            files = listOf(
                ModelFile("encoder.int8.onnx", ArchiveChecksum(161_141_793L, "5ac51e27981bb4dab01bb9be4958453ba50c3b61c063ddda0eab23fd3671aa4f")),
                ModelFile("decoder.onnx", ArchiveChecksum(5_165_083L, "06522ad63cec0fdf6809f4e1db9bb4f7d710c34582e3b35db62ac60eccafac7e")),
                ModelFile("joiner.int8.onnx", ArchiveChecksum(1_033_416L, "b34584dc6f561089e1d747fedebb3765f2caa72c927ef54d7ca55e5ae40a814b")),
                ModelFile("tokens.txt", ArchiveChecksum(20_628L, "6193c7ea1c96d0d9a1e9652789b40d13a8a913b434a5451e93158f5a09fd6652"))
            ),
            mirrorRepo = "csukuangfj/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30"
        ),
        ModelPackageSpec(
            pack = OfflineModelPack.LARGE,
            label = "大档精修模型",
            directoryName = "sherpa-onnx-zipformer-ctc-zh-int8-2025-07-03",
            archiveUrl = OFFICIAL_RELEASE_BASE + "sherpa-onnx-zipformer-ctc-zh-int8-2025-07-03.tar.bz2",
            archive = ArchiveChecksum(
                sizeBytes = 301_377_906L,
                sha256 = "f3ad1814fea34c407eab0cc3df6f6b625419ac9a60d8aebd8efe772a8e85ef67"
            ),
            files = listOf(
                ModelFile("model.int8.onnx", ArchiveChecksum(367_074_356L, "e291b9c468b651e2697caa09bc684326c3addc6a019e78eb537cfd1a8248ca07")),
                ModelFile("tokens.txt", ArchiveChecksum(13_366L, "6fed8c6c248516f38e7faa19404b57413e8ce259f1cbc1fa4aebc86eac32fdfd"))
            ),
            mirrorRepo = "csukuangfj/sherpa-onnx-zipformer-ctc-zh-int8-2025-07-03"
        )
    ).associateBy(ModelPackageSpec::pack)

    fun spec(pack: OfflineModelPack): ModelPackageSpec = specs.getValue(pack)

    fun mirrorUrl(spec: ModelPackageSpec, file: ModelFile): String =
        "${spec.mirrorBase}${spec.mirrorRepo}/resolve/main/${file.name}"
}
