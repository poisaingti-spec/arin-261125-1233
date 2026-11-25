=== ABOUT: Cuts a video into a few pictures and its sound track so the model can "watch" it ===
```kotlin
package com.aibuilt.arin.data

import com.aibuilt.arin.model.Attachment
import com.aibuilt.arin.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoFrames(private val dir: File) {

    suspend fun extract(video: Attachment): List<Attachment> = withContext(Dispatchers.IO) {
        if (!Linux.run("command -v ffmpeg").ok) return@withContext listOf(video)
        val base = video.path.substringAfterLast('/').substringBeforeLast('.')
        val out = mutableListOf<Attachment>()

        Linux.run("ffmpeg -y -i '${video.path}' -vf \"select='gt(scene,0.35)',scale=1280:-2\" -vsync vfr -frames:v 5 /root/files/$base-s%02d.jpg", 240)
        Linux.run("ffmpeg -y -i '${video.path}' -vf \"fps=1/4,scale=1280:-2\" -frames:v 4 /root/files/$base-t%02d.jpg", 240)

        val found = Linux.run("ls -1 /root/files/$base-s*.jpg /root/files/$base-t*.jpg 2>/dev/null")
            .stdout.lines().filter { it.isNotBlank() }.take(8)
        found.forEach { p ->
            out += Attachment(
                name = p.substringAfterLast('/'),
                path = p,
                mime = "image/jpeg",
                kind = AttachmentKind.IMAGE,
                size = File(dir, p.substringAfterLast('/')).length()
            )
        }

        val audio = Linux.run("ffmpeg -y -i '${video.path}' -vn -ac 1 -ar 16000 /root/files/$base.m4a", 240)
        if (audio.ok) {
            out += Attachment(
                name = "$base.m4a",
                path = "/root/files/$base.m4a",
                mime = "audio/mp4",
                kind = AttachmentKind.OTHER,
                size = File(dir, "$base.m4a").length()
            )
        }
        if (Linux.run("command -v whisper").ok) {
            val tr = Linux.run("whisper '/root/files/$base.m4a' --output_format txt --output_dir /root/files", 280)
            if (tr.ok) {
                out += Attachment(
                    name = "$base-transcript.txt",
                    path = "/root/files/$base-transcript.txt",
                    mime = "text/plain",
                    kind = AttachmentKind.OTHER,
                    size = File(dir, "$base-transcript.txt").length()
                )
            }
        }
        if (out.isEmpty()) listOf(video) else out
    }
}
