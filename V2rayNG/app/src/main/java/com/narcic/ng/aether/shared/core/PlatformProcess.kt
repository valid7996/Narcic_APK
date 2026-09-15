package com.narcic.ng.aether.shared.core

// Single Android implementation (ported from AetherST KMP expect/actual).
class PlatformProcess {
    suspend fun start(command: List<String>, directory: String, env: Map<String, String>): Boolean
    suspend fun readLine(): String?
    suspend fun writeLine(line: String)
    fun waitFor(): Int
    fun destroy()
}
