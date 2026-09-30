package com.narcic.ng.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.enums.AetherProtocol
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import com.narcic.ng.ui.server.AETHER_EXIT_COUNTRIES
import com.narcic.ng.ui.server.AetherEditorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The 4-box quick-setup dashboard of the اتر page — and it IS the config.
 *
 *   ┌ کادر ۱: انتخاب مسیر ──────────────────────────────┐
 *   │ وایرگارد · WARP-in-WARP · MASQUE · فقط سایفون · فقط تور │
 *   └───────────────────────────────────────────────────┘
 *   ┌ کادر ۲: زنجیره ──────────────────────────────────┐
 *   │ مسیر warp     → سایفون ✓ تور ✓ (+ تنظیمات هرکدام)   │
 *   │ فقط سایفون    → فقط تنظیمات سایفون                  │
 *   │ فقط تور       → فقط تنظیمات تور                     │
 *   └───────────────────────────────────────────────────┘
 *   ┌ کادر ۳: اتصال ───────────────────────────────────┐
 *   │ خودکار (اسکن واقعی هنگام اتصال — لاگ زنده)          │
 *   │ دستی  → آدرس/پورت + اسکن + کلید جدید + scan/obf/ip  │
 *   └───────────────────────────────────────────────────┘
 *   ┌ کادر ۴: لاگ ─────────────────────────────────────┘
 *
 * Single source of truth: the backing profile in MMKV. Every value shown
 * here is decoded straight from storage and every change is written straight
 * back (decode → mutate → encode → re-decode), so the dashboard, the full
 * editor and the connect flow always agree. Returning to the page (or the
 * app) re-decodes, which is why settings survive; edits made in the full
 * editor appear here on resume.
 */
