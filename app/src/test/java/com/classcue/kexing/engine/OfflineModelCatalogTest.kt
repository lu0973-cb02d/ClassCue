package com.classcue.kexing.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModelCatalogTest {
    @Test
    fun everyDownloadablePackHasAPinnedOfficialArchive() {
        OfflineModelPack.entries.forEach { pack ->
            val spec = OfflineModelCatalog.spec(pack)

            assertEquals(pack, spec.pack)
            assertTrue(
                spec.archiveUrl,
                spec.archiveUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/")
            )
            assertEquals("${spec.directoryName}.tar.bz2", spec.archiveUrl.substringAfterLast('/'))
            assertTrue(spec.requiredFiles.isNotEmpty())
            assertTrue(
                OfflineModelCatalog.mirrorUrl(spec, spec.files.first())
                    .startsWith("https://hf-mirror.com/csukuangfj/${spec.directoryName}/resolve/main/")
            )
        }
    }

    @Test
    fun runtimeFilesArePinnedForTheMirror() {
        val medium = OfflineModelCatalog.spec(OfflineModelPack.MEDIUM)
        assertEquals(listOf("encoder.int8.onnx", "decoder.onnx", "joiner.int8.onnx", "tokens.txt"), medium.requiredFiles)
        assertEquals(167_360_920L, medium.mirrorBytes)
        val large = OfflineModelCatalog.spec(OfflineModelPack.LARGE)
        assertEquals(listOf("model.int8.onnx", "tokens.txt"), large.requiredFiles)
        assertEquals(
            ArchiveChecksum(367_074_356L, "e291b9c468b651e2697caa09bc684326c3addc6a019e78eb537cfd1a8248ca07"),
            large.files.first().checksum
        )
    }

    @Test
    fun pinnedValuesMatchTheVerifiedUpstreamAssets() {
        // Changing these values means a new upstream asset was re-verified for this release.
        assertEquals(
            ArchiveChecksum(132_634_597L, "5a2832047ea1f97dd0dc595b816c230c4bafad65cfc0341fa57517cadc50afd0"),
            OfflineModelCatalog.spec(OfflineModelPack.MEDIUM).archive
        )
        assertEquals(
            ArchiveChecksum(301_377_906L, "f3ad1814fea34c407eab0cc3df6f6b625419ac9a60d8aebd8efe772a8e85ef67"),
            OfflineModelCatalog.spec(OfflineModelPack.LARGE).archive
        )
    }
}
