package com.aibuilt.arin.data

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs shell commands. When a PRoot binary is present the command runs inside the
 * Linux root with the app's files folder mounted at /root/files, so attachments have
 * a real path every tab can open.
 */
object Linux {

    @Volatile
    var bindDir: File? = null

    @Volatile
    private var cachedProot: String? = null
    @Volatile
    private var probed = false

    data class Out(val code: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = code == 0
    }

    private fun prootPath(): String? {
        if (probed) return cachedProot
        synchronized(this) {
            if (probed) return cachedProot
            probed = true
            val candidates = listOf(
                "/usr/bin/proot", "/usr/local/bin/proot", "/system/bin/proot"
            )
            cachedProot = candidates.firstOrNull { File(it).exists() }
            if (cachedProot == null) {
                val r = raw(listOf("sh", "-c", "command -v proot"), 10)
                val p = r.stdout.trim().lines().firstOrNull()?.trim()
                cachedProot = if (!p.isNullOrEmpty() && File(p).exists()) p else null
            }
            return cachedProot
        }
    }

    fun available(): Boolean = prootPath() != null

    fun run(cmd: String, timeoutSec: Long = 120): Out {
        val pr = prootPath() ?: return raw(listOf("sh", "-c", cmd), timeoutSec)
        val argv = mutableListOf(
            pr, "-0", "-r", "/",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-w", "/root"
        )
        bindDir?.let { d ->
            d.mkdirs()
            argv += listOf("-b", "${d.absolutePath}:/root/files")
        }
        argv += listOf(
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=dumb",
            "sh", "-c", cmd
        )
        return raw(argv, timeoutSec)
    }

    private fun raw(argv: List<String>, timeoutSec: Long): Out {
        var process: Process? = null
        return try {
            val p = ProcessBuilder(argv).start()
            process = p
            var out = ""
            var err = ""
            val t1 = Thread { runCatching { out = p.inputStream.bufferedReader().readText() } }
            val t2 = Thread { runCatching { err = p.errorStream.bufferedReader().readText() } }
            t1.start(); t2.start()
            val done = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!done) p.destroyForcibly()
            t1.join(2500); t2.join(2500)
            Out(if (done) runCatching { p.exitValue() }.getOrDefault(1) else 124, out, err)
        } catch (t: Throwable) {
            runCatching { process?.destroyForcibly() }
            Out(127, "", t.message ?: t::class.java.simpleName)
        }
    }
}
