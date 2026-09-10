package dev.coworker.agent

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.coworker.llm.AgentAction
import dev.coworker.llm.LlmEngine
import dev.coworker.llm.MockLlmEngine
import dev.coworker.llm.OllamaHttpEngine
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
        ChatMessage(false, "Hallo! Ich bin dein KI Co-Worker (v0.1).\nProbier z.B.:\n" +
            "- Stell einen Timer auf 3 Minuten\n" +
            "- Navigiere mich zum Supermarkt\n" +
            "- Merke dir: Milch kaufen\n" +
            "- Schreib Maria dass ich 10 Minuten spaeter komme")
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

    private val llm: LlmEngine = OllamaHttpEngine()
    // private val llm: LlmEngine = MockLlmEngine()  // fuer Offline-Tests

    fun send(input: String) {
        if (input.isBlank()) return
        messages.add(ChatMessage(true, input))
        viewModelScope.launch {
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
}
