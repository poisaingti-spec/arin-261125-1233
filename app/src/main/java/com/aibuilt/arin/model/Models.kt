package com.aibuilt.arin.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class Tab { CHAT, REA, UPDATE }

@Serializable
enum class AttachmentKind { IMAGE, VIDEO, OTHER }

@Serializable
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val mime: String,
    val kind: AttachmentKind,
    val size: Long
)

@Serializable
enum class MsgRole { USER, MODEL, ASK, ANSWER_HEADER, ANALYST, NOTE }

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: MsgRole,
    val text: String,
    val at: Long = System.currentTimeMillis(),
    val attachments: List<Attachment> = emptyList()
)

@Serializable
data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val tab: Tab = Tab.CHAT,
    val reaOn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val messages: List<Message> = emptyList()
)
