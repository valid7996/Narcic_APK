package com.narcic.ng.aether.core

import android.content.Context
import android.net.VpnService
import com.narcic.ng.aether.platform.PlatformContext
import com.narcic.ng.aether.platform.getSettings
import com.narcic.ng.aether.shared.data.AetherConfigRepository
import com.narcic.ng.aether.shared.data.LogRepository
import com.narcic.ng.aether.shared.model.AetherConfig
import java.io.File
import java.lang.ref.WeakReference
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicLong

object PsiphonController {
    @Volatile private var psiphonPort: Int = 3080
    @Volatile private var running = false
    @Volatile private var connected = false
    @Volatile private var tunnelObj: Any? = null
    private val lastEventTime = AtomicLong(0L)
    @Volatile private var vpnServiceRef: WeakReference<VpnService>? = null

    fun setVpnService(service: VpnService) {
        vpnServiceRef = WeakReference(service)
    }

    /**
     * Releases the VpnService reference without a full stop() — used by
     * AetherVpnService.onDestroy() so a dead service is never pinned and the
     * Psiphon state stays strictly Aether-scoped.
     */
    fun clearVpnService() {
        vpnServiceRef?.clear()
        vpnServiceRef = null
    }

    fun isSupported(config: AetherConfig): Boolean {
        return config.psiphonEnabled
    }

    fun getUpstreamProxy(): String {
        return "socks5://127.0.0.1:$psiphonPort"
    }

    private fun findFreePort(): Int {
        return try {
            ServerSocket(0).use { it.localPort }
        } catch (_: Exception) { 3080 }
    }

    fun start(context: Context, config: AetherConfig, upstream: String? = null): Boolean {
        if (!isSupported(config)) return false
        return try {
            val dir = File(context.filesDir, "psiphon")
            if (!dir.exists()) dir.mkdirs()
            val requested = config.psiphonSocksPort.toIntOrNull() ?: 3080
            val port = if (requested.toString() == config.socksPort) findFreePort() else requested
            psiphonPort = port
            try {
                // ca.psiphon classes + their gomobile libgojni.so. NOTE: the
                // packaging keeps only ONE libgojni (Xray's); the Psiphon AAR
                // is stripped of its own in CI. loadLibrary below will throw
                // UnsatisfiedLinkError (an Error, not Exception) there — catch
                // Throwable so the chain degrades with a clear log line
                // instead of crashing the app.
                val clazz = Class.forName("ca.psiphon.PsiphonTunnel")
                val hostServiceClass = Class.forName("ca.psiphon.PsiphonTunnel\$HostService")
                val hostService = java.lang.reflect.Proxy.newProxyInstance(
                    hostServiceClass.classLoader,
                    arrayOf(hostServiceClass, Class.forName("ca.psiphon.PsiphonTunnel\$HostLogger"), Class.forName("ca.psiphon.PsiphonTunnel\$HostLibraryLoader"))
                ) { _, method, args ->
                    when (method.name) {
                        "getContext" -> context
                        "getPsiphonConfig" -> buildPsiphonConfig(context, port, config.psiphonEgressRegion, upstream)
                        "onListeningSocksProxyPort" -> {
                            val p = args?.get(0) as? Int ?: port
                            psiphonPort = p
                            LogRepository.i("Psiphon listening on $p", "Psiphon")
                            null
                        }
                        "onConnected" -> {
                            connected = true
                            lastEventTime.set(System.currentTimeMillis())
                            LogRepository.i("Psiphon connected", "Psiphon")
                            null
                        }
                        "onConnecting" -> {
                            lastEventTime.set(System.currentTimeMillis())
                            LogRepository.i("Psiphon connecting", "Psiphon")
                            null
                        }
                        "onAvailableEgressRegions" -> {
                            val list = (args?.get(0) as? List<*>)?.mapNotNull { it?.toString()?.trim()?.uppercase() }?.filter { it.matches(Regex("^[A-Z]{2}$")) } ?: emptyList()
                            // Append-only: the runtime notice lists the regions the
                            // CURRENT server-entry set can egress through today —
                            // it must never replace (shrink) the canonical country
                            // list. cacheEgressRegions unions the reported regions
                            // with DEFAULT_EGRESS_REGIONS + persisted cache and
                            // republishes the full set to PsiphonEgressRegistry, so
                            // every country (and its name/flag) stays visible even
                            // when the bundled server_entries lack it.
                            runCatching {
                                AetherConfigRepository.getInstance(getSettings(PlatformContext(context))).cacheEgressRegions(list)
                            }.onFailure {
                                LogRepository.w("Failed to persist egress regions: ${it.message}", "Psiphon")
                            }
                            LogRepository.i("Psiphon available egress regions: ${list.joinToString()}", "Psiphon")
                            null
                        }
                        "bindToDevice" -> {
                            val fd = (args?.get(0) as? Long)?.toInt() ?: (args?.get(0) as? Int) ?: -1
                            val ok = if (fd > 0) {
                                vpnServiceRef?.get()?.protect(fd) ?: run {
                                    LogRepository.e("Psiphon bindToDevice: no VpnService available to protect fd=$fd", "Psiphon")
                                    false
                                }
                            } else {
                                LogRepository.e("Psiphon bindToDevice: invalid fd=$fd", "Psiphon")
                                false
                            }
                            if (!ok) LogRepository.e("Psiphon bindToDevice: protect(fd=$fd) failed; routing loop may persist", "Psiphon")
                            null
                        }
                        else -> null
                    }
                }
                val newTunnel = clazz.getMethod("newPsiphonTunnel", hostServiceClass).invoke(null, hostService)
                tunnelObj = newTunnel
                val serverEntries = try { context.assets.open("server_entries.txt").bufferedReader().readText().trim() } catch (_: Exception) { "" }
                val startMethod = clazz.getMethod("startTunneling", String::class.java)
                startMethod.invoke(newTunnel, serverEntries)
                val setVpnMode = try { clazz.getMethod("setVpnMode", Boolean::class.javaPrimitiveType) } catch (_: Exception) { null }
                setVpnMode?.invoke(newTunnel, false)
                running = true
                LogRepository.i("Psiphon started on 127.0.0.1:$psiphonPort", "Psiphon")
                true
            } catch (e: Throwable) {
                // Throwable (not Exception): a stripped/missing libgojni throws
                // UnsatisfiedLinkError — degrade cleanly, never crash.
                val reason = e.message ?: e.javaClass.simpleName
                LogRepository.w("Psiphon library not found or start failed ($reason), psiphon disabled", "Psiphon")
                running = false
                connected = false
                false
            }
        } catch (e: Throwable) {
            val reason = e.message ?: e.javaClass.simpleName
            LogRepository.e("Psiphon start exception: $reason", "Psiphon")
            false
        }
    }

