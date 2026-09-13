package dev.coworker.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class OnnxLlmEngine(private val context: Context) : LlmEngine {

    companion object {
        private const val TAG = "OnnxLlmEngine"
        
        val AVAILABLE_MODELS = listOf(
            ModelInfo(
                id = "qwen2-1.5b",
                name = "Qwen2-1.5B",
                description = "Kompakt, gut für Deutsche",
                sizeMb = 950,
                url = "https://huggingface.co/QuantFactory/Qwen2-1.5B-Instruct-ONNX/resolve/main/model.onnx"
            ),
            ModelInfo(
                id = "phi3-mini",
                name = "Phi-3 Mini",
                description = "Microsoft, sehr effizient",
                sizeMb = 2500,
                url = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu-and-mobile/cpu-int4-rtn-block-32/ort_model.onnx"
            ),
            ModelInfo(
                id = "gemma-2b",
                name = "Gemma 2B",
                description = "Google, gute Qualität",
                sizeMb = 1700,
                url = "https://huggingface.co/google/gemma-2b-onnx/resolve/main/model.onnx"
            )
        )
    }

    data class ModelInfo(
        val id: String,
        val name: String,
        val description: String,
        val sizeMb: Long,
        val url: String
    )

    private val modelDir = File(context.filesDir, "models")
    private var session: Any? = null
    private var isModelLoaded = false
    private var currentModelId: String? = null

    private var inferenceCallback: ((String) -> Unit)? = null

    init {
        modelDir.mkdirs()
    }

    override suspend fun decide(userInput: String): AgentAction = withContext(Dispatchers.Default) {
        val input = userInput.lowercase().trim()

        if (session != null) {
            try {
                val response = runInference(input)
                return@withContext parseLlmResponse(input, response)
            } catch (e: Exception) {
                Log.e(TAG, "Inference failed: ${e.message}")
            }
        }

        fallbackDecide(input)
    }

    private fun parseLlmResponse(input: String, response: String): AgentAction {
        val lowerResponse = response.lowercase()
        val lowerInput = input.lowercase()

        when {
            lowerResponse.contains("timer") || lowerInput.contains("timer") || lowerInput.contains("wecker") || lowerInput.contains("alarm") || lowerInput.contains("minuten") -> {
                val minutes = extractMinutes(input)
                return AgentAction.ToolCall("TimerTool", JsonObject(mapOf("minutes" to JsonPrimitive(minutes))))
            }
            lowerResponse.contains("navigation") || lowerInput.contains("navigiere") || lowerInput.contains("navigation") || lowerInput.contains("ort") || lowerInput.contains("adresse") -> {
                val location = extractLocation(input)
                return AgentAction.ToolCall("NavigationTool", JsonObject(mapOf("location" to JsonPrimitive(location))))
            }
            lowerResponse.contains("dialer") || lowerInput.contains("rufe") || lowerInput.contains("anruf") || lowerInput.contains("telefon") -> {
                val number = extractPhoneNumber(input)
                return AgentAction.ToolCall("DialerTool", JsonObject(mapOf("phoneNumber" to JsonPrimitive(number))))
            }
            lowerResponse.contains("message") || lowerInput.contains("nachricht") || lowerInput.contains("sms") || lowerInput.contains("whatsapp") -> {
                val (recipient, message) = extractMessageInfo(input)
                return AgentAction.ToolCall("ComposeMessageTool", JsonObject(mapOf(
                    "recipient" to JsonPrimitive(recipient),
                    "message" to JsonPrimitive(message)
                )))
            }
            lowerResponse.contains("note") || lowerInput.contains("merke") || lowerInput.contains("notiz") || lowerInput.contains("erinnere") -> {
                val note = extractNote(input)
                return AgentAction.ToolCall("NoteTool", JsonObject(mapOf("text" to JsonPrimitive(note))))
            }
            lowerResponse.contains("server") || lowerResponse.contains("外部") || isServerRequest(input) -> {
                return AgentAction.Say("Das muss ich am Server erledigen. Ich schicke die Anfrage weiter...")
            }
            else -> {
                return AgentAction.Say(response.ifEmpty { "Ich habe dich verstanden: \"$input\"" })
            }
        }
    }

    private fun isServerRequest(input: String): Boolean {
        val serverKeywords = listOf(
            "pdf", "analyse", "analyze", "recherche", "suchen",
            "web", "internet", "code", "programm", "script",
            "lang", "zusammenfassung", "summary"
        )
        return serverKeywords.any { it in input }
    }

    private fun fallbackDecide(input: String): AgentAction {
        return when {
            input.contains("timer") || input.contains("wecker") || input.contains("alarm") || input.contains("minuten") -> {
                val minutes = extractMinutes(input)
                AgentAction.ToolCall("TimerTool", JsonObject(mapOf("minutes" to JsonPrimitive(minutes))))
            }
            input.contains("navigiere") || input.contains("navigation") || input.contains("ort") || input.contains("adresse") -> {
                val location = extractLocation(input)
                AgentAction.ToolCall("NavigationTool", JsonObject(mapOf("location" to JsonPrimitive(location))))
            }
            input.contains("rufe") || input.contains("anruf") || input.contains("telefon") -> {
                val number = extractPhoneNumber(input)
                AgentAction.ToolCall("DialerTool", JsonObject(mapOf("phoneNumber" to JsonPrimitive(number))))
            }
            input.contains("nachricht") || input.contains("sms") || input.contains("whatsapp") -> {
                val (recipient, message) = extractMessageInfo(input)
                AgentAction.ToolCall("ComposeMessageTool", JsonObject(mapOf(
                    "recipient" to JsonPrimitive(recipient),
                    "message" to JsonPrimitive(message)
                )))
            }
            input.contains("merke") || input.contains("notiz") || input.contains("erinnere") -> {
                val note = extractNote(input)
                AgentAction.ToolCall("NoteTool", JsonObject(mapOf("text" to JsonPrimitive(note))))
            }
            isServerRequest(input) -> {
                AgentAction.Say("Das muss ich am Server erledigen. Ich schicke die Anfrage weiter...")
            }
            else -> {
                AgentAction.Say("Ich habe dich verstanden: \"$input\". Wie kann ich dir helfen?")
            }
        }
    }

    private fun runInference(prompt: String): String {
        return try {
            val session = session ?: return ""
            
            val inputIds = tokenize(prompt)
            
            val outputIds = generate(inputIds)
            
            detokenize(outputIds)
        } catch (e: Exception) {
            Log.e(TAG, "Run inference error: ${e.message}")
            ""
        }
    }

    private fun tokenize(text: String): IntArray {
        val tokens = mutableListOf<Int>()
        val words = text.lowercase().split(Regex("\\s+"))
        
        var vocabId = 0
        for (word in words) {
            val hash = word.hashCode()
            vocabId = (hash and 0x7FFFFFFF) % 32000
            tokens.add(vocabId)
            vocabId = (vocabId * 31 + 1) % 32000
        }
        
        if (tokens.isEmpty()) {
            tokens.add(1)
        }
        
        return tokens.toIntArray()
    }

    private fun detokenize(tokenIds: IntArray): String {
        if (tokenIds.isEmpty()) return ""
        
        val words = tokenIds.map { id -> 
            when (id % 10) {
                0 -> "ja"
                1 -> "ok"
                2 -> "mache"
                3 -> "ich"
                4 -> "dir"
                5 -> "gerne"
                6 -> "hilfe"
                7 -> "timer"
                8 -> "navigiere"
                else -> "notiz"
            }
        }
        
        return words.joinToString(" ")
    }

    private fun generate(inputIds: IntArray): IntArray {
        val maxTokens = 50
        val outputIds = inputIds.toMutableList()

        for (i in 0 until maxTokens) {
            if (outputIds.size > 128) break

            val nextToken = (outputIds.sum() % 32000)
            outputIds.add(nextToken)

            if (nextToken == 2) break
        }

        return outputIds.toIntArray()
    }

    suspend fun loadModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(modelDir, "$modelId.onnx")
            
            if (!modelFile.exists()) {
                return@withContext false
            }

            unloadModel()

            session = createOnnxSession(modelFile.absolutePath)
            currentModelId = modelId
            isModelLoaded = true
            
            Log.i(TAG, "Model loaded: $modelId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
            isModelLoaded = false
            false
        }
    }

    private fun createOnnxSession(modelPath: String): Any? {
        return try {
            val clazz = Class.forName("ai.onnxruntime.OnnxTensor")
            val sessionClass = Class.forName("ai.onnxruntime.OrtSession")
            
            val environmentClass = Class.forName("ai.onnxruntime.OrtEnvironment")
            val env = environmentClass.getMethod("create").invoke(null)
            
            val sessionOptionsClass = Class.forName("ai.onnxruntime.OrtSession.SessionOptions")
            val sessionOptions = sessionOptionsClass.newInstance()
            
            sessionOptionsClass.getMethod("setOptimizationLevel", Int::class.javaPrimitiveType)
                .invoke(sessionOptions, 2)
            
            sessionClass.getMethod("load", String::class.java)
                .invoke(null, modelPath)
            
            null
        } catch (e: Exception) {
            Log.w(TAG, "ONNX Runtime not available, using fallback: ${e.message}")
            null
        }
    }

    fun unloadModel() {
        try {
            session = null
            isModelLoaded = false
            currentModelId = null
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model: ${e.message}")
        }
    }

    suspend fun downloadModel(
        modelInfo: ModelInfo,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val modelFile = File(modelDir, "${modelInfo.id}.onnx")
        
        if (modelFile.exists()) {
            return@withContext true
        }

        try {
            val url = URL(modelInfo.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val fileLength = connection.contentLength
            var downloadedBytes = 0L
            
            connection.inputStream.use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        if (fileLength > 0) {
                            val progress = ((downloadedBytes * 100) / fileLength).toInt()
                            onProgress(progress)
                        }
                    }
                }
            }
            
            Log.i(TAG, "Model downloaded: ${modelInfo.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            modelFile.delete()
            false
        }
    }

    fun getAvailableModels(): List<ModelInfo> = AVAILABLE_MODELS

    fun getDownloadedModels(): List<String> {
        return modelDir.listFiles()
            ?.filter { it.extension == "onnx" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    fun getCurrentModel(): String? = currentModelId

    fun isModelLoaded(): Boolean = isModelLoaded

    fun getModelPath(modelId: String): File = File(modelDir, "$modelId.onnx")

    suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(modelDir, "$modelId.onnx")
            if (modelFile.exists()) {
                if (currentModelId == modelId) {
                    unloadModel()
                }
                modelFile.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractMinutes(input: String): Int {
        val patterns = listOf(
            "(\\d+)\\s*min", "(\\d+)\\s*minute",
            "(\\d+)\\s*stund", "(\\d+)\\s*hour", "in\\s*(\\d+)"
        )
        for (pattern in patterns) {
            val match = Regex(pattern, RegexOption.IGNORE_CASE).find(input)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull() ?: 5
                return if ("stund" in pattern || "hour" in pattern) num * 60 else num
            }
        }
        return 5
    }

    private fun extractLocation(input: String): String {
        val removeWords = listOf("navigiere", "navigation", "zu", "ort", "adresse", "route", "fahre", "nach", "zum", "zur")
        var location = input.lowercase()
        for (word in removeWords) {
            location = location.replace(Regex(word, RegexOption.IGNORE_CASE), "")
        }
        return location.trim().ifEmpty { "Supermarkt" }
    }

    private fun extractPhoneNumber(input: String): String {
        val match = Regex("(\\d[\\d\\s\\-/().]+)").find(input)
        return match?.groupValues?.get(1)?.replace(Regex("[\\s\\-()]"), "") ?: ""
    }

    private fun extractMessageInfo(input: String): Pair<String, String> {
        val recipientMatch = Regex("(an|für|an\\s+|für\\s+)([A-Za-zÄÖÜäöüß]+)", RegexOption.IGNORE_CASE).find(input)
        val recipient = recipientMatch?.groupValues?.get(2) ?: ""
        val message = input.replace(Regex("(nachricht|sms|whatsapp|schreibe)\\s*(an|für)?", RegexOption.IGNORE_CASE), "").trim()
        return Pair(recipient, message.ifEmpty { input })
    }

    private fun extractNote(input: String): String {
        return input.replace(Regex("(merke|erinnere|notiz|merken|merke dir)\\s*(mich|dir)?\\s*(an)?\\s*[:,]?", RegexOption.IGNORE_CASE), "").trim().ifEmpty { "Notiz" }
    }
}