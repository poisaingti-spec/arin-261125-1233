=== ABOUT: Installs and repairs the Claude asker and its little service, keeps a backup and can restore it, all off the main thread ===
```kotlin
package com.aibuilt.arin.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AnalystInstall(private val context: Context) {

    enum class Bridge { UNKNOWN, UP, DOWN }

    private val _bridge = MutableStateFlow(Bridge.UNKNOWN)
    val bridge: StateFlow<Bridge> = _bridge.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val mcpPatch: String =
        """const fs=require('fs');const p='/root/.arin/cc-mcp.json';let j={mcpServers:{}};
try{j=JSON.parse(fs.readFileSync(p,'utf8'))}catch(e){}
j.mcpServers=j.mcpServers||{};
j.mcpServers.analyst={type:'http',url:'http://127.0.0.1:8791/mcp'};
fs.writeFileSync(p,JSON.stringify(j,null,2));
"""

    private val profilePatch: String =
        """const fs=require('fs');const p='/root/.claude/settings.json';let j={};
try{j=JSON.parse(fs.readFileSync(p,'utf8'))}catch(e){}
j.toolCatalogProfile='hot-cold';
fs.writeFileSync(p,JSON.stringify(j,null,2));
"""

    suspend fun ensure() = withContext(Dispatchers.IO) {
        Linux.run("mkdir -p /root/.arin /root/.claude/skills/arin-analyst /root/.arin/skills/arin-analyst /root/.arin/pkg/skills/arin-analyst /root/.arin/skills/arin-rea")
        put("/root/.arin/analyst.sh", Scripts.analystScript, true)
        put("/root/.arin/analyst-mcp.mjs", Scripts.mcpServer, false)
        put("/root/.arin/mcp-bridge.mjs", Scripts.bridge, false)
        put("/root/.arin/analyst-mcp-up.sh", Scripts.upScript, true)
        put("/root/.claude/skills/arin-analyst/SKILL.md", Scripts.skill, false)
        put("/root/.arin/skills/arin-analyst/SKILL.md", Scripts.skill, false)
        put("/root/.arin/pkg/skills/arin-analyst/SKILL.md", Scripts.skill, false)
        put("/root/.arin/skills/arin-rea/SKILL.md", Scripts.skill, false)
        registerMcp()
        setProfile()
        appendClaudeMd()
        bringBridgeUp()
        Unit
    }

    suspend fun bringBridgeUp() = withContext(Dispatchers.IO) {
        val out = Linux.run("sh /root/.arin/analyst-mcp-up.sh", 40)
        if (out.ok && out.stdout.contains("bridge up")) {
            _bridge.value = Bridge.UP
            _lastError.value = null
        } else {
            _bridge.value = Bridge.DOWN
            _lastError.value = listOf(out.stdout, out.stderr).joinToString(" ").trim()
                .ifBlank { "The asker service is not running." }
        }
        _bridge.value == Bridge.UP
    }

    suspend fun restore(): String = withContext(Dispatchers.IO) {
        if (!exists("/root/.arin/cc-mcp.json.bak")) return@withContext "No backup yet - nothing to restore."
        Linux.run("cp -f /root/.arin/cc-mcp.json.bak /root/.arin/cc-mcp.json")
        bringBridgeUp()
        "Restored the previous service config."
    }

    suspend fun doctor(): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        report.append(if (exists("/root/.arin/analyst.sh")) "asker present\n" else "asker MISSING\n")
        report.append(if (Linux.run("bash -n /root/.arin/analyst.sh").ok) "asker syntax ok\n" else "asker syntax BAD\n")
        report.append(if (Linux.run("command -v claude").ok) "claude found\n" else "claude MISSING\n")
        report.append(if (Linux.run("command -v node").ok) "node found\n" else "node MISSING\n")
        val health = Linux.run("curl -fsS http://127.0.0.1:8791/health", 15)
        report.append(if (health.ok) "bridge up on 8791\n" else "bridge DOWN\n")
        val same = Linux.run("cmp -s /root/.arin/skills/arin-analyst/SKILL.md /root/.arin/skills/arin-rea/SKILL.md").ok
        report.append(if (same) "skills identical\n" else "skill stale - copies differ\n")
        report.append(if (exists("/root/.arin/cc-mcp.json.bak")) "backup present\n" else "no backup\n")
        report.toString().trim()
    }

    suspend fun housekeeping() = withContext(Dispatchers.IO) {
        Linux.run("bash -n /root/.arin/analyst.sh")
        Linux.run("chmod 755 /root/.arin/analyst.sh /root/.arin/analyst-mcp-up.sh")
        bringBridgeUp()
        Unit
    }

    private suspend fun registerMcp() {
        if (!exists("/root/.arin/cc-mcp.json")) {
            put("/root/.arin/cc-mcp.json", "{\n  \"mcpServers\": {}\n}\n", false)
        }
        Linux.run("cp -f /root/.arin/cc-mcp.json /root/.arin/cc-mcp.json.bak")
        put("/root/.arin/.mcp-patch.js", mcpPatch, false)
        Linux.run("node /root/.arin/.mcp-patch.js")
    }

    private suspend fun setProfile() {
        put("/root/.arin/.profile-patch.js", profilePatch, false)
        Linux.run("mkdir -p /root/.claude && node /root/.arin/.profile-patch.js")
    }

    private suspend fun appendClaudeMd() {
        Linux.run("mkdir -p /root/.claude && touch /root/.claude/CLAUDE.md")
        if (Linux.run("grep -q 'arin-analyst:begin' /root/.claude/CLAUDE.md").ok) return
        put("/root/.arin/.claude-block.md", Scripts.claudeMdBlock, false)
        Linux.run("cat /root/.arin/.claude-block.md >> /root/.claude/CLAUDE.md")
    }

    private suspend fun exists(path: String): Boolean = Linux.run("test -e '$path'").ok

    private suspend fun put(path: String, content: String, exec: Boolean) {
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val dir = path.substringBeforeLast('/')
        val cmd = buildString {
            append("mkdir -p '$dir' && printf %s '$b64' | base64 -d > '$path'")
            if (exec) append(" && chmod 755 '$path'")
        }
        Linux.run(cmd)
    }
}