@Composable
fun AetherQuickSetupCard(
    guid: String,
    isBlocked: Boolean,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
    val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
    val cardBg = if (isDark) Color.White.copy(alpha = .05f) else Color.White.copy(alpha = .92f)
    val stroke = if (isDark) Color.White.copy(alpha = .12f) else Color(0x1A1B2230)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AetherEditorRepository(context) }

    // Revision counter: bumped after every write and on lifecycle resume so
    // the profile below re-decodes from MMKV (single source of truth).
    var rev by remember { mutableIntStateOf(0) }
    val profile = remember(guid, rev) { MmkvManager.decodeServerConfig(guid) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, guid) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) rev++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val logLines = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    fun log(line: String) {
        logLines.add(line)
        if (logLines.size > 60) logLines.removeRange(0, logLines.size - 60)
    }

    if (profile == null) {
        Text(
            "در حال آماده‌سازی کانفیگ Aether…",
            color = txtSub, fontSize = 10.5.sp,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )
        return
    }

    fun mutate(block: (ProfileItem) -> Unit) {
        MmkvManager.decodeServerConfig(guid)?.let { p ->
            block(p)
            MmkvManager.encodeServerConfig(guid, p)
        }
        rev++
    }

    // All values read straight from the freshly decoded profile.
    val protocol = profile.aetherProtocol ?: "masque"
    val psiphon = profile.aetherPsiphon ?: "off"
    val tor = profile.aetherTor ?: "off"
    val psiphonMode = profile.aetherPsiphonMode ?: "auto"
    val region = profile.aetherPsiphonRegion ?: ""
    val cdnIps = profile.aetherPsiphonCdnIps ?: ""
    val torBridges = profile.aetherTorBridges ?: "auto"
    val torRelays = profile.aetherTorRelays ?: "auto"
    val bridgeLines = profile.aetherTorBridgeLines ?: ""
    val scanMode = profile.aetherScanMode ?: "balanced"
    val obfuscation = profile.aetherObfuscation ?: "auto"
    val ipVersion = profile.aetherIpVersion ?: "v4"
    val address = profile.server ?: ""
    val port = profile.serverPort ?: ""
    val outer = profile.aetherWiwOuter ?: ""
    val inner = profile.aetherWiwInner ?: ""

    val twoHops = protocol == "gool" || protocol == "mim"
    val onlyPsiphon = psiphon == "only"
    val onlyTor = tor == "only"
    val box1 = when {
        onlyPsiphon -> "p_only"
        onlyTor -> "t_only"
        protocol == "wg" -> "wg"
        protocol == "gool" || protocol == "mim" -> "wiw"
        else -> "masque"
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(20.dp))
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("راه‌اندازی سریع Aether", color = txtMain, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text(
                "تنظیمات کامل ‹",
                color = Nc.BadgeAether, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onOpenEditor),
            )
        }

        // ════ کادر ۱: انتخاب مسیر ════
        SectionLabel("۱ · انتخاب مسیر", txtSub)
        val options = listOf(
            "wg" to "وایرگارد",
            "wiw" to "WARP-in-WARP",
            "masque" to "MASQUE",
            "p_only" to "فقط سایفون",
            "t_only" to "فقط تور",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            options.take(3).forEach { (id, label) ->
                ChoiceChip(label, box1 == id, Nc.BadgeAether, Modifier.weight(1f)) {
                    mutate {
                        it.aetherProtocol = when (id) {
                            "wg" -> "wg"
                            "wiw" -> "gool"
                            else -> "masque"
                        }
                        // Leaving a solo mode must not leave it stuck on.
                        if (it.aetherPsiphon == "only") it.aetherPsiphon = "off"
                        if (it.aetherTor == "only") it.aetherTor = "off"
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            options.drop(3).forEach { (id, label) ->
                ChoiceChip(label, box1 == id, Nc.BadgeAether, Modifier.weight(1f)) {
                    mutate {
                        when (id) {
                            "p_only" -> {
                                it.aetherProtocol = "masque"
                                it.aetherPsiphon = "only"
                                it.aetherTor = "off"
                            }
                            else -> {
                                it.aetherProtocol = "masque"
                                it.aetherTor = "only"
                                it.aetherPsiphon = "off"
                            }
                        }
                    }
                }
            }
        }

        // ════ کادر ۲: زنجیره / تنظیمات حمل‌ونقل ════
        if (!onlyPsiphon && !onlyTor) {
            SectionLabel("۲ · زنجیره‌ی حمل‌ونقل", txtSub)
            ToggleRow("سایفون (زنجیره)", psiphon == "chain", Nc.BadgeAether, txtMain) { on ->
                mutate { it.aetherPsiphon = if (on) "chain" else "off" }
            }
            if (psiphon == "chain") {
                SubField("Psiphon connection") {
                    MiniDropdown(
                        value = psiphonMode,
                        options = listOf("خودکار" to "auto", "فقط CDN" to "cdn", "فقط مستقیم" to "direct"),
                    ) { picked -> mutate { it.aetherPsiphonMode = picked } }
                }
                SubField("کشور خروجی") {
                    MiniDropdown(
                        value = region,
                        options = listOf("خودکار (Auto)" to "") + AETHER_EXIT_COUNTRIES.map { it.first to it.second },
                    ) { picked -> mutate { it.aetherPsiphonRegion = picked } }
                }
                SubField("CDN fronting (اختیاری)") {
                    MiniTextField(cdnIps, "با کاما جدا کنید") { picked -> mutate { it.aetherPsiphonCdnIps = picked } }
                }
            }
            ToggleRow("تور (زنجیره)", tor == "chain", Nc.BadgeAether, txtMain) { on ->
                mutate { it.aetherTor = if (on) "chain" else "off" }
            }
            if (tor == "chain") {
                SubField("Bridges") {
                    MiniDropdown(
                        value = torBridges,
                        options = listOf(
                            "وقتی تور فیلتر است" to "auto",
                            "از ابتدا" to "first",
                            "هرگز" to "never",
                            "پل‌های خودم" to "own",
                        ),
                    ) { picked -> mutate { it.aetherTorBridges = picked } }
                }
                SubField("Bridge sources") {
                    MiniDropdown(
                        value = torRelays,
                        options = listOf(
                            "bridgedb و رله‌های عمومی" to "auto",
                            "فقط رله‌های عمومی" to "only",
                            "خاموش" to "off",
                        ),
                    ) { picked -> mutate { it.aetherTorRelays = picked } }
                }
                if (torBridges == "own") {
                    SubField("Bridge lines") {
                        MiniTextField(bridgeLines, "یک پل در هر خط (torrc)") { picked -> mutate { it.aetherTorBridgeLines = picked } }
                    }
                }
            }
        } else if (onlyPsiphon) {
            SectionLabel("۲ · تنظیمات سایفون (فقط سایفون)", txtSub)
            SubField("Psiphon connection") {
                MiniDropdown(
                    value = psiphonMode,
                    options = listOf("خودکار" to "auto", "فقط CDN" to "cdn", "فقط مستقیم" to "direct"),
                ) { picked -> mutate { it.aetherPsiphonMode = picked } }
            }
            SubField("کشور خروجی") {
                MiniDropdown(
                    value = region,
                    options = listOf("خودکار (Auto)" to "") + AETHER_EXIT_COUNTRIES.map { it.first to it.second },
                ) { picked -> mutate { it.aetherPsiphonRegion = picked } }
            }
            SubField("CDN fronting (اختیاری)") {
                MiniTextField(cdnIps, "با کاما جدا کنید") { picked -> mutate { it.aetherPsiphonCdnIps = picked } }
            }
        } else {
            SectionLabel("۲ · تنظیمات تور (فقط تور)", txtSub)
            SubField("Bridges") {
                MiniDropdown(
                    value = torBridges,
                    options = listOf(
                        "وقتی تور فیلتر است" to "auto",
                        "از ابتدا" to "first",
                        "هرگز" to "never",
                        "پل‌های خودم" to "own",
                    ),
                ) { picked -> mutate { it.aetherTorBridges = picked } }
            }
            SubField("Bridge sources") {
                MiniDropdown(
                    value = torRelays,
                    options = listOf(
                        "bridgedb و رله‌های عمومی" to "auto",
                        "فقط رله‌های عمومی" to "only",
                        "خاموش" to "off",
                    ),
                ) { picked -> mutate { it.aetherTorRelays = picked } }
            }
            if (torBridges == "own") {
                SubField("Bridge lines") {
                    MiniTextField(bridgeLines, "یک پل در هر خط (torrc)") { picked -> mutate { it.aetherTorBridgeLines = picked } }
                }
            }
        }

        // ════ کادر ۳: اتصال ════
        SectionLabel("۳ · اتصال", txtSub)
        val autoMode = if (twoHops) outer.isBlank() else (address.isBlank() || port.isBlank())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ChoiceChip("اتصال خودکار", autoMode, Nc.BadgeAether, Modifier.weight(1f)) {
                mutate { p ->
                    p.server = ""; p.serverPort = ""
                    p.aetherWiwOuter = ""; p.aetherWiwInner = ""
                }
                log("حالت خودکار: اندپوینت هنگام اتصال اسکن می‌شود")
            }
            ChoiceChip("اتصال دستی", !autoMode, Nc.BadgeAether, Modifier.weight(1f)) { /* fields appear below */ }
        }
        if (autoMode) {
            Text(
                "اندپوینت به‌صورت خودکار اسکن و اتصال برقرار می‌شود (تا ۱۲۰ ثانیه) — پیشرفت واقعی هسته در کادر ۴ گزارش می‌شود:",
                color = txtSub, fontSize = 9.5.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
            if (isBlocked) {
                // LIVE real core output while it hunts for an endpoint and
                // brings the carriers up (from AetherCoreManager's session log).
                var liveLines by remember { mutableStateOf(listOf<String>()) }
                LaunchedEffect(isBlocked) {
                    while (isBlocked) {
                        liveLines = com.narcic.ng.core.AetherCoreManager.sessionLogSnapshot()
                        delay(650)
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.Black.copy(alpha = .35f))
                        .border(1.dp, stroke, RoundedCornerShape(11.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (liveLines.isEmpty()) "…" else liveLines.takeLast(10).joinToString("\n"),
                        color = Color(0xFF9FB0C8),
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        } else {
            if (twoHops) {
                SubField("مسیر بیرونی / درونی") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) {
                            MiniTextField(outer, "hop بیرونی") { picked -> mutate { it.aetherWiwOuter = picked } }
                        }
                        Box(Modifier.weight(1f)) {
                            MiniTextField(inner, "hop درونی") { picked -> mutate { it.aetherWiwInner = picked } }
                        }
                    }
                }
            } else {
                SubField("آدرس و پورت") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(2f)) {
                            MiniTextField(address, "162.159.192.1") { picked -> mutate { it.server = picked } }
                        }
                        Box(Modifier.weight(1f)) {
                            MiniTextField(port, "2408") { picked -> mutate { it.serverPort = picked } }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
                ActionButton(
                    label = if (busy) "…" else "اسکن اندپوینت",
                    enabled = !busy && !isBlocked,
                    accent = Nc.BadgeAether,
                    modifier = Modifier.weight(1f),
                ) {
                    busy = true
                    log("در حال اسکن برای یافتن سرور…")
                    scope.launch(Dispatchers.IO) {
                        val fresh = MmkvManager.decodeServerConfig(guid)
                        val result = if (fresh != null) repo.scan(fresh) { line -> log(line) } else null
                        withContext(Dispatchers.Main) {
                            busy = false
                            if (result != null) {
                                val proto = fresh?.aetherProtocol ?: protocol
                                if (AetherProtocol.fromString(proto).twoHops) {
                                    val outerV = result.endpoint.toString()
                                    val innerV = result.innerHop?.toString().orEmpty()
                                    mutate { p ->
                                        p.aetherWiwOuter = outerV
                                        p.aetherWiwInner = innerV
                                    }
                                } else {
                                    val hostV = result.endpoint.host
                                    val portV = result.endpoint.port.toString()
                                    mutate { p ->
                                        p.server = hostV
                                        p.serverPort = portV
                                    }
                                }
                                log("پیدا شد: ${result.endpoint}")
                            } else {
                                log("سروری پیدا نشد")
                            }
                        }
                    }
                }
                ActionButton(
                    label = if (busy) "…" else "کلید جدید WARP",
                    enabled = !busy && !isBlocked,
                    accent = Nc.Sub,
                    modifier = Modifier.weight(1f),
                ) {
                    busy = true
                    log("در حال دریافت کلید جدید WARP…")
                    scope.launch(Dispatchers.IO) {
                        val fresh = MmkvManager.decodeServerConfig(guid)
                        val status = if (fresh != null) repo.renewIdentity(fresh) { line -> log(line) } else null
                        withContext(Dispatchers.Main) {
                            busy = false
                            log(if (status != null) "کلید جدید WARP آماده است" else "ثبت کلید جدید ناموفق بود")
                        }
                    }
                }
            }
        }
        // scan mode / obfuscation / ip version — always inside box 3.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Box(Modifier.weight(1f)) {
                MiniDropdown(
                    value = scanMode,
                    options = listOf(
                        "توربو" to "turbo", "متعادل" to "balanced", "دقیق" to "thorough",
                        "پنهانی" to "verified", "تضمینی" to "ironclad",
                    ),
                    label = "Scan mode",
                ) { picked -> mutate { it.aetherScanMode = picked } }
            }
            Box(Modifier.weight(1f)) {
                MiniDropdown(
                    value = obfuscation,
                    options = listOf(
                        "خودکار" to "auto", "خاموش" to "off", "سبک" to "light",
                        "فایروال" to "firewall", "متعادل" to "balanced", "GFW" to "gfw", "تهاجمی" to "aggressive",
                    ),
                    label = "Obfuscation",
                ) { picked -> mutate { it.aetherObfuscation = picked } }
            }
            Box(Modifier.weight(1f)) {
                MiniDropdown(
                    value = ipVersion,
                    options = listOf("IPv4" to "v4", "IPv6" to "v6", "هر دو" to "both"),
                    label = "IP",
                ) { picked -> mutate { it.aetherIpVersion = picked } }
            }
        }

        // ════ کادر ۴: لاگ ════
        SectionLabel("۴ · لاگ", txtSub)
        Box(
            Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.Black.copy(alpha = .35f))
                .border(1.dp, stroke, RoundedCornerShape(11.dp))
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(
                text = if (logLines.isEmpty()) "خالی — دکمه‌های اسکن/کلید و اتصال خودکار اینجا گزارش می‌دهند" else logLines.joinToString("\n"),
                color = Color(0xFF9FB0C8),
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (isBlocked) {
            Text(
                "⚠ اتصال در جریان است — تغییر مسیر و اسکن تا پایان قفل هستند",
                color = txtSub, fontSize = 8.5.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

// ───────────────────────────── helpers ─────────────────────────────

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text,
        color = color, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 11.dp, bottom = 5.dp),
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) accent.copy(alpha = .16f) else Color.White.copy(alpha = .04f))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = .6f) else Color.White.copy(alpha = .09f),
                RoundedCornerShape(11.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) accent else Nc.Sub,
            fontSize = 9.5.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, accent: Color, txtMain: Color, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = .04f))
            .padding(horizontal = 11.dp, vertical = 4.dp)
    ) {
        Text(label, color = txtMain, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = accent),
        )
    }
}

