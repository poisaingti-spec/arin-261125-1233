package com.aibuilt.arin.data

import com.aibuilt.arin.model.Chat
import com.aibuilt.arin.model.Message
import com.aibuilt.arin.model.MsgRole
import com.aibuilt.arin.model.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

class ChatRepository(private val dir: File) {

    private val file = File(dir, "chats.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    init {
        dir.mkdirs()
        runCatching {
            if (file.exists()) _chats.value = json.decodeFromString<List<Chat>>(file.readText())
        }
    }

    private fun persist() {
        runCatching { file.writeText(json.encodeToString(_chats.value)) }
    }

    fun create(tab: Tab): Chat {
        val chat = Chat(tab = tab, title = when (tab) {
            Tab.CHAT -> "New chat"
            Tab.REA -> "Reverse engineering"
            Tab.UPDATE -> "Update Arin"
        })
        _chats.value = listOf(chat) + _chats.value
        persist()
        return chat
    }

    fun chat(id: String): Chat? = _chats.value.firstOrNull { it.id == id }

    fun update(id: String, change: (Chat) -> Chat) {
        _chats.value = _chats.value.map { if (it.id == id) change(it) else it }
        persist()
    }

    fun addMessage(id: String, message: Message) {
        update(id) { it.copy(messages = it.messages + message, title = titleFor(it, message)) }
    }

    private fun titleFor(chat: Chat, message: Message): String {
        if (chat.messages.isNotEmpty() || message.role != MsgRole.USER) return chat.title
        val base = message.text.trim().ifEmpty { message.attachments.firstOrNull()?.name ?: return chat.title }
        return base.take(42)
    }

    fun delete(id: String) {
        _chats.value = _chats.value.filterNot { it.id == id }
        persist()
    }

    fun lastFor(tab: Tab): Chat? = _chats.value.firstOrNull { it.tab == tab }
}
