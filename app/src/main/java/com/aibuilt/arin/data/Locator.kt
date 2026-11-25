package com.aibuilt.arin.data

object Locator {

    private val pattern = Regex("([\\w./-]+\\.(?:smali|kt|java|xml|js|mjs|json|gradle|kts|txt))(?::(\\d+))?")
    private val range = Regex("(\\d{1,6})\\s*(?:-|–|to)\\s*(\\d{1,6})")

    /** Builds "file:line" or "file:from-to" for display next to the question. */
    fun describe(question: String, files: List<String>): String {
        val hits = pattern.findAll(question).mapNotNull { m ->
            val file = m.groupValues[1]
            val line = m.groupValues[2]
            if (file.isBlank()) null else if (line.isBlank()) file else "$file:$line"
        }.toMutableList()
        if (hits.isEmpty()) hits += files.map { "$it:?" }
        if (hits.isEmpty()) return "(no file named)"
        val r = range.find(question)
        val first = hits.first()
        if (r != null && !first.contains(":")) {
            return "$first:${r.groupValues[1]}-${r.groupValues[2]}"
        }
        return hits.take(3).joinToString(", ")
    }
}
