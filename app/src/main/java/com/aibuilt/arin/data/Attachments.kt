package com.aibuilt.arin.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aibuilt.arin.model.Attachment
import com.aibuilt.arin.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class Attachments(private val context: Context, private val dir: File) {

    suspend fun import(uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val original = displayName(uri) ?: "file-${System.currentTimeMillis()}"
        val safe = original.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val unique = "${System.currentTimeMillis().toString().takeLast(5)}-$safe"
        val out = File(dir, unique)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }.getOrNull()
        if (copied == null) return@withContext null
        val kind = when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            mime.startsWith("video/") -> AttachmentKind.VIDEO
            else -> AttachmentKind.OTHER
        }
        Attachment(name = safe, path = "/root/files/$unique", mime = mime, kind = kind, size = out.length())
    }

    suspend fun expand(items: List<Attachment>): List<Attachment> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Attachment>()
        for (a in items) {
            if (a.kind == AttachmentKind.VIDEO) out += VideoFrames(dir).extract(a) else out += a
        }
        out.take(30)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}
