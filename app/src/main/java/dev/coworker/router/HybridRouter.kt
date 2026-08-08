package dev.coworker.router

/**
 * Regelbasiertes Routing (keine Token-Konfidenz).
 * v0.1: immer LOCAL. Server-Anbindung (Ollama) kommt in v0.3.
 */
enum class Backend { LOCAL, HOME_SERVER }

data class RoutingDecision(val backend: Backend, val reason: String)

class HybridRouter(private val serverAvailable: () -> Boolean = { false }) {

    fun route(contextTokens: Int, explicitServerTrigger: Boolean): RoutingDecision {
        if (explicitServerTrigger && serverAvailable())
            return RoutingDecision(Backend.HOME_SERVER, "expliziter Trigger")
        if (contextTokens > 3000 && serverAvailable())
            return RoutingDecision(Backend.HOME_SERVER, "Kontext zu lang fuer lokales Modell")
        return RoutingDecision(Backend.LOCAL, "Standard: local-first")
    }
}
