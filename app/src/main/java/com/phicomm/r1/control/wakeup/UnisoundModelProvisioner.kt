package com.phicomm.r1.control.wakeup

import cn.yunzhisheng.asrfix.JniAsrFix
import com.phicomm.r1.control.model.ModelAssetManifest
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile

data class ProvisionedModels(
    val modelDirPathWithSlash: String,
    val wakeupGrammarPath: String,
    val netGrammarPath: String,
    val extractedCount: Int,
    val elapsedMs: Long
) {
    val grammarPathsCsv: String
        get() = "$wakeupGrammarPath,$netGrammarPath"
}

/**
 * Extracts stock Unisound acoustic and wakeup model files directly from the
 * read-only `/system/app/Unisound/Unisound.apk` (`0644`) into app-private storage,
 * using atomic `.tmp` writes and native `JniAsrFix.checkCrc` validation.
 */
class UnisoundModelProvisioner(
    private val manifest: ModelAssetManifest = ModelAssetManifest(),
    private val crcValidator: (String) -> Boolean = { path -> JniAsrFix.checkCrc(path) },
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    fun provision(appFilesDir: File): Result<ProvisionedModels> = runCatching {
        val startMs = clockMs()
        val targetDir = File(appFilesDir, manifest.targetDirName)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            error("Failed to create model directory: ${targetDir.absolutePath}")
        }

        val apkFile = File(manifest.sourceApkPath)
        if (!apkFile.exists() || !apkFile.canRead()) {
            error("Stock Unisound APK is not readable at ${manifest.sourceApkPath}")
        }

        var extractedCount = 0
        ZipFile(apkFile).use { zip ->
            val expectedVersion = readZipTextEntry(zip, manifest.versionEntryPath)?.trim()
            val versionFile = File(targetDir, VERSION_FILE_NAME)
            val cachedVersion = if (versionFile.exists()) versionFile.readText().trim() else null
            val versionMatches = expectedVersion != null && expectedVersion == cachedVersion

            for (entry in manifest.entries) {
                val zipEntry = zip.getEntry(entry.zipEntryPath)
                    ?: error("Missing required model entry '${entry.zipEntryPath}' in ${manifest.sourceApkPath}")
                val expectedSize = zipEntry.size
                val outFile = File(targetDir, entry.outputFileName)
                val sizeOk = outFile.exists() &&
                    outFile.length() > 0L &&
                    (expectedSize < 0L || outFile.length() == expectedSize)
                val needsExtract = !versionMatches ||
                    !sizeOk ||
                    (entry.requireCrcCheck && !crcValidator(outFile.absolutePath))

                if (needsExtract) {
                    val actualCrc32 = extractAtomic(zip, entry.zipEntryPath, outFile)
                    extractedCount++
                    if (expectedSize >= 0L && outFile.length() != expectedSize) {
                        outFile.delete()
                        error("Size mismatch after extracting ${entry.outputFileName}: expected=$expectedSize actual=${outFile.length()}")
                    }
                    if (zipEntry.crc >= 0L && actualCrc32 != zipEntry.crc) {
                        outFile.delete()
                        error("ZIP CRC-32 mismatch after extracting ${entry.outputFileName}: expected=${zipEntry.crc} actual=$actualCrc32")
                    }
                    if (entry.requireCrcCheck && !crcValidator(outFile.absolutePath)) {
                        outFile.delete()
                        error("CRC validation failed after extracting ${entry.outputFileName}")
                    }
                }
            }

            if (expectedVersion != null && (!versionMatches || !versionFile.exists())) {
                val tmpVersion = File(targetDir, "$VERSION_FILE_NAME.tmp")
                tmpVersion.writeText(expectedVersion)
                if (!tmpVersion.renameTo(versionFile)) {
                    versionFile.writeText(expectedVersion)
                    tmpVersion.delete()
                }
            }
        }

        val dirWithSlash = targetDir.absolutePath.trimEnd(File.separatorChar) + File.separator
        val wakeupPath = File(targetDir, "wakeup.dat").absolutePath
        val netPath = File(targetDir, "net.dat").absolutePath
        ProvisionedModels(
            modelDirPathWithSlash = dirWithSlash,
            wakeupGrammarPath = wakeupPath,
            netGrammarPath = netPath,
            extractedCount = extractedCount,
            elapsedMs = (clockMs() - startMs).coerceAtLeast(0L)
        )
    }

    private fun readZipTextEntry(zip: ZipFile, entryPath: String): String? {
        val entry = zip.getEntry(entryPath) ?: return null
        return zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun extractAtomic(zip: ZipFile, zipEntryPath: String, targetFile: File): Long {
        val entry = zip.getEntry(zipEntryPath)
            ?: error("Missing required model entry '$zipEntryPath' in ${manifest.sourceApkPath}")
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()

        val crc32 = CRC32()
        zip.getInputStream(entry).use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    crc32.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }

        if (targetFile.exists() && !targetFile.delete()) {
            tempFile.delete()
            error("Failed to replace existing model file: ${targetFile.absolutePath}")
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.delete()
            error("Failed to atomically move ${tempFile.name} -> ${targetFile.name}")
        }
        return crc32.value
    }

    companion object {
        private const val VERSION_FILE_NAME = "version"
        private const val COPY_BUFFER_BYTES = 16 * 1024
    }
}
