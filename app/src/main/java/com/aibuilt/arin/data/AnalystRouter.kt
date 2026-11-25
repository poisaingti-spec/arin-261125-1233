package com.aibuilt.arin.data

object AnalystRouter {

    private val codey = listOf(
        "what does", "what is this method", "what does this verify", "verif", "check lives",
        "where is", "where does", "what breaks", "if i change", "patch", "hook point", "hook ",
        "grep", "opcode", "smali", ".locals", "register", "method", "field", "class ", "signature",
        "const/", "invoke-", "iget", "iput", "sget", "sput", "line ", "diff", "decompile", "getter", "setter"
    )

    private val notCode = listOf(
        "apktool d", "jadx", "sign the apk", "re-sign", "list the files", "install it",
        "logcat", "build error", "gradle error", "unzip", "test the app", "run the app"
    )

    fun isCodeQuestion(text: String): Boolean {
        val t = text.lowercase()
        if (t.isBlank()) return false
        if (notCode.any { t.contains(it) } && !t.contains("verify")) return false
        return codey.any { t.contains(it) }
    }

    fun hedged(answer: String): Boolean {
        val a = answer.lowercase()
        return listOf("i can't", "unfortunately", "note that", "for personal use only", "this may vary")
            .any { a.contains(it) }
    }
}
