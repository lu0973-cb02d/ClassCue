package com.classcue.kexing.engine

internal data class ModelPackageSpec(
    val pack: OfflineModelPack,
    val label: String,
    val directoryName: String,
    val archiveUrl: String,
    val archive: ArchiveChecksum,
    val requiredFiles: List<String>
) {
    val archiveBytes: Long get() = archive.sizeBytes
}

/**
 * Downloadable sherpa-onnx packs. Size and SHA-256 were computed from the official
 * k2-fsa/sherpa-onnx `asr-models` release assets on 2026-10-15. If upstream ever
 * replaces an asset under the same name, the client refuses to install it until a new
 * ClassCue release re-pins the values.
 */
internal object OfflineModelCatalog {
    private const val OFFICIAL_RELEASE_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"

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
            requiredFiles = listOf("encoder.int8.onnx", "decoder.onnx", "joiner.int8.onnx", "tokens.txt")
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
            requiredFiles = listOf("model.int8.onnx", "tokens.txt")
        )
    ).associateBy(ModelPackageSpec::pack)

    fun spec(pack: OfflineModelPack): ModelPackageSpec = specs.getValue(pack)
}
