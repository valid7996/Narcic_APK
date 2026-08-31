package com.narcic.ng.awg

import android.content.Context
import android.net.VpnService
import android.util.Log
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.atomic.AtomicBoolean

object AwgManager {

    private const val TAG = "AwgManager"
    private const val TUNNEL_NAME = "narcic-awg"
    private const val DISCONNECT_SETTLE_MS = 600L
    private const val SETTLE_POLL_INTERVAL_MS = 50L
    private val CONNECT_RETRY_DELAYS = longArrayOf(200L, 400L, 800L)

    private var backend: GoBackend? = null
    private var currentTunnel: SimpleTunnel? = null
    private val isTransitioning = AtomicBoolean(false)

    fun isAmneziaWgConfig(rawText: String): Boolean {
        if (!rawText.contains("[Interface]")) return false
        return Regex("(?m)^\s*(Jc|Jmin|Jmax|H1|H2|H3|H4|I1|I2|I3|I4|I5)\s*=").containsMatchIn(rawText)
    }

    fun sanitizeConfigText(rawText: String): String {
        return rawText
            .removePrefix("﻿")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { line ->
                line
                    .replace("​", "").replace("‌", "")
                    .replace("‍", "").replace("⁠", "")
                    .trim()
            }
            .trim()
    }

    fun preload(context: Context) {
        try {
            ensureBackend(context)
        } catch (e: Exception) {
            Log.w(TAG, "AmneziaWG backend preload failed, will retry on demand", e)
        }
    }

    private fun ensureBackend(context: Context): GoBackend {
        return backend ?: GoBackend(context.applicationContext).also { backend = it }
    }

    fun connect(context: Context, rawConfigText: String): String? {
        if (!isTransitioning.compareAndSet(false, true)) {
            val deadline = System.currentTimeMillis() + DISCONNECT_SETTLE_MS
            while (isTransitioning.get() && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(SETTLE_POLL_INTERVAL_MS) } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt(); return "Connect interrupted"
                }
            }
            if (!isTransitioning.compareAndSet(false, true)) {
                return "AmneziaWG: another transition is still in progress"
            }
        }
        return try {
            connectInternal(context, rawConfigText)
        } finally {
            isTransitioning.set(false)
        }
    }

    private fun connectInternal(context: Context, rawConfigText: String): String? {
        val cleanedText = sanitizeConfigText(rawConfigText)
        val config = try {
            Config.parse(BufferedReader(StringReader(cleanedText)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AmneziaWG config", e)
            return e.message ?: "AmneziaWG config is invalid"
        }

        val activeTunnel = currentTunnel
        if (activeTunnel != null) {
            try {
                Log.i(TAG, "AmneziaWG: bringing existing tunnel down before reconnect")
                backend?.setState(activeTunnel, Tunnel.State.DOWN, null)
                currentTunnel = null
                waitForTunnelDown(activeTunnel)
            } catch (e: Exception) {
                Log.w(TAG, "AmneziaWG: error while bringing tunnel down before reconnect", e)
            }
        }

        var lastError: String? = null
        for ((index, retryDelay) in CONNECT_RETRY_DELAYS.withIndex()) {
            try {
                startTunnel(context, config)
                Log.i(TAG, "AmneziaWG: tunnel UP on attempt ${index + 1}")
                return null
            } catch (e: Exception) {
                lastError = e.message ?: "AmneziaWG connect failed"
                Log.w(TAG, "AmneziaWG connect attempt ${index + 1} failed: $lastError", e)
                try { Thread.sleep(retryDelay) } catch (e2: InterruptedException) {
                    Thread.currentThread().interrupt(); return "Connect interrupted"
                }
            }
        }

        Log.e(TAG, "AmneziaWG: all connect attempts exhausted. Last error: $lastError")
        return lastError ?: "AmneziaWG connect failed"
    }

    private fun waitForTunnelDown(tunnel: SimpleTunnel) {
        val deadline = System.currentTimeMillis() + DISCONNECT_SETTLE_MS
        while (System.currentTimeMillis() < deadline) {
            val state = try {
                backend?.getState(tunnel)
            } catch (e: Exception) {
                null
            }
            if (state == Tunnel.State.DOWN || state == null) {
                Log.d(TAG, "AmneziaWG: tunnel settled DOWN")
                return
            }
            try { Thread.sleep(SETTLE_POLL_INTERVAL_MS) } catch (e: InterruptedException) {
                Thread.currentThread().interrupt(); return
            }
        }
        Log.w(TAG, "AmneziaWG: tunnel did not settle DOWN within ${DISCONNECT_SETTLE_MS}ms")
    }

    private fun startTunnel(context: Context, config: Config): String? {
        val goBackend = ensureBackend(context)
        val tunnel = SimpleTunnel(TUNNEL_NAME)
        currentTunnel = tunnel
        goBackend.setState(tunnel, Tunnel.State.UP, config)
        return null
    }

    fun disconnect(): String? {
        if (!isTransitioning.compareAndSet(false, true)) {
            val deadline = System.currentTimeMillis() + DISCONNECT_SETTLE_MS
            while (isTransitioning.get() && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(SETTLE_POLL_INTERVAL_MS) } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt(); return "Disconnect interrupted"
                }
            }
            if (!isTransitioning.compareAndSet(false, true)) {
                return "AmneziaWG: transition still in progress, cannot disconnect now"
            }
        }
        return try {
            disconnectInternal()
        } finally {
            isTransitioning.set(false)
        }
    }

    private fun disconnectInternal(): String? {
        val goBackend = backend ?: return null
        val tunnel = currentTunnel ?: return null
        return try {
            goBackend.setState(tunnel, Tunnel.State.DOWN, null)
            currentTunnel = null
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop AmneziaWG tunnel", e)
            e.message ?: "AmneziaWG disconnect failed"
        }
    }

    fun isRunning(): Boolean {
        val goBackend = backend ?: return false
        val tunnel = currentTunnel ?: return false
        return try {
            goBackend.getState(tunnel) == Tunnel.State.UP
        } catch (e: Exception) {
            false
        }
    }

    fun getStatistics(): org.amnezia.awg.backend.Statistics? {
        val goBackend = backend ?: return null
        val tunnel = currentTunnel ?: return null
        return try {
            goBackend.getStatistics(tunnel)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read AmneziaWG statistics", e)
            null
        }
    }

    fun prepare(context: Context) = VpnService.prepare(context)

    private class SimpleTunnel(private val name: String) : Tunnel {
        override fun getName(): String = name
        override fun onStateChange(newState: Tunnel.State) {
            Log.i(TAG, "AmneziaWG tunnel state changed: $newState")
        }
    }
}
