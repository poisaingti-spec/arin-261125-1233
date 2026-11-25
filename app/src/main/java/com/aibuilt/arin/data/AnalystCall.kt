package com.aibuilt.arin.data

import com.aibuilt.arin.model.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class AnalystCall(private val dir: File) {

    data class Result(
        val ok: Boolean,
        val answer: String,
        val seconds: Long,
        val call: Int,
        val nonce: String
    )

    private val counter = AtomicInteger(0)
    private val used = AtomicInteger(0)
    private val hex = "0123456789abcdef"

    fun resetTurn() {
        counter.set(0)
        used.set(0)
    }

    fun usedThisTurn(): Int = used.get()

    fun cap(tab: Tab): Int = if (tab == Tab.UPDATE) 3 else 6

    fun capLeft(tab: Tab): Int = (cap(tab) - used.get()).coerceAtLeast(0)

    suspend fun ask(question: String, rootPaths: List<String>): Result = withContext(Dispatchers.IO) {
        used.incrementAndGet()
        var last = Result(false, "", 0, counter.get(), "")
        var attempt = 0
        while (attempt < 2) {
            attempt++
            last = once(question, rootPaths)
            if (last.ok) break
        }
        last
    }

    private fun once(question: String, rootPaths: List<String>): Result {
        if (!Linux.run("test -x /root/.arin/analyst.sh").ok) {
            return Result(false, "The asker is not installed.", 0, counter.get(), "")
        }
        val n = counter.incrementAndGet()
        val nonce = buildString { repeat(5) { append(hex[Random.nextInt(16)]) } }
        dir.mkdirs()
        val qFile = File(dir, "q-$nonce.txt")
        val args = StringBuilder("-q /root/files/${qFile.name}")
        rootPaths.take(3).forEach { p ->
            val name = p.substringAfterLast('/')
            if (name.isNotBlank()) args.append(" -f /root/files/").append(name)
        }
        runCatching { qFile.writeText(question) }
        val start = System.currentTimeMillis()
        val out = Linux.run("sh /root/.arin/analyst.sh $args", 330)
        val seconds = (System.currentTimeMillis() - start) / 1000
        val text = out.stdout.trim()
        val ok = out.code == 0 && text.contains("Claude's answer")
        val answer = if (ok) {
            text.lines().drop(1).joinToString("\n").trim()
        } else {
            text.ifBlank { out.stderr.trim() }
        }
        runCatching { qFile.delete() }
        return Result(ok, answer, seconds, n, nonce)
    }
}
