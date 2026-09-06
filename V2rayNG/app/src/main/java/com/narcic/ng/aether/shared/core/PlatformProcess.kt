package com.narcic.ng.aether.shared.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

// Single Android implementation (ported from AetherST KMP expect/actual).
class PlatformProcess {
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    suspend fun start(command: List<String>, directory: String, env: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(command)
            pb.directory(File(directory))
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            process = proc
            reader = BufferedReader(InputStreamReader(proc.inputStream))
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun readLine(): String? = withContext(Dispatchers.IO) {
        try { reader?.readLine() } catch (e: Exception) { null }
    }

    suspend fun writeLine(line: String): Unit = withContext(Dispatchers.IO) {
        try {
            writer?.write(line)
            writer?.newLine()
            writer?.flush()
        } catch (_: Exception) {}
    }

    fun waitFor(): Int = process?.waitFor() ?: -1
    fun destroy() {
        process?.destroy()
    }
}
