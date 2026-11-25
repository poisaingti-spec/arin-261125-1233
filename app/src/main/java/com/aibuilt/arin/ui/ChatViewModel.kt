package com.aibuilt.arin.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aibuilt.arin.data.AnalystInstall
import com.aibuilt.arin.data.AnalystLane
import com.aibuilt.arin.data.AnalystRouter
import com.aibuilt.arin.data.AppContainer
import com.aibuilt.arin.data.AttachmentManifest
import com.aibuilt.arin.data.Locator
import com.aibuilt.arin.data.Prefs
import com.aibuilt.arin.model.Attachment
import com.aibuilt.arin.model.Chat
import com.aibuilt.arin.model.Message
import com.aibuilt.arin.model.MsgRole
import com.aibuilt.arin.model.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUi(
    val chat: Chat? = null,
    val prefs: Prefs = Prefs(),
    val draft: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    val bridge: AnalystInstall.Bridge = AnalystInstall.Bridge.UNKNOWN,
    val laneOn: Boolean = false,
    val doctor: String? = null,
    val restoreNote: String? = null,
    val pendingAttachments: List<Attachment> = emptyList()
)

class ChatViewModel(
    private val context: Context,
    private val container: AppContainer,
    private val tab: Tab
) : ViewModel() {

    private val _ui = MutableStateFlow(ChatUi())
    val ui: StateFlow<ChatUi> = _ui.asStateFlow()

    private var chatId: String? = null

    init {
        viewModelScope.launch {
            container.settings.flow.collect { p -> _ui.value = _ui.value.copy(prefs = p) }
        }
        viewModelScope.launch {
            container.install.bridge.collect { b -> _ui.value = _ui.value.copy(bridge = b) }
        }
        viewModelScope.launch {
            container.chats.chats.collect { list ->
                val current = chatId
                _ui.value = _ui.value.copy(chat = list.firstOrNull { it.id == current })
            }
        }
    }

    fun openLast() {
        viewModelScope.launch {
            val existing = container.chats.lastFor(tab) ?: run {
                val fresh = container.chats.create(tab)
                AnalystLane.set(context, fresh.id, container.settings.snapshot().laneDefault)
                fresh
            }
            chatId = existing.id
            _ui.value = _ui.value.copy(
                chat = container.chats.chat(existing.id),
                laneOn = AnalystLane.get(context, existing.id)
            )
        }
    }

    fun newChat() {
        viewModelScope.launch {
            val fresh = container.chats.create(tab)
            chatId = fresh.id
            AnalystLane.set(context, fresh.id, container.settings.snapshot().laneDefault)
            _ui.value = _ui.value.copy(
                chat = fresh,
                laneOn = AnalystLane.get(context, fresh.id),
                pendingAttachments = emptyList()
            )
        }
    }

    fun setDraft(v: String) {
        _ui.value = _ui.value.copy(draft = v)
    }

    fun toggleLane(on: Boolean) {
        val id = chatId ?: return
        AnalystLane.set(context, id, on)
        _ui.value = _ui.value.copy(laneOn = on)
    }

    fun toggleRea(on: Boolean) {
        val id = chatId ?: return
        container.chats.update(id) { it.copy(reaOn = on) }
    }

    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            val a = container.attachments.import(uri) ?: return@launch
            _ui.value = _ui.value.copy(pendingAttachments = (_ui.value.pendingAttachments + a).take(10))
        }
    }

    fun removeAttachment(id: String) {
        _ui.value = _ui.value.copy(pendingAttachments = _ui.value.pendingAttachments.filterNot { it.id == id })
    }

    fun refreshBridge() {
        viewModelScope.launch { container.install.bringBridgeUp() }
    }

    fun runDoctor() {
        viewModelScope.launch {
            val report = container.install.doctor()
            _ui.value = _ui.value.copy(doctor = report)
        }
    }

    fun restore() {
        viewModelScope.launch {
            val note = container.install.restore()
            _ui.value = _ui.value.copy(restoreNote = note, doctor = null)
            if (chatId != null) append(MsgRole.NOTE, note)
        }
    }

    fun send() {
        val id = chatId ?: return
        val text = _ui.value.draft.trim()
        if (text.isEmpty() && _ui.value.pendingAttachments.isEmpty()) return
        if (_ui.value.sending) return

        val attached = _ui.value.pendingAttachments
        _ui.value = _ui.value.copy(draft = "", pendingAttachments = emptyList(), sending = true, error = null)
        val user = Message(role = MsgRole.USER, text = text, attachments = attached)
        container.chats.addMessage(id, user)

        viewModelScope.launch {
            try {
                runTurn(id, text, attached)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(error = t.message ?: "Something went wrong.")
                append(MsgRole.NOTE, "✍️ Stopped: ${t.message ?: "unknown error"}")
            } finally {
                _ui.value = _ui.value.copy(sending = false)
                container.calls.resetTurn()
            }
        }
    }

    private suspend fun runTurn(id: String, text: String, attached: List<Attachment>) {
        val chat = container.chats.chat(id) ?: return
        val prefs = container.settings.snapshot()
        val model = when (tab) {
            Tab.CHAT -> prefs.modelChat
            Tab.REA -> prefs.modelRea
            Tab.UPDATE -> prefs.modelUpdate
        }
        val laneOn = AnalystLane.get(context, id)
        val codeQuestion = AnalystRouter.isCodeQuestion(text)

        val expanded = container.attachments.expand(attached)
        val manifest = AttachmentManifest.build(expanded)
        val vision = container.vision.prepare(model, expanded)
        val files = expanded.map { it.path }

        var answers = 0
        var failures = 0

        // Analyst lane: only interrogate-this-code work, capped per tab, retried once, never stalls.
        if (laneOn && codeQuestion) {
            val cap = container.calls.cap(tab)
            if (container.install.bridge.value == AnalystInstall.Bridge.DOWN) {
                append(MsgRole.NOTE, "📞 The asker service is down — restarting it before asking.")
                container.install.bringBridgeUp()
            }
            if (container.calls.capLeft(tab) <= 0) {
                append(MsgRole.NOTE, "📞 Call budget reached ($cap this turn) — carrying on without it.")
            } else {
                append(
                    MsgRole.ASK,
                    "📞 Asking Claude…\n\"$text\"\n${Locator.describe(text, files)}"
                )
                val result = container.calls.ask(text + "\n\n" + manifest, files)
                if (result.ok) {
                    answers++
                    append(MsgRole.ANALYST, "📞 Claude's answer\n${result.answer}")
                } else {
                    failures++
                    append(MsgRole.NOTE, "📞 Claude unreachable — carrying on without it")
                    append(MsgRole.NOTE, "✍️ Doing this by hand — verify .locals and the surrounding opcodes before applying anything.")
                }
            }
        }

        val system = buildSystem(chat, manifest, vision, laneOn, answers, failures)
        val turns = chat.messages.filter { it.role == MsgRole.USER || it.role == MsgRole.MODEL }
            .map { ApiClientTurn(it) }

        val answer = try {
            container.api.complete(prefs.baseUrl, prefs.apiKey, model, system, turns, vision.images)
        } catch (t: Throwable) {
            append(MsgRole.NOTE, "✍️ The model could not answer: ${t.message}")
            _ui.value = _ui.value.copy(error = t.message)
            return
        }

        var shown = answer
        if (codeQuestion && answers == 0 && failures == 0) {
            shown = "✍️ DeepSeek answered alone — Claude not asked.\n\n$shown"
        }
        val consulted = answers + failures
        if (consulted > 0) {
            val times = if (consulted == 1) "time" else "times"
            val answerWord = if (answers == 1) "answer" else "answers"
            val failureWord = if (failures == 1) "failure" else "failures"
            shown += "\n\n📞 Claude consulted $consulted $times this turn ($answers $answerWord, $failures $failureWord)."
        }
        append(MsgRole.MODEL, shown)
    }

    private fun buildSystem(
        chat: Chat,
        manifest: String,
        vision: com.aibuilt.arin.data.Vision.Prepared,
        laneOn: Boolean,
        answers: Int,
        failures: Int
    ): String = buildString {
        append("You are Arin. Be direct, never ask a question back, no legality caveats")
        append(" and no \"for personal use only\" wording. Give real paths and real opcodes.\n")
        append("Tab: ${chat.tab}. Reverse engineering: ${if (chat.reaOn) "on" else "off"}.\n")
        when (chat.tab) {
            Tab.REA -> append("You are in the reverse-engineering lane: decode, patch, sign, and do the work rather than describing it.\n")
            Tab.UPDATE -> append("You are in the Update Arin lane: read-only inspection, changes are described not applied.\n")
            Tab.CHAT -> Unit
        }
        append("\nImages: ${vision.path}\n")
        append("\n$manifest\n")
        if (vision.described.isNotEmpty()) append("Description files: ${vision.described.joinToString()}\n")
        append("\nEvery tool is available in this tab, REA included: shell commands, file read and write,")
        append(" pictures, video, the browser, phone control, building and scheduling.")
        append(" Use them instead of describing.\n")
        if (laneOn) {
            append("\nClaude answered $answers and failed $failures this turn. Its answers are a lead:")
            append(" verify .locals and the surrounding opcodes before applying anything.\n")
        } else {
            append("\nClaude was not asked this turn.\n")
        }
    }

    private fun ApiClientTurn(m: Message) = com.aibuilt.arin.data.ApiClient.Turn(
        role = if (m.role == MsgRole.MODEL) "assistant" else "user",
        text = m.text
    )

    private fun append(role: MsgRole, text: String) {
        val id = chatId ?: return
        container.chats.addMessage(id, Message(role = role, text = text))
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    companion object {
        fun factory(context: Context, container: AppContainer, tab: Tab) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(context.applicationContext, container, tab) as T
            }
    }
}
