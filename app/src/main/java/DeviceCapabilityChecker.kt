package com.kicoworker.llm // TODO: an euer tatsächliches Package anpassen

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

/**
 * Prüft, ob dieses Gerät ein lokales GGUF-Modell via llama.cpp sinnvoll
 * ausführen kann, und schlägt eine passende Modell-Größe (Tier) vor.
 */
data class DeviceCapability(
    val isSupportedAbi: Boolean,
    val abi: String,
    val totalRamMb: Long,
    val availableStorageMb: Long,
    val recommendedTier: ModelTier?
)

/**
 * Ein Tier = eine konkrete GGUF-Datei + die Mindest-Voraussetzungen dafür.
 *
 * v0.2: nur TINY. Sobald das läuft, SMALL (2B) und MEDIUM (4B) ergänzen —
 * gleiche Engine, nur anderes GGUF.
 */
enum class ModelTier(
    val label: String,
    val minRamMb: Long,
    val downloadSizeMb: Long,
    val downloadUrl: String,
    val sha256: String
) {
    TINY(
        label = "qwen3.5-0.8b-q4_k_m",
        minRamMb = 3072,
        downloadSizeMb = 533,
        downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
        // Verifiziert von der HF-Dateiseite (Stand: siehe Recherche). Vor Produktiv-Einsatz
        // einmal selbst nachprüfen — Quants werden gelegentlich neu hochgeladen.
        sha256 = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
    )
}

object DeviceCapabilityChecker {

    // v0.2: nur arm64-v8a gebaut (deckt praktisch alle Geräte ab ~2018 ab).
    // armeabi-v7a bei Bedarf später als zweite CI-Build-Variante ergänzen.
    private val SUPPORTED_ABIS = setOf("arm64-v8a")

    fun check(context: Context): DeviceCapability {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS }
        val isSupportedAbi = abi != null

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)

        val stat = StatFs(context.filesDir.path)
        val availableStorageMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)

        // Faktor 1.3 als Puffer: Download-Tempfile + finale Datei können kurz
        // gleichzeitig existieren, plus etwas Luft für die App selbst.
        val tier = ModelTier.entries
            .filter { totalRamMb >= it.minRamMb && availableStorageMb >= it.downloadSizeMb * 1.3 }
            .maxByOrNull { it.minRamMb }

        return DeviceCapability(
            isSupportedAbi = isSupportedAbi,
            abi = abi ?: Build.SUPPORTED_ABIS.first(),
            totalRamMb = totalRamMb,
            availableStorageMb = availableStorageMb,
            recommendedTier = if (isSupportedAbi) tier else null
        )
    }
}
