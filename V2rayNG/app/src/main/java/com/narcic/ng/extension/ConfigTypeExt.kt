package com.narcic.ng.extension

import com.narcic.ng.enums.EConfigType

/**
 * Human-friendly protocol label for the home "اتصال" row subtitle and the
 * connection picker's type filter (e.g. "Shadowsocks", "VLESS", "Trojan").
 */
fun EConfigType.displayLabel(): String = when (this) {
    EConfigType.VMESS -> "VMess"
    EConfigType.VLESS -> "VLESS"
    EConfigType.SHADOWSOCKS -> "Shadowsocks"
    EConfigType.SOCKS -> "SOCKS"
    EConfigType.HTTP -> "HTTP"
    EConfigType.TROJAN -> "Trojan"
    EConfigType.WIREGUARD -> "WireGuard"
    EConfigType.HYSTERIA2 -> "Hysteria2"
    EConfigType.AETHER -> "Aether"
    EConfigType.CROSS_CHAIN -> "Chain"
    EConfigType.CUSTOM -> "Custom"
    else -> name
}
