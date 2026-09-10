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

class OllamaHttpEngine(
    private val baseUrl: String = "http://localhost:11434"
) : LlmEngine {

    private val client = OkHttpClient()
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

        val jsonBody = kotlinx.serialization.json.Json.encodeToString(
            ChatRequest.serializer(),
            request
        )

        val httpRequest = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext AgentAction.Say("Server-Fehler: ${response.code}")
            }

            val body = response.body?.string() ?: return@withContext AgentAction.Say("Keine Antwort")
            val chatResponse = json.decodeFromString(ChatResponse.serializer(), body)

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
    }
}