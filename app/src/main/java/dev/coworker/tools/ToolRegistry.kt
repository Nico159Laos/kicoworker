package dev.coworker.tools

import android.util.Log
import kotlinx.serialization.json.JsonObject

/**
 * Zentrale Registry. Dispatcht validierte Tool-Calls.
 * TODO Phase 1: JSON-Schema-Validierung + Approval-UI fuer CONFIRM-Tools.
 */
class ToolRegistry(private val tools: List<Tool>) {

    fun systemPromptSection(): String =
        tools.joinToString("\n") { "- " + it.name + ": " + it.description }

    suspend fun dispatch(toolName: String, params: JsonObject): ToolResult {
        val tool = tools.find { it.name == toolName }
            ?: return ToolResult.Error("Unbekanntes Tool: " + toolName)

        Log.i("AuditLog", "tool=" + toolName + " params=" + params.toString())

        return when (tool.risk) {
            RiskLevel.SENSITIVE ->
                ToolResult.Error("Tool '" + tool.name + "' erfordert Freigabe (noch nicht implementiert).")
            else -> try {
                tool.execute(params)
            } catch (e: Exception) {
                ToolResult.Error("Fehler bei " + tool.name + ": " + (e.message ?: "unbekannt"))
            }
        }
    }
}
