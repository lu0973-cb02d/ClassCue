package com.classcue.kexing.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The README promises that the lesson history, API keys and recordings stay out of system backups
 * and device-to-device transfer. Android reads those rules from three places (cloud backup and
 * device transfer in `data_extraction_rules.xml`, plus the Android 11 and lower `backup_rules.xml`),
 * and the device-transfer block once lacked the history database.
 */
class BackupRulesTest {
    private val required = listOf(
        "database" to "classcue_history.db",
        // SQLite keeps recent writes in these sidecar files, which are separate paths in the same folder.
        "database" to "classcue_history.db-wal",
        "database" to "classcue_history.db-shm",
        "database" to "classcue_history.db-journal",
        "sharedpref" to "secret_store.xml",
        "file" to "recordings/",
        "external" to "recordings/"
    )

    @Test
    fun cloudBackupKeepsPrivateDataOut() = assertExcludes("data_extraction_rules.xml", "cloud-backup")

    @Test
    fun deviceTransferKeepsPrivateDataOut() = assertExcludes("data_extraction_rules.xml", "device-transfer")

    @Test
    fun legacyBackupRulesKeepPrivateDataOut() = assertExcludes("backup_rules.xml", "full-backup-content")

    private fun assertExcludes(fileName: String, section: String) {
        val declared = excludes(fileName, section)
        val missing = required.filterNot { it in declared }
        assertTrue("$fileName <$section> does not exclude: $missing", missing.isEmpty())
    }

    private fun excludes(fileName: String, section: String): Set<Pair<String, String>> {
        // Gradle runs unit tests from the module directory; an IDE may start at the repository root.
        val file = listOf("src/main/res/xml/$fileName", "app/src/main/res/xml/$fileName")
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("$fileName not found from ${File(".").absoluteFile}")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val container = document.getElementsByTagName(section).item(0) as? Element
            ?: error("<$section> is missing in $fileName")
        val nodes = container.getElementsByTagName("exclude")
        return (0 until nodes.length).map { index ->
            val node = nodes.item(index) as Element
            node.getAttribute("domain") to node.getAttribute("path")
        }.toSet()
    }
}
