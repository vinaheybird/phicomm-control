package com.phicomm.r1.control.wakeup

import com.phicomm.r1.control.model.ModelAssetManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UnisoundModelProvisionerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createSyntheticApk(version: String): File {
        val apk = tempFolder.newFile("SyntheticUnisound.apk")
        ZipOutputStream(FileOutputStream(apk)).use { zos ->
            fun add(path: String, data: ByteArray) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(data)
                zos.closeEntry()
            }
            add("assets/asrfix/data", ByteArray(4096) { 0x1A })
            add("assets/wakeup/data", ByteArray(647) { 0x2B })
            add("assets/net/data", ByteArray(1321) { 0x3C })
            add("assets/version/data", version.toByteArray())
        }
        return apk
    }

    @Test
    fun extractsOnceAndCachesOnSecondRun() {
        val apk = createSyntheticApk("v3.10.77-test")
        val filesDir = tempFolder.newFolder("files")
        val manifest = ModelAssetManifest(sourceApkPath = apk.absolutePath)
        val provisioner = UnisoundModelProvisioner(
            manifest = manifest,
            crcValidator = { File(it).length() > 0L }
        )

        val first = provisioner.provision(filesDir).getOrThrow()
        assertEquals(3, first.extractedCount)
        assertTrue(first.modelDirPathWithSlash.endsWith(File.separator))
        assertTrue(File(first.wakeupGrammarPath).exists())
        assertTrue(File(first.netGrammarPath).exists())

        val second = provisioner.provision(filesDir).getOrThrow()
        assertEquals(0, second.extractedCount)
    }

    @Test
    fun reExtractsCorruptedModelAutomatically() {
        val apk = createSyntheticApk("v3.10.77-test")
        val filesDir = tempFolder.newFolder("files_corrupt")
        val manifest = ModelAssetManifest(sourceApkPath = apk.absolutePath)
        val provisioner = UnisoundModelProvisioner(
            manifest = manifest,
            crcValidator = { path -> File(path).length() >= 647L }
        )

        val first = provisioner.provision(filesDir).getOrThrow()
        assertEquals(3, first.extractedCount)

        // Corrupt wakeup.dat by truncating it
        File(first.wakeupGrammarPath).writeBytes(byteArrayOf(1, 2, 3))

        val repaired = provisioner.provision(filesDir).getOrThrow()
        assertEquals(1, repaired.extractedCount)
        assertEquals(647L, File(repaired.wakeupGrammarPath).length())
    }

    @Test
    fun failsWhenCrcValidationRejectsExtractedFile() {
        val apk = createSyntheticApk("v3.10.77-test")
        val filesDir = tempFolder.newFolder("files_bad_crc")
        val manifest = ModelAssetManifest(
            sourceApkPath = apk.absolutePath,
            entries = ModelAssetManifest.DEFAULT_ENTRIES.map { it.copy(requireCrcCheck = true) }
        )
        val provisioner = UnisoundModelProvisioner(
            manifest = manifest,
            crcValidator = { false }
        )

        val result = provisioner.provision(filesDir)
        assertTrue(result.isFailure)
    }
}