@Composable
private fun SubField(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 11.dp, top = 4.dp, bottom = 2.dp)) {
        Text(label, color = Nc.Sub, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        content()
    }
}

@Composable
private fun MiniDropdown(
    value: String,
    options: List<Pair<String, String>>,
    label: String? = null,
    onPicked: (String) -> Unit,
) {
    // Plain Box + DropdownMenu — deliberately NOT ExposedDropdownMenuBox:
    // inside a verticalScroll parent the menu-box anchor swallows the first
    // tap (felt as a "frozen" picker), while a plain popup always opens.
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.second == value }?.first
        ?: value.ifBlank { options.firstOrNull()?.first ?: "—" }
    Column(Modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, color = Nc.Sub, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
        }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = .05f))
                    .border(1.dp, Nc.Stroke, RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    display, color = Nc.Txt, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(if (expanded) "▴" else "▾", color = Nc.Sub, fontSize = 10.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (labelOpt, valueOpt) ->
                    DropdownMenuItem(
                        text = { Text(labelOpt, fontSize = 11.sp) },
                        onClick = {
                            onPicked(valueOpt)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniTextField(
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
) {
    // Local text mirror: keeps the cursor stable while every keystroke is
    // written straight through to the profile.
    var local by remember(value) { mutableStateOf(value) }
    androidx.compose.material3.OutlinedTextField(
        value = local,
        onValueChange = {
            local = it
            onValue(it)
        },
        placeholder = { Text(placeholder, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Nc.Txt),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        singleLine = true,
    )
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) accent.copy(alpha = .16f) else Color.White.copy(alpha = .04f))
            .border(
                1.dp,
                if (enabled) accent.copy(alpha = .55f) else Color.White.copy(alpha = .08f),
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp)
    ) {
        if (label == "…") {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(12.dp), color = accent)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (enabled) accent else Nc.Sub, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}
