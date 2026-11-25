package com.aibuilt.arin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient {

    data class Turn(val role: String, val text: String)

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun complete(
        baseUrl: String,
        apiKey: String,
        model: String,
        system: String,
        turns: List<Turn>,
        images: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw IOException("Add your API key in Settings first.")
        if (baseUrl.isBlank()) throw IOException("Add a base URL in Settings first.")

        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", system)
            })
            turns.forEachIndexed { index, turn ->
                val isLastUser = turn.role == "user" && turns.drop(index + 1).none { it.role == "user" }
                add(buildJsonObject {
                    put("role", turn.role)
                    if (isLastUser && images.isNotEmpty()) {
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", turn.text)
                            })
                            images.forEach { url ->
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject { put("url", url) })
                                })
                            }
                        })
                    } else {
                        put("content", turn.text)
                    }
                })
            }
        }

        val payload = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.4)
            put("stream", false)
        }

        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                throw IOException(message ?: "Model error ${response.code}")
            }
            val content = runCatching {
                json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.first()
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            }.getOrNull()
            content?.takeIf { it.isNotBlank() } ?: throw IOException("The model returned an empty answer.")
        }
    }
}
