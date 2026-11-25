package com.aibuilt.arin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aibuilt.arin.data.AppContainer
import com.aibuilt.arin.data.AnalystInstall
import com.aibuilt.arin.model.Tab
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.aibuilt.arin.data.Prefs

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch { container.settings.flow.collect { p -> _ui.value = _ui.value.copy(prefs = p) } }
        viewModelScope.launch { container.install.bridge.collect { b -> _ui.value = _ui.value.copy(bridge = b) } }
    }

    fun save(base: String, key: String, chat: String, rea: String, update: String) {
        viewModelScope.launch {
            container.settings.setBaseUrl(base.trim())
            container.settings.setApiKey(key.trim())
            container.settings.setModel(Tab.CHAT, chat.trim())
            container.settings.setModel(Tab.REA, rea.trim())
            container.settings.setModel(Tab.UPDATE, update.trim())
            _ui.value = _ui.value.copy(saved = "Saved.")
        }
    }

    fun setLaneDefault(on: Boolean) {
        viewModelScope.launch { container.settings.setLaneDefault(on) }
    }

    fun setGhToken(token: String) {
        viewModelScope.launch { container.settings.setGhToken(token.trim()) }
    }

    fun doctor() {
        viewModelScope.launch { _ui.value = _ui.value.copy(doctor = container.install.doctor()) }
    }

    fun restore() {
        viewModelScope.launch {
            val note = container.install.restore()
            _ui.value = _ui.value.copy(restoreNote = note)
        }
    }

    fun repair() {
        viewModelScope.launch {
            container.install.ensure()
            container.install.housekeeping()
            _ui.value = _ui.value.copy(doctor = container.install.doctor(), restoreNote = "Repair ran.")
        }
    }

    fun clearNote() {
        _ui.value = _ui.value.copy(saved = null)
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container) as T
        }
    }
}

data class SettingsUi(
    val prefs: Prefs = Prefs(),
    val bridge: AnalystInstall.Bridge = AnalystInstall.Bridge.UNKNOWN,
    val saved: String? = null,
    val doctor: String? = null,
    val restoreNote: String? = null
)

@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = SettingsViewModel.factory(container))
    val ui by vm.ui.collectAsStateWithLifecycle()

    var base by remember(ui.prefs.baseUrl) { mutableStateOf(ui.prefs.baseUrl) }
    var key by remember(ui.prefs.apiKey) { mutableStateOf(ui.prefs.apiKey) }
    var mChat by remember(ui.prefs.modelChat) { mutableStateOf(ui.prefs.modelChat) }
    var mRea by remember(ui.prefs.modelRea) { mutableStateOf(ui.prefs.modelRea) }
    var mUpd by remember(ui.prefs.modelUpdate) { mutableStateOf(ui.prefs.modelUpdate) }
    var gh by remember(ui.prefs.ghToken) { mutableStateOf(ui.prefs.ghToken) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(value = base, onValueChange = { base = it }, label = { Text("Server base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        OutlinedTextField(value = mChat, onValueChange = { mChat = it }, label = { Text("Chat model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = mRea, onValueChange = { mRea = it }, label = { Text("REA model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = mUpd, onValueChange = { mUpd = it }, label = { Text("Update Arin model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Button(onClick = { vm.save(base, key, mChat, mRea, mUpd) }) { Text("Save model settings") }
        ui.saved?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
            scope.launch { }
        }

        HorizontalDivider()

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Talk to Claude by default")
                Text("New chats start with the line on. Existing chats keep their own switch.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = ui.prefs.laneDefault, onCheckedChange = { vm.setLaneDefault(it) })
        }

        OutlinedTextField(value = gh, onValueChange = { gh = it }, label = { Text("GitHub token") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(onClick = { vm.setGhToken(gh) }) { Text("Save GitHub token") }

        HorizontalDivider()

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Claude line", style = MaterialTheme.typography.titleLarge)
                Text(
                    when (ui.bridge) {
                        AnalystInstall.Bridge.UP -> "Service is up on port 8791."
                        AnalystInstall.Bridge.DOWN -> "Service is down — the tab answers alone until it is repaired."
                        AnalystInstall.Bridge.UNKNOWN -> "Not checked yet."
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.doctor() }) { Text("Check") }
                    OutlinedButton(onClick = { vm.repair() }) { Text("Repair") }
                    OutlinedButton(onClick = { vm.restore() }) { Text("Restore backup") }
                }
                ui.doctor?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                ui.restoreNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun LabeledSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
