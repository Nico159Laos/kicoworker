package dev.coworker.agent

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.coworker.llm.AgentAction
import dev.coworker.llm.LlmEngine
import dev.coworker.llm.OnnxLlmEngine
import dev.coworker.llm.OllamaHttpEngine
import dev.coworker.router.Backend
import dev.coworker.router.HybridRouter
import dev.coworker.router.RoutingDecision
import dev.coworker.tools.ComposeMessageTool
import dev.coworker.tools.DialerTool
import dev.coworker.tools.NavigationTool
import dev.coworker.tools.NoteTool
import dev.coworker.tools.TimerTool
import dev.coworker.tools.ToolRegistry
import dev.coworker.tools.ToolResult
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String)

class AgentViewModel(app: Application) : AndroidViewModel(app) {

    val messages = mutableStateListOf<ChatMessage>(
        ChatMessage(false, "Hallo! Ich bin dein KI Co-Worker (v0.2).\nProbier z.B.:\n" +
            "- Stell einen Timer auf 3 Minuten\n" +
            "- Navigiere mich zum Supermarkt\n" +
            "- Merke dir: Milch kaufen\n" +
            "- Schreib Maria dass ich 10 Minuten später komme\n" +
            "- Oder: Analysiere diese PDF (Server)")
    )

    private val registry = ToolRegistry(
        listOf(
            NavigationTool(app),
            TimerTool(app),
            DialerTool(app),
            ComposeMessageTool(app),
            NoteTool()
        )
    )

    private val hybridRouter = HybridRouter(
        context = app,
        serverAvailable = { isServerAvailable(app) },
        serverUrl = "http://192.168.1.x:1880"
    )

    private val localLlm: LlmEngine = OnnxLlmEngine(app)

    init {
        viewModelScope.launch {
            val downloadedModels = (localLlm as? OnnxLlmEngine)?.getDownloadedModels() ?: emptyList()
            if (downloadedModels.isNotEmpty()) {
                (localLlm as? OnnxLlmEngine)?.loadModel(downloadedModels.first())
            }
        }
    }

    fun send(input: String) {
        if (input.isBlank()) return
        messages.add(ChatMessage(true, input))
        viewModelScope.launch {
            val decision: RoutingDecision = hybridRouter.route(input)
            
            messages.add(ChatMessage(false, "[Router] ${decision.backend.name}: ${decision.reason}"))

            val llm: LlmEngine = when (decision.backend) {
                Backend.LOCAL -> localLlm
                Backend.HOME_SERVER -> decision.engine ?: OllamaHttpEngine()
            }

            when (val action = llm.decide(input)) {
                is AgentAction.Say -> messages.add(ChatMessage(false, action.text))
                is AgentAction.ToolCall -> {
                    messages.add(ChatMessage(false, "[Tool] " + action.tool + " " + action.params.toString()))
                    val text = when (val r = registry.dispatch(action.tool, action.params)) {
                        is ToolResult.Success -> r.message
                        is ToolResult.NeedsUserAction -> r.message
                        is ToolResult.Error -> "Fehler: " + r.message
                    }
                    messages.add(ChatMessage(false, text))
                }
            }
        }
    }

    fun setServerUrl(url: String) {
        hybridRouter.setServerUrl(url)
        messages.add(ChatMessage(false, "Server-URL aktualisiert: $url"))
    }

    private fun isServerAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
