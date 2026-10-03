package com.narcic.ng.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.narcic.ng.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two-engine chain's JSON mechanics: where the carrier attaches on the
 * exit's dial chain and which carrier/exit pairs are refused. Mirrors the
 * shape of ZedSecure's CrossChainTest and CarrierProxyTest (AGPL-3.0).
 */
class CoreOutboundBuilderTest {

    private fun root(vararg outbounds: String, rules: String = """[{"outboundTag":"direct"}]"""): JsonObject =
        JsonParser.parseString(
            """{"outbounds":[${outbounds.joinToString(",")}],"routing":{"rules":$rules}}""",
        ).asJsonObject

    private val plainExit = """{"protocol":"vless","tag":"exit","settings":{}}"""

    private val fragmentExit = """{"protocol":"vless","tag":"exit","streamSettings":{"sockopt":{"dialerProxy":"fragment"}}}"""

    private val hopA = """{"protocol":"vless","tag":"a","streamSettings":{"sockopt":{"dialerProxy":"b"}}}"""

    private val hopB = """{"protocol":"vless","tag":"b","settings":{}}"""

    private fun dialerOf(json: JsonObject, tag: String): String? =
        json.getAsJsonArray("outbounds").map { it.asJsonObject }
            .firstOrNull { it.get("tag")?.asString == tag }
            ?.getAsJsonObject("streamSettings")?.getAsJsonObject("sockopt")
            ?.get("dialerProxy")?.takeIf { it.isJsonPrimitive }?.asString

    @Test
    fun `a plain exit gets the carrier on its first outbound`() {
        val json = root(plainExit)
        assertTrue(CoreOutboundBuilder.attachCarrierToRoot(json, "chain1"))
        assertEquals("chain1", dialerOf(json, "exit"))
    }

    @Test
    fun `a fragment dialer is replaced by the carrier and the fragment is dropped`() {
        val json = root(fragmentExit, """{"protocol":"freedom","tag":"fragment","settings":{}}""")
        assertTrue(CoreOutboundBuilder.attachCarrierToRoot(json, "chain1"))
        assertEquals("chain1", dialerOf(json, "exit"))
        val tags = json.getAsJsonArray("outbounds").map { it.asJsonObject.get("tag")?.asString }
        assertFalse("the fragment outbound must be gone", tags.contains("fragment"))
    }

    @Test
    fun `the walk lands on the root of a multi-hop dialer chain`() {
        val json = root(hopA, hopB)
        assertTrue(CoreOutboundBuilder.attachCarrierToRoot(json, "chain1"))
        assertEquals("the middle hop keeps its dialer", "b", dialerOf(json, "a"))
        assertEquals("the root dials through the carrier", "chain1", dialerOf(json, "b"))
    }

    @Test
    fun `a config without outbounds cannot take a carrier`() {
        val json = JsonParser.parseString("""{"outbounds":[]}""").asJsonObject
        assertFalse(CoreOutboundBuilder.attachCarrierToRoot(json, "chain1"))
    }

    @Test
    fun `the carrier inbound routes ahead of every other rule`() {
        val json = root(plainExit, rules = """[{"outboundTag":"proxy","domain":["geosite:google"]},{"outboundTag":"direct"}]""")
        CoreOutboundBuilder.addCarrierInbound(json, 40001, "chain1in", "chain1")
        val rules = json.getAsJsonObject("routing").getAsJsonArray("rules")
        assertEquals("chain1in", rules[0].asJsonObject.getAsJsonArray("inboundTag")[0].asString)
        assertEquals(3, rules.size())
        val inbound = json.getAsJsonArray("inbounds").first { it.asJsonObject.get("tag")?.asString == "chain1in" }.asJsonObject
        assertEquals("127.0.0.1", inbound.get("listen").asString)
        assertEquals(40001, inbound.get("port").asInt)
        assertTrue(inbound.getAsJsonObject("settings").get("udp").asBoolean)
    }

    @Test
    fun `the udp gate fires only for the aether carrier`() {
        assertEquals(
            "a wireguard exit cannot ride an aether carrier",
            "WIREGUARD",
            CoreOutboundBuilder.udpCarrierMismatch(EConfigType.AETHER, EConfigType.WIREGUARD, null),
        )
        assertEquals(
            "kcp transport is udp as well",
            "VLESS",
            CoreOutboundBuilder.udpCarrierMismatch(EConfigType.AETHER, EConfigType.VLESS, "kcp"),
        )
        assertNull("tcp exits are fine", CoreOutboundBuilder.udpCarrierMismatch(EConfigType.AETHER, EConfigType.VLESS, "tcp"))
        assertNull("an aether exit was refused earlier", CoreOutboundBuilder.udpCarrierMismatch(EConfigType.AETHER, EConfigType.AETHER, null))
        assertNull("xray-family carriers relay udp", CoreOutboundBuilder.udpCarrierMismatch(EConfigType.AMNEZIAWG, EConfigType.WIREGUARD, null))
        assertNull("xray-family carriers relay udp", CoreOutboundBuilder.udpCarrierMismatch(EConfigType.VLESS, EConfigType.HYSTERIA2, null))
    }
}
