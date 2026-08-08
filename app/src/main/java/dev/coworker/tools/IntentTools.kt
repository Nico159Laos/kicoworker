package dev.coworker.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun JsonObject.str(key: String): String? =
    this[key]?.jsonPrimitive?.content

private fun Context.launch(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}

/** Navigation via geo-Intent - keine Sonderrechte noetig. */
class NavigationTool(private val ctx: Context) : Tool {
    override val name = "open_navigation"
    override val description = "Oeffnet Navigation zu einem Ziel (Adresse oder Ortsname)."
    override val risk = RiskLevel.SAFE

    override suspend fun execute(params: JsonObject): ToolResult {
        val dest = params.str("destination") ?: return ToolResult.Error("destination fehlt")
        val ok = ctx.launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(dest))))
        return if (ok) ToolResult.Success("Navigation zu '" + dest + "' geoeffnet.")
        else ToolResult.Error("Keine Karten-App gefunden.")
    }
}

/** Timer via AlarmClock-Intent. */
class TimerTool(private val ctx: Context) : Tool {
    override val name = "set_timer"
    override val description = "Stellt einen Timer. Parameter: minutes (Zahl)."
    override val risk = RiskLevel.SAFE

    override suspend fun execute(params: JsonObject): ToolResult {
        val minutes = params.str("minutes")?.toIntOrNull() ?: return ToolResult.Error("minutes fehlt")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "KI Co-Worker Timer")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val ok = ctx.launch(intent)
        return if (ok) ToolResult.Success("Timer auf " + minutes + " Minuten gestellt.")
        else ToolResult.Error("Keine Uhr-App gefunden.")
    }
}

/** Dialer oeffnen - Human-in-the-Loop: Nutzer drueckt selbst auf Anrufen. */
class DialerTool(private val ctx: Context) : Tool {
    override val name = "open_dialer"
    override val description = "Oeffnet den Telefon-Dialer, optional mit Nummer."
    override val risk = RiskLevel.CONFIRM

    override suspend fun execute(params: JsonObject): ToolResult {
        val number = params.str("number")
        val uri = if (number.isNullOrBlank()) null else Uri.parse("tel:" + number)
        val intent = if (uri != null) Intent(Intent.ACTION_DIAL, uri) else Intent(Intent.ACTION_DIAL)
        val ok = ctx.launch(intent)
        return if (ok) ToolResult.NeedsUserAction("Dialer geoeffnet - bitte Anruf selbst starten.")
        else ToolResult.Error("Dialer konnte nicht geoeffnet werden.")
    }
}

/** Nachricht vorbereiten - Nutzer waehlt App/Empfaenger und sendet selbst. */
class ComposeMessageTool(private val ctx: Context) : Tool {
    override val name = "compose_message"
    override val description = "Bereitet eine Nachricht vor (WhatsApp/SMS/etc.), Nutzer sendet selbst."
    override val risk = RiskLevel.CONFIRM

    override suspend fun execute(params: JsonObject): ToolResult {
        val text = params.str("text") ?: return ToolResult.Error("text fehlt")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(send, "Nachricht senden")
        val ok = ctx.launch(chooser)
        return if (ok) ToolResult.NeedsUserAction("Nachricht vorbereitet - App waehlen und senden.")
        else ToolResult.Error("Keine Messaging-App gefunden.")
    }
}

/** Lokale Notiz - bleibt komplett in der App (in-memory MVP, spaeter Room). */
object NotesStore {
    val notes = mutableListOf<String>()
}

class NoteTool : Tool {
    override val name = "save_note"
    override val description = "Speichert eine lokale Notiz. Parameter: text."
    override val risk = RiskLevel.SAFE

    override suspend fun execute(params: JsonObject): ToolResult {
        val text = params.str("text") ?: return ToolResult.Error("text fehlt")
        NotesStore.notes.add(text)
        return ToolResult.Success("Notiz gespeichert (" + NotesStore.notes.size + " insgesamt): " + text)
    }
}
