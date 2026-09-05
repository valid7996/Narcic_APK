package com.narcic.ng.aether.shared.core

import com.narcic.ng.aether.platform.PlatformContext

interface BinaryManager {
    fun prepareBinary(name: String = "aether"): String
}

// Single Android implementation (ported from AetherST KMP expect/actual).
fun getBinaryManager(context: PlatformContext): BinaryManager = AndroidBinaryManager(context)
