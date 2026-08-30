package com.narcic.ng.core

/**
 * Minimal JNI owner for the embedded, rootless DPI-desync engine (ciadpi/byedpi, MIT licensed).
 *
 * The native side runs a local SOCKS proxy on 127.0.0.1. It is meant to be dialed through
 * via an outbound's `sockopt.dialerProxy`, not connected to directly by the user.
 */
class DesyncNativeBridge {
    companion object {
        init {
            System.loadLibrary("narcicdesync")
        }
    }

    @Volatile
    private var fd: Int = -1

    /**
     * Parses [args] (argv-style, e.g. `["--proto=tls", "--split", "1+s", "--ip", "127.0.0.1", "--port", "1080"]`)
     * and opens the listening socket. Throws if the command line is rejected.
     */
    @Synchronized
    fun prepare(args: Array<String>) {
        check(fd < 0) { "Desync engine is already prepared" }
        val socket = jniCreateSocketWithCommandLine(args)
        check(socket >= 0) { "Desync engine rejected the command line" }
        fd = socket
    }

    /** Blocks running the proxy's event loop. Call from a dedicated background thread. */
    fun runLoop(): Int {
        val socket = fd
        check(socket >= 0) { "Desync engine is not prepared" }
        return jniStartProxy(socket)
    }

    @Synchronized
    fun stop(): Int {
        val socket = fd
        if (socket < 0) return 0
        val result = jniStopProxy(socket)
        fd = -1
        return result
    }

    private external fun jniCreateSocketWithCommandLine(args: Array<String>): Int
    private external fun jniStartProxy(fd: Int): Int
    private external fun jniStopProxy(fd: Int): Int
}
