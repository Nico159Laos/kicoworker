package dev.coworker.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaHttpEngine(
    private val serverUrl: String = "http://192.168.1.x:1880"  // Node-RED
) : LlmEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @Serializable
    data class ChatRequest(
        val model: String = "phi3",
        val messages: List<Message>,
        val tools: List<Tool>? = null,
        val stream: Boolean = false
    )

    @Serializable
    data class Message(val role: String, val content: String)

    @Serializable
    data class Tool(
        val type: String = "function",
        val function: FunctionDef
    )

    @Serializable
    data class FunctionDef(
        val name: String,
        val description: String,
        val parameters: JsonObject? = null
    )

    @Serializable
    data class ChatResponse(
        val message: ResponseMessage
    )

    @Serializable
    data class ResponseMessage(
        val role: String,
        val content: String,
        val tool_calls: List<ToolCall>? = null
    )

    @Serializable
    data class ToolCall(
        val function: ToolFunction
    )

    @Serializable
    data class ToolFunction(
        val name: String,
        val arguments: JsonObject
    )

    override suspend fun decide(userInput: String): AgentAction = withContext(Dispatchers.IO) {
        val request = ChatRequest(
            messages = listOf(Message("user", userInput))
        )

        val jsonBody = try {
            kotlinx.serialization.json.Json.encodeToString(
                ChatRequest.serializer(),
                request
            )
        } catch (e: Exception) {
            return@withContext AgentAction.Say("Interner Fehler beim Erstellen der Anfrage: ${e.message}")
        }

        val httpRequest = Request.Builder()
            .url("$serverUrl/chat")  // Node-RED endpoint
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AgentAction.Say("Server-Fehler: ${response.code}")
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext AgentAction.Say("Server hat keine Antwort geschickt")
                }

                val chatResponse = try {
                    json.decodeFromString(ChatResponse.serializer(), body)
                } catch (e: Exception) {
                    return@withContext AgentAction.Say("Server-Antwort hat unerwartetes Format: ${e.message}")
                }

                val toolCalls = chatResponse.message.tool_calls
                if (!toolCalls.isNullOrEmpty()) {
                    val call = toolCalls.first()
                    return@withContext AgentAction.ToolCall(
                        tool = call.function.name,
                        params = call.function.arguments
                    )
                }

                AgentAction.Say(chatResponse.message.content)
            }
        } catch (e: java.net.ConnectException) {
            AgentAction.Say("Server nicht erreichbar unter $serverUrl — läuft er und bist du im selben Netzwerk?")
        } catch (e: java.net.SocketTimeoutException) {
            AgentAction.Say("Server antwortet nicht rechtzeitig (Timeout). Versuch's nochmal.")
        } catch (e: IOException) {
            AgentAction.Say("Verbindung zum Server fehlgeschlagen: ${e.message}")
        } catch (e: Exception) {
            AgentAction.Say("Unerwarteter Fehler beim Server-Aufruf: ${e.message}")
        }
    }
}