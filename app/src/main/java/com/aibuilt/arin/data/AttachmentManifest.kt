=== ABOUT: Writes out every picture and video attached this turn with its real path inside Linux, so any tab can open it ===
```kotlin
package com.aibuilt.arin.data

import com.aibuilt.arin.model.Attachment
import com.aibuilt.arin.model.AttachmentKind

object AttachmentManifest {

    fun build(items: List<Attachment>): String {
        if (items.isEmpty()) return "No files attached this turn."
        val lines = StringBuilder()
        lines.append("Files attached this turn (real paths inside Linux, open them with Bash/Read):\n")
        items.forEachIndexed { index, a ->
            val kind = when (a.kind) {
                AttachmentKind.IMAGE -> "image"
                AttachmentKind.VIDEO -> "video"
                AttachmentKind.OTHER -> "other"
            }
            lines.append("${index + 1}. $kind \t ${a.path} \t ${a.mime} \t ${a.size} bytes\n")
        }
        lines.append("Never guess a path - use the ones above.")
        return lines.toString()
    }
}
