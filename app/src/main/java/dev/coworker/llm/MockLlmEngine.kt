package dev.coworker.llm

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * v0.1 Platzhalter: simuliert das LLM per Keyword-Erkennung.
 * Wird in v0.2 durch LlamaCppEngine (GBNF-erzwungenes JSON) ersetzt.
 */
class MockLlmEngine : LlmEngine {

    override suspend fun decide(userInput: String): AgentAction {
        val lower = userInput.lowercase()

        if (lower.contains("timer") || lower.contains("wecker")) {
            val minutes = Regex("(\\d+)").find(lower)?.groupValues?.get(1) ?: "5"
            return AgentAction.ToolCall("set_timer", buildJsonObject { put("minutes", minutes) })
        }

        for (kw in listOf("navigier", "route ", "weg nach", "weg zum", "weg zur", "fahre zu", "maps")) {
            if (lower.contains(kw)) {
                val dest = userInput
                    .replace(Regex("(?i).*?(navigiere?( mich)?( bitte)?|route|den weg|fahre)\\s*(nach|zum|zur|zu)?\\s*"), "")
                    .trim().removeSuffix(".").removeSuffix("!")
                return AgentAction.ToolCall("open_navigation", buildJsonObject {
                    put("destination", if (dest.isBlank()) userInput else dest)
                })
            }
        }

        for (kw in listOf("notiz", "merke", "notier")) {
            if (lower.contains(kw)) {
                val text = userInput.substringAfter(":", userInput)
                    .replace(Regex("(?i)(notiz|merke dir|merke|notiere?)\\s*"), "").trim()
                return AgentAction.ToolCall("save_note", buildJsonObject { put("text", text) })
            }
        }

        if (lower.contains("ruf") || lower.contains("anruf") || lower.contains("waehle")) {
            val number = Regex("(\\+?\\d[\\d\\s/-]{4,})").find(userInput)?.groupValues?.get(1)?.replace(" ", "")
            return AgentAction.ToolCall("open_dialer", buildJsonObject {
                if (number != null) put("number", number)
            })
        }

        if (lower.contains("schreib") || lower.contains("nachricht") || lower.contains("whatsapp") || lower.contains("sms")) {
            val text = userInput.substringAfter("dass ", userInput).trim()
            return AgentAction.ToolCall("compose_message", buildJsonObject { put("text", text) })
        }

        return AgentAction.Say(
            "Ich habe (noch) kein passendes Werkzeug erkannt. Verfuegbar: Timer, Navigation, Notiz, Anruf, Nachricht.\n" +
            "(v0.1 nutzt einen Keyword-Parser - das lokale LLM kommt in v0.2.)"
        )
    }
}
