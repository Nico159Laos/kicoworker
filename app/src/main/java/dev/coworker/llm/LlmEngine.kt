package dev.coworker.llm

import kotlinx.serialization.json.JsonObject

sealed class AgentAction {
    data class ToolCall(val tool: String, val params: JsonObject) : AgentAction()
    data class Say(val text: String) : AgentAction()
}

/**
 * Abstraktion ueber die Inferenz. Implementierungen:
 * - MockLlmEngine (v0.1): Keyword-Parser, damit die Tool-Pipeline sofort testbar ist
 * - LlamaCppEngine (v0.2): llama.cpp via JNI mit GBNF-Grammar
 * - RemoteEngine  (v0.3): Ollama/Home-Server, OpenAI-kompatibel
 */
interface LlmEngine {
    suspend fun decide(userInput: String): AgentAction
}
