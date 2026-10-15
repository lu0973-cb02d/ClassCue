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
        }
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
