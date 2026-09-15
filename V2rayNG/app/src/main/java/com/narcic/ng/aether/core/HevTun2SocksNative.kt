package com.narcic.ng.aether.core

import androidx.annotation.Keep
import com.narcic.ng.aether.shared.data.LogRepository

@Keep
object HevTun2SocksNative {
    private val loaded: Boolean
    private val failure: Throwable?

    init {
        var nativeLoaded = false
        var nativeFailure: Throwable? = null
        try {
            System.loadLibrary("hev-tun2socks-jni")
            nativeLoaded = true
        } catch (throwable: Throwable) {
            nativeFailure = throwable
            LogRepository.e("[Hev] Native library load failed: ${throwable.localizedMessage}")
        }
        loaded = nativeLoaded
        failure = nativeFailure
    }

    val isAvailable: Boolean
        get() = loaded

    // NOTE: nativePause/nativeResume/nativeUpdateUpstream were declared here
    // in upstream AetherST but have NO implementation in hev_tun2socks_jni.c
    // (only nativeStart/Stop/GetStats/GetVersion are registered). Declaring
    // them as `external` caused UnsatisfiedLinkError at call time — the
    // engine-side helpers below are the intentional no-op replacements.
    external fun nativeStart(configStr: String, tunFd: Int): Int
    external fun nativeStop()
    external fun nativeGetStats(): LongArray?
    external fun nativeGetVersion(): Int

    fun nativePause() {
        // Not implemented natively; state handled in HevTun2SocksEngine.
    }

    fun nativeResume() {
        // Not implemented natively; state handled in HevTun2SocksEngine.
    }

    fun nativeUpdateUpstream(@Suppress("UNUSED_PARAMETER") host: String, @Suppress("UNUSED_PARAMETER") port: Int) {
        // Not implemented natively; upstream changes require a full restart
        // via nativeStop + nativeStart (handled by the connection controller).
    }
}
