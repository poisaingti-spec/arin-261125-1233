package com.aibuilt.arin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aibuilt.arin.data.AnalystInstall
import com.aibuilt.arin.data.AppContainer
import com.aibuilt.arin.model.AttachmentKind
import com.aibuilt.arin.model.Message
import com.aibuilt.arin.model.MsgRole
import com.aibuilt.arin.model.Tab
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatScreen(container: AppContainer, tab: Tab) {
    val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(LocalContext(), container, tab))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(tab) { vm.openLast() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.addAttachment(uri)
    }

    LaunchedEffect(ui.chat?.messages?.size) {
        val n = ui.chat?.messages?.size ?: 0
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = when (tab) {
                Tab.CHAT -> ui.chat?.title ?: "Chat"
                Tab.REA -> "Reverse engineering"
                Tab.UPDATE -> "Update Arin"
            },
            laneOn = ui.laneOn,
            bridge = ui.bridge,
            onNew = { vm.newChat() },
            menuOpen = { menu = true }
        )

        menuAnchor@{}

        Box(Modifier.weight(1f)) {
            if (ui.chat == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Starting…")
                }
            } else if (ui.chat?.messages.isNullOrEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when (tab) {
                            Tab.REA -> "Paste smali or an APK question. Turn on Talk to Claude in the ⋮ menu and Claude is asked first."
                            Tab.UPDATE -> "Describe the change. Talk to Claude in ⋮ makes Claude read the code first (3 calls per turn)."
                            Tab.CHAT -> "Ask anything. ⋮ holds the Claude switch."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ui.chat?.messages ?: emptyList(), key = { it.id }) { m -> MessageRow(m) }
                }
            }
            if (ui.sending) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }

        ui.error?.let { err ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(err, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (ui.pendingAttachments.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.pendingAttachments.forEach { a ->
                    AssistChip(
                        onClick = { vm.removeAttachment(a.id) },
                        label = { Text(if (a.kind == AttachmentKind.IMAGE) "🖼 ${a.name}" else "🎞 ${a.name}") }
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { picker.launch("*/*") }) { Icon(Icons.Filled.AttachFile, "attach") }
            OutlinedTextField(
                value = ui.draft,
                onValueChange = vm::setDraft,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                maxLines = 6
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { vm.send() }, enabled = !ui.sending) {
                Icon(Icons.Filled.Send, "send")
            }
        }

        Box {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Chat info") },
                    onClick = { menu = false }
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    laneOn: Boolean,
    bridge: AnalystInstall.Bridge,
    onNew: () -> Unit,
    menuOpen: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            if (bridge == AnalystInstall.Bridge.DOWN) {
                Text("line down", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = onNew) { Icon(Icons.Filled.Add, "new chat") }
            IconButton(onClick = menuOpen) { Icon(Icons.Filled.MoreVert, "menu") }
        }
    }
    HorizontalDivider()
}

@Composable
private fun MessageRow(m: Message) {
    val (bg, align) = when (m.role) {
        MsgRole.USER -> MaterialTheme.colorScheme.primaryContainer to Alignment.End
        MsgRole.MODEL -> MaterialTheme.colorScheme.surfaceVariant to Alignment.Start
        MsgRole.ANALYST -> MaterialTheme.colorScheme.tertiaryContainer to Alignment.Start
        MsgRole.ASK -> MaterialTheme.colorScheme.secondaryContainer to Alignment.Start
        else -> MaterialTheme.colorScheme.surface to Alignment.Start
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Surface(color = bg, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    m.text,
                    style = if (m.role == MsgRole.ANALYST || m.role == MsgRole.MODEL)
                        MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default)
                    else MaterialTheme.typography.bodyMedium
                )
                m.attachments.forEach { a ->
                    Text("📎 ${a.path}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun LocalContext(): android.content.Context = androidx.compose.ui.platform.LocalContext.current

@Composable
fun ChatMenuItems(
    laneOn: Boolean,
    onLaneChange: (Boolean) -> Unit
) {
    // Row that sits directly under "Activate reverse engineering" in the ⋮ menu.
    DropdownMenuItem(
        text = { Text("Talk to Claude") },
        trailingIcon = {
            Switch(checked = laneOn, onCheckedChange = onLaneChange)
        },
        onClick = { onLaneChange(!laneOn) }
    )
}
