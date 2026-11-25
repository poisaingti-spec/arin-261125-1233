package com.aibuilt.arin.data

import android.content.Context

object AnalystLane {

    private const val PREFS = "arin_analyst_lane"

    fun get(context: Context, chatId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(chatId, false)

    fun set(context: Context, chatId: String, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(chatId, on).apply()
    }

    fun forget(context: Context, chatId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(chatId).apply()
    }
}
