package com.example.tieba.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

class TiebaBridge {
    private val LOG = Logger.getInstance(TiebaBridge::class.java)
    private val gson = Gson()

    private var process: Process? = null
    private var stdinWriter: java.io.OutputStreamWriter? = null
    private var stdoutReader: BufferedReader? = null

    private val requestCounter = AtomicLong(0)
    private val pendingRequests = ConcurrentHashMap<Long, CompletableFuture<String>>()

    @Volatile
    private var isRunning = false

    fun start(project: Project) {
        if (isRunning) return

        val pythonPath = findPython()
        if (pythonPath == null) {
            LOG.error("Python not found in PATH")
            return
        }

        val scriptPath = getBridgeScriptPath()
        if (scriptPath == null || !java.io.File(scriptPath).exists()) {
            LOG.error("Bridge script not found at: $scriptPath")
            return
        }

        try {
            val builder = ProcessBuilder(pythonPath, scriptPath)
            builder.redirectErrorStream(false)
            builder.redirectError(ProcessBuilder.Redirect.PIPE)
            process = builder.start()
            stdinWriter = java.io.OutputStreamWriter(process!!.outputStream, StandardCharsets.UTF_8)
            stdoutReader = BufferedReader(InputStreamReader(process!!.inputStream, StandardCharsets.UTF_8))

            isRunning = true
            LOG.info("Tieba bridge started with Python: $pythonPath")

            Thread({ readStderr() }, "TiebaBridge-Stderr").apply {
                isDaemon = true
                start()
            }

            Thread({ readLoop() }, "TiebaBridge-Reader").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            LOG.error("Failed to start bridge", e)
        }
    }

    private fun readStderr() {
        try {
            val errReader = BufferedReader(InputStreamReader(process!!.errorStream, StandardCharsets.UTF_8))
            var line = errReader.readLine()
            while (line != null && isRunning) {
                if (line.trim().isNotEmpty()) {
                    LOG.warn("[Python stderr] $line")
                }
                line = errReader.readLine()
            }
        } catch (e: Exception) {
            if (isRunning) {
                LOG.error("Bridge stderr reader error", e)
            }
        }
    }

    private fun readLoop() {
        try {
            stdoutReader?.use { reader ->
                var line = reader.readLine()
                while (line != null && isRunning) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        if (!trimmed.startsWith("{")) {
                            LOG.warn("Non-JSON output from bridge: $trimmed")
                            val errorJson = """{"error":"Unexpected output: $trimmed"}"""
                            pendingRequests.values.forEach { it.complete(errorJson) }
                            pendingRequests.clear()
                            continue
                        }
                        try {
                            val obj = JsonParser.parseString(trimmed).asJsonObject
                            val reqId = obj.get("req_id")?.asLong
                            LOG.info("Received response #$reqId: $trimmed")
                            if (reqId != null) {
                                pendingRequests.remove(reqId)?.complete(trimmed)
                            } else {
                                LOG.warn("Response without req_id: $trimmed")
                            }
                        } catch (e: Exception) {
                            LOG.warn("Failed to parse response: $trimmed", e)
                            val errorJson = """{"error":"Parse error: ${e.message}"}"""
                            pendingRequests.values.forEach { it.complete(errorJson) }
                            pendingRequests.clear()
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                LOG.error("Bridge reader error", e)
            }
        } finally {
            isRunning = false
        }
    }

    fun sendRequest(request: Map<String, Any?>): CompletableFuture<String> {
        if (!isRunning || process == null || process?.isAlive != true) {
            LOG.warn("sendRequest called but bridge not running")
            return CompletableFuture.completedFuture("""{"error":"Bridge not running"}""")
        }

        val reqId = requestCounter.incrementAndGet()
        val fullRequest = request + mapOf("req_id" to reqId)
        val json = gson.toJson(fullRequest)
        LOG.info("Sending request #$reqId: $json")

        val future = CompletableFuture<String>()
        pendingRequests[reqId] = future

        try {
            stdinWriter?.apply {
                write(json)
                write("\n")
                flush()
            } ?: run {
                future.complete("""{"error":"stdin not available"}""")
            }
        } catch (e: Exception) {
            pendingRequests.remove(reqId)
            LOG.error("Failed to send request #$reqId", e)
            future.complete("""{"error":"${e.message}"}""")
        }

        return future
    }

    fun stop() {
        isRunning = false
        try {
            stdinWriter?.close()
        } catch (_: Exception) {}
        try {
            stdoutReader?.close()
        } catch (_: Exception) {}
        process?.destroyForcibly()
        process = null
        LOG.info("Tieba bridge stopped")
    }

    private fun findPython(): String? {
        val os = System.getProperty("os.name").lowercase()
        val candidates = if (os.contains("win")) {
            listOf("python", "python3", "py")
        } else {
            listOf("python3", "python")
        }
        for (candidate in candidates) {
            try {
                val proc = ProcessBuilder(candidate, "--version").start()
                val output = BufferedReader(InputStreamReader(proc.inputStream, StandardCharsets.UTF_8)).readLine()
                proc.waitFor()
                if (output != null && output.startsWith("Python")) {
                    LOG.info("Found Python: $candidate ($output)")
                    return candidate
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun getBridgeScriptPath(): String? {
        val scriptResource = "python-bridge/aiotieba_bridge.py"

        val input = TiebaBridge::class.java.classLoader?.getResourceAsStream(scriptResource)
        if (input != null) {
            try {
                val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "tieba-plugin")
                tempDir.mkdirs()
                val tempScript = java.io.File(tempDir, "aiotieba_bridge.py")
                input.copyTo(tempScript.outputStream())
                LOG.info("Bridge script extracted to: ${tempScript.absolutePath}")
                return tempScript.absolutePath
            } catch (e: Exception) {
                LOG.error("Failed to extract bridge script", e)
            } finally {
                input.close()
            }
        }

        val cwd = System.getProperty("user.dir")
        val script = java.io.File(cwd, "python-bridge/aiotieba_bridge.py")
        if (script.exists()) return script.absolutePath

        return null
    }
}
