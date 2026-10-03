package com.narcic.ng.fmt

import com.narcic.ng.util.Utils
import java.net.URI
import java.util.Base64

/**
 * A Narcic Chain link carries a whole two-engine chain in one string, so the set can be shared as
 * plain links and imported from the clipboard like any other config. The AmneziaWG config text of
 * the CARRIER (the first hop, member ۱) is embedded base64url-encoded, and the EXIT is named by its
 * Psiphon region:
 *
 *   narcicchain://chain?conf=<b64url conf>&region=DE#Narcic Chain 🇩🇪
 *   narcicchain://direct?conf=<b64url conf>#Narcic AMWG 🇮🇷
 *
 * A link without a region is the plain AmneziaWG config on its own (no chain). Where the members
 * are found or created once and shared between the links is the importer's business — this object
 * only reads the link.
 */
object NarcicChainFmt : FmtBase() {

    /** One parsed link: [confText] is the carrier's raw `[Interface]`/`[Peer]` text, [region] the Psiphon exit or null for a plain config. */
    data class Parsed(val name: String, val confText: String, val region: String?)

    private val regionShape = Regex("^[A-Za-z]{2}$")

    fun parse(str: String): Parsed? {
        return try {
            val uri = URI(Utils.fixIllegalUrl(str.trim()))
            if (uri.rawQuery.isNullOrEmpty()) return null
            val queryParam = getQueryParam(uri)

            val packed = queryParam["conf"]?.trim().orEmpty()
            if (packed.isEmpty()) return null
            val confText = String(Base64.getUrlDecoder().decode(pad(packed)), Charsets.UTF_8)
            if (!confText.contains("[Interface]", ignoreCase = true) ||
                !confText.contains("PrivateKey", ignoreCase = true)
            ) {
                return null
            }

            val region = queryParam["region"]?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            if (region != null && !regionShape.matches(region)) return null

            val name = Utils.decodeURIComponent(uri.fragment.orEmpty()).trim()
            Parsed(
                name = name.ifEmpty { if (region == null) "Narcic AMWG" else "Narcic Chain" },
                confText = confText,
                region = region,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** The link's conf is written unpadded; the decoder wants the padding back. */
    private fun pad(text: String): String {
        val remainder = text.length % 4
        return text + "=".repeat((4 - remainder) % 4)
    }

    /** The conf text of [parsed] as the link carries it, base64url without padding. */
    fun packConf(confText: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(confText.toByteArray(Charsets.UTF_8))
}
