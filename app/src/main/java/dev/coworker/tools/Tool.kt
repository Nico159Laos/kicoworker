package dev.coworker.tools

import kotlinx.serialization.json.JsonObject

/** Sicherheitsstufe entscheidet ueber Auto-Ausfuehrung vs. Bestaetigung. */
enum class RiskLevel { SAFE, CONFIRM, SENSITIVE }

interface Tool {
    val name: String
    val description: String
    val risk: RiskLevel
    suspend fun execute(params: JsonObject): ToolResult
}

sealed class ToolResult {
    data class Success(val message: String) : ToolResult()
    data class NeedsUserAction(val message: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
}
