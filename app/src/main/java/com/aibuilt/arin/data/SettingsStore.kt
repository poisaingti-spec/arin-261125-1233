package com.aibuilt.arin.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aibuilt.arin.model.Tab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("arin_settings")

data class Prefs(
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val modelChat: String = "deepseek-chat",
    val modelRea: String = "deepseek-chat",
    val modelUpdate: String = "deepseek-chat",
    val laneDefault: Boolean = false,
    val ghToken: String = ""
)

class SettingsStore(private val context: Context) {

    private object K {
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val modelChat = stringPreferencesKey("model_chat")
        val modelRea = stringPreferencesKey("model_rea")
        val modelUpdate = stringPreferencesKey("model_update")
        val laneDefault = booleanPreferencesKey("lane_default")
        val ghToken = stringPreferencesKey("gh_token")
        fun cap(model: String) = stringPreferencesKey("caps:" + model)
    }

    val flow: Flow<Prefs> = context.dataStore.data.map { p ->
        val base = p[K.baseUrl]
        Prefs(
            baseUrl = base ?: "https://api.deepseek.com/v1",
            apiKey = p[K.apiKey] ?: "",
            modelChat = p[K.modelChat] ?: "deepseek-chat",
            modelRea = p[K.modelRea] ?: "deepseek-chat",
            modelUpdate = p[K.modelUpdate] ?: "deepseek-chat",
            laneDefault = p[K.laneDefault] ?: false,
            ghToken = p[K.ghToken] ?: ""
        )
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun snapshot(): Prefs = flow.first()

    suspend fun setBaseUrl(v: String) = edit { it[K.baseUrl] = v }
    suspend fun setApiKey(v: String) = edit { it[K.apiKey] = v }
    suspend fun setGhToken(v: String) = edit { it[K.ghToken] = v }
    suspend fun setLaneDefault(v: Boolean) = edit { it[K.laneDefault] = v }

    suspend fun setModel(tab: Tab, v: String) = edit {
        when (tab) {
            Tab.CHAT -> it[K.modelChat] = v
            Tab.REA -> it[K.modelRea] = v
            Tab.UPDATE -> it[K.modelUpdate] = v
        }
    }

    suspend fun model(tab: Tab): String = when (tab) {
        Tab.CHAT -> snapshot().modelChat
        Tab.REA -> snapshot().modelRea
        Tab.UPDATE -> snapshot().modelUpdate
    }

    suspend fun capability(model: String): String? = context.dataStore.data.first()[K.cap(model)]
    suspend fun setCapability(model: String, yes: Boolean) = edit { it[K.cap(model)] = if (yes) "yes" else "no" }
}
