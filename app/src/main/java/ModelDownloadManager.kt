package com.kicoworker.llm // TODO: an euer tatsächliches Package anpassen

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class DownloadProgress {
    data class InProgress(val downloadedBytes: Long, val totalBytes: Long) : DownloadProgress() {
        val fraction: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
    }
    data class Done(val file: File) : DownloadProgress()
    data class Failed(val error: Throwable) : DownloadProgress()
}

/**
 * Lädt ein GGUF-Modell nach context.filesDir/models herunter.
 * - Resumable via HTTP Range (bricht der Download ab, geht's beim nächsten
 *   Versuch an der Stelle weiter statt von vorn)
 * - SHA256-Verifikation nach Abschluss
 * - Keine externen Dependencies (kein OkHttp nötig)
 *
 * Nutzung z.B. aus AgentViewModel:
 *   ModelDownloadManager(context).downloadModel(tier).collect { progress -> ... }
 */
class ModelDownloadManager(private val context: Context) {

    fun downloadModel(tier: ModelTier): Flow<DownloadProgress> = flow {
        val targetDir = File(context.filesDir, "models").apply { mkdirs() }
        val targetFile = File(targetDir, "${tier.label}.gguf")
        val tmpFile = File(targetDir, "${tier.label}.gguf.part")

        if (targetFile.exists() && verifySha256(targetFile, tier.sha256)) {
            emit(DownloadProgress.Done(targetFile))
            return@flow
        }

        try {
            var downloaded = if (tmpFile.exists()) tmpFile.length() else 0L

            val connection = (URL(tier.downloadUrl).openConnection() as HttpURLConnection).apply {
                if (downloaded > 0) setRequestProperty("Range", "bytes=$downloaded-")
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
            }
            connection.connect()

            if (connection.responseCode !in intArrayOf(200, 206)) {
                throw IllegalStateException("HTTP ${connection.responseCode} beim Download von ${tier.downloadUrl}")
            }
            // Server unterstützt Range nicht -> von vorne beginnen
            if (connection.responseCode == 200 && downloaded > 0) {
                downloaded = 0L
                tmpFile.delete()
            }

            val totalBytes = connection.contentLengthLong.let {
                if (it > 0) it + downloaded else tier.downloadSizeMb * 1024L * 1024L
            }

            RandomAccessFile(tmpFile, "rw").use { raf ->
                raf.seek(downloaded)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var lastEmit = downloaded
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastEmit > 200 * 1024) {
                            emit(DownloadProgress.InProgress(downloaded, totalBytes))
                            lastEmit = downloaded
                        }
                    }
                }
            }
            connection.disconnect()
            emit(DownloadProgress.InProgress(downloaded, totalBytes))

            if (!verifySha256(tmpFile, tier.sha256)) {
                tmpFile.delete()
                throw IllegalStateException("Checksummen-Fehler nach Download — Datei verworfen, bitte erneut versuchen")
            }

            tmpFile.renameTo(targetFile)
            emit(DownloadProgress.Done(targetFile))
        } catch (e: Exception) {
            emit(DownloadProgress.Failed(e))
        }
    }.flowOn(Dispatchers.IO)

    fun deleteModel(tier: ModelTier) {
        File(File(context.filesDir, "models"), "${tier.label}.gguf").delete()
    }

    fun isDownloaded(tier: ModelTier): Boolean {
        val file = File(File(context.filesDir, "models"), "${tier.label}.gguf")
        return file.exists() && verifySha256(file, tier.sha256)
    }

    private fun verifySha256(file: File, expected: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
