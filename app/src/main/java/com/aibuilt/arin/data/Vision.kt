package com.aibuilt.arin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.aibuilt.arin.model.Attachment
import com.aibuilt.arin.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Prepares images for a model that may or may not accept image blocks.
 * Path used is reported so the transcript can say which one happened.
 */
class Vision(private val context: Context, private val dir: File, private val settings: SettingsStore) {

    data class Prepared(
        val images: List<String>,
        val described: List<String>,
        val path: String
    )

    suspend fun prepare(model: String, items: List<Attachment>): Prepared = withContext(Dispatchers.IO) {
        val pics = items.filter { it.kind == AttachmentKind.IMAGE }.take(10)
        if (pics.isEmpty()) return@withContext Prepared(emptyList(), emptyList(), "none")

        val natives = mutableListOf<String>()
        val described = mutableListOf<String>()
        for (a in pics) {
            val shrunk = shrink(a) ?: continue
            natives += shrunk
        }

        val known = settings.capability(model)
        val accepts = when (known) {
            "yes" -> true
            "no" -> false
            else -> probed(model)
        }

        if (accepts) {
            return@withContext Prepared(natives, emptyList(), "sent natively as image blocks")
        }

        val text = pics.joinToString("\n") { a ->
            "Picture attached: ${a.name} at ${a.path} (${a.mime}, ${a.size} bytes). Open it with Bash/Read; it is not sent as an image block."
        }
        val descFile = File(dir, "image-description-${System.currentTimeMillis()}.txt")
        runCatching { descFile.writeText(text) }
        described += "/root/files/${descFile.name}"
        Prepared(emptyList(), described, "model takes no image blocks, written description injected instead")
    }

    /** Asks the provider once by sending a 1x1 png; remembers the answer. */
    private suspend fun probed(model: String): Boolean {
        val prefs = settings.snapshot()
        if (prefs.apiKey.isBlank()) return true
        val tiny = onePixel()
        val answered = runCatching {
            ApiClient().complete(
                baseUrl = prefs.baseUrl,
                apiKey = prefs.apiKey,
                model = model,
                system = "Reply with one word.",
                turns = listOf(ApiClient.Turn("user", "What colour is this?")),
                images = listOf(tiny)
            )
        }
        val yes = answered.isSuccess
        settings.setCapability(model, yes)
        return yes
    }

    private fun onePixel(): String {
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF3366CC.toInt())
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
        bmp.recycle()
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun shrink(a: Attachment): String? {
        val src = File(dir, a.path.substringAfterLast('/'))
        if (!src.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var scale = 1
        val long = maxOf(bounds.outWidth, bounds.outHeight)
        while (long / scale > 1600) scale *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = scale }
        val bmp = runCatching { BitmapFactory.decodeFile(src.absolutePath, opts) }.getOrNull() ?: return null
        val resized = fit(bmp)
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (resized !== bmp) resized.recycle()
        bmp.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun fit(bmp: Bitmap): Bitmap {
        val long = maxOf(bmp.width, bmp.height)
        if (long <= 1600) return bmp
        val ratio = 1600f / long
        return Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
    }
}