    private fun buildPsiphonConfig(context: Context, port: Int, egressRegion: String = "", upstream: String? = null): String {
        val dir = File(context.filesDir, "psiphon")
        return try {
            val json = org.json.JSONObject()
            json.put("PropagationChannelId", "FFFFFFFFFFFFFFFF")
            json.put("SponsorId", "1111111111111111")
            json.put("EgressRegion", egressRegion)
            json.put("EstablishTunnelTimeoutSeconds", 120)
            json.put("DataRootDirectory", dir.absolutePath)
            json.put("ClientVersion", "1")
            json.put("TunnelProtocol", "")
            json.put("RemoteServerListURL", "")
            json.put("LocalSocksProxyPort", port)
            if (!upstream.isNullOrEmpty()) json.put("UpstreamProxyURL", upstream)
            json.put("RemoteServerListSignaturePublicKey", "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42KcotLFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7GstZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1OgeF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xalKxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM=")
            json.put("ServerEntrySignaturePublicKey", "sHuUVTWaRyh5pZwy4UguSgkwmBe0EHtJJkoF5WrxmvA=")
            json.put("ExchangeObfuscationKey", "DpXzloJk1Hw6aSzmKKky0xcahsEHubch81Mi6K0XMlU=")
            json.put("EmitBytesTransferred", true)
            json.put("DeviceRegion", "IR")
            json.put("ConnectionWorkerPoolSize", 12)
            json.toString()
        } catch (_: Exception) {
            val safeDir = dir.absolutePath.replace("\\", "\\\\")
            "{\"LocalSocksProxyPort\":$port,\"DataRootDirectory\":\"$safeDir\"}"
        }
    }

    fun stop() {
        if (!running) return
        try {
            tunnelObj?.let {
                try {
                    val m = it.javaClass.getMethod("stop")
                    m.invoke(it)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        tunnelObj = null
        running = false
        connected = false
        vpnServiceRef?.clear()
        vpnServiceRef = null
        LogRepository.i("Psiphon stopped", "Psiphon")
    }

    /**
     * Bounded single-tunnel region probe for the WG + Psiphon Chain fast-path
     * (Smart Reconnect). Starts Psiphon pinned to [egressRegion] (EgressRegion
     * override on a copy of [baseConfig]), requires onConnected within
     * [connectTimeoutMs] plus a short stability window of [stableMs], then
     * ALWAYS stops the tunnel before returning — winner or loser.
     *
     * The psi runtime is a single-tunnel singleton: callers MUST enforce
     * concurrency = 1. The guard below refuses to stack a second tunnel
     * instead of racing them.
     *
     * stop() clears the host VpnService WeakReference; it is restored right
     * after so the real chain start following this probe keeps bindToDevice()
     * working.
     */
    fun probeRegion(
        context: Context,
        baseConfig: AetherConfig,
        egressRegion: String,
        connectTimeoutMs: Long = 8000L,
        stableMs: Long = 2000L,
    ): Boolean {
        if (running) return false
        val host = vpnServiceRef?.get()
        var started = false
        val probe = Thread {
            started = try {
                start(context, baseConfig.copy(psiphonEgressRegion = egressRegion), upstream = null)
            } catch (_: Throwable) {
                false
            }
        }
        probe.isDaemon = true
        probe.name = "psiphon-probe-$egressRegion"
        probe.start()
        probe.join(connectTimeoutMs)
        var healthy = false
        if (!probe.isAlive) {
            healthy = started && isConnected()
            if (healthy) {
                val deadline = System.currentTimeMillis() + stableMs
                while (System.currentTimeMillis() < deadline) {
                    if (!isConnected()) { healthy = false; break }
                    Thread.sleep(200L)
                }
                healthy = healthy && stableFor(stableMs)
            }
        }
        val stopper = Thread {
            runCatching { stop() }
            runCatching { host?.let { setVpnService(it) } }
        }
        stopper.isDaemon = true
        stopper.name = "psiphon-probe-stop"
        stopper.start()
        stopper.join(3000L)
        Thread.sleep(200L) // let the psi runtime fully release before next start
        return healthy
    }

    fun isRunning(): Boolean {
        if (!running) return false
        return try {
            tunnelObj?.let {
                try {
                    val m = it.javaClass.getMethod("isRunning")
                    val r = m.invoke(it)
                    if (r is Boolean) return r
                } catch (_: Exception) {}
            }
            running
        } catch (_: Exception) { running }
    }

    fun isConnected(): Boolean = connected && running

    fun stableFor(graceMs: Long): Boolean = connected && running &&
            (System.currentTimeMillis() - lastEventTime.get()) >= graceMs
}
