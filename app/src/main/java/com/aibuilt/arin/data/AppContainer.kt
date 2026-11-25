package com.aibuilt.arin.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class AppContainer(private val context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val baseDir: File = File(context.filesDir, "linux").apply { mkdirs() }
    val filesDir: File = File(baseDir, "files").apply { mkdirs() }

    val settings = SettingsStore(context)
    val chats = ChatRepository(File(context.filesDir, "store"))
    val attachments = Attachments(context, filesDir)
    val vision = Vision(context, filesDir, settings)
    val calls = AnalystCall(filesDir)
    val install = AnalystInstall(context)

    val api = ApiClient()

    /** Binds /root/files to the app folder and stands the asker service up on start. */
    fun boot() {
        Linux.bindDir = filesDir
        scope.launch {
            runCatching { install.ensure() }
            runCatching { install.housekeeping() }
            runCatching { install.doctor() }
        }
    }

    fun lastError(): String? = install.lastError.value
}
