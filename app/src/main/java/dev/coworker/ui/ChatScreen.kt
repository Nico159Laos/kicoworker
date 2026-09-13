package dev.coworker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.coworker.agent.AgentViewModel
import dev.coworker.llm.OnnxLlmEngine
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(vm: AgentViewModel) {
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("http://192.168.1.x:1880") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("KicoWorker v0.3 (LLM)")
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.messages) { msg ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card { Text(msg.text, Modifier.padding(10.dp)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Befehl eingeben...") }
                )
                Button(
                    onClick = { vm.send(input); input = "" },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Los") }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            serverUrl = serverUrl,
            onServerUrlChange = { serverUrl = it },
            onSave = {
                vm.setServerUrl(serverUrl)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun SettingsDialog(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var downloadProgress by remember { mutableStateOf(-1) }
    var isDownloading by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("Lädt...") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Server") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("LLM Modell") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        Text("Node-RED Server URL:")
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = onServerUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("http://192.168.1.x:1880") }
                        )
                        Text(
                            "Der Server wird für komplexe Anfragen genutzt",
                            modifier = Modifier.padding(top = 8.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                    1 -> {
                        Text("Lokales KI-Modell:", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                        Text(
                            "Modelle werden heruntergeladen und lokal gespeichert",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OnnxLlmEngine.AVAILABLE_MODELS.forEach { model ->
                            ModelItem(
                                model = model,
                                isDownloading = isDownloading,
                                downloadProgress = downloadProgress,
                                onDownload = {
                                    scope.launch {
                                        isDownloading = true
                                        downloadProgress = 0
                                        onDismiss()
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun ModelItem(
    model: OnnxLlmEngine.ModelInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(model.name, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text(
                "${model.description} • ${model.sizeMb}MB",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            if (isDownloading && downloadProgress >= 0) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
        } else {
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Herunterladen")
            }
        }
    }
}