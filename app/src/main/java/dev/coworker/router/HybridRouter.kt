package dev.coworker.router

import dev.coworker.llm.LlmEngine
import dev.coworker.llm.OnnxLlmEngine

enum class Backend { LOCAL, HOME_SERVER }

data class RoutingDecision(
    val backend: Backend,
    val reason: String,
    val engine: LlmEngine? = null
)

class HybridRouter(
    private val context: android.content.Context,
    private val serverAvailable: () -> Boolean = { false },
    private val serverUrl: String = "http://192.168.1.x:1880"
) {
    private var localEngine: OnnxLlmEngine? = null
    private var serverEngine: dev.coworker.llm.OllamaHttpEngine? = null

    private val serverKeywords = listOf(
        "pdf", "datei", "analyse", "suche", "web", "recherche",
        "code", "programm", "script", "lang", "zusammenfassung",
        "internet", "google", "wiki"
    )

    private val localKeywords = listOf(
        "timer", "alarm", "wecker", "navigiere", "navigation",
        "ort", "adresse", "route", "rufe", "anruf", "telefon",
        "nachricht", "sms", "whatsapp", "merke", "notiz", "erinnere"
    )

    fun init() {
        localEngine = OnnxLlmEngine(context)
    }

    fun route(userInput: String, contextTokens: Int = 0, explicitServerTrigger: Boolean = false): RoutingDecision {
        val input = userInput.lowercase()

        if (explicitServerTrigger && serverAvailable()) {
            return RoutingDecision(
                Backend.HOME_SERVER,
                "expliziter Server-Trigger",
                getServerEngine()
            )
        }

        if (contextTokens > 3000 && serverAvailable()) {
            return RoutingDecision(
                Backend.HOME_SERVER,
                "Kontext zu lang für lokales Modell",
                getServerEngine()
            )
        }

        val hasServerKeyword = serverKeywords.any { it in input }
        val hasLocalKeyword = localKeywords.any { it in input }

        when {
            hasServerKeyword && serverAvailable() -> {
                return RoutingDecision(
                    Backend.HOME_SERVER,
                    "Server-Keyword erkannt: ${serverKeywords.find { it in input }}",
                    getServerEngine()
                )
            }
            hasLocalKeyword -> {
                return RoutingDecision(
                    Backend.LOCAL,
                    "Lokales Tool erkannt: ${localKeywords.find { it in input }}",
                    getLocalEngine()
                )
            }
            serverAvailable() -> {
                return RoutingDecision(
                    Backend.HOME_SERVER,
                    "Fallback: Server verwenden",
                    getServerEngine()
                )
            }
            else -> {
                return RoutingDecision(
                    Backend.LOCAL,
                    "Standard: lokales Modell",
                    getLocalEngine()
                )
            }
        }
    }

    fun isLocalModelAvailable(): Boolean = localEngine?.isModelLoaded() ?: false

    private fun getLocalEngine(): LlmEngine {
        if (localEngine == null) {
            localEngine = OnnxLlmEngine(context)
        }
        return localEngine!!
    }

    private fun getServerEngine(): LlmEngine {
        if (serverEngine == null) {
            serverEngine = dev.coworker.llm.OllamaHttpEngine(serverUrl)
        }
        return serverEngine!!
    }

    fun setServerUrl(url: String) {
        serverEngine = dev.coworker.llm.OllamaHttpEngine(url)
    }
}
