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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.enums.AetherProtocol
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import com.narcic.ng.ui.server.AETHER_EXIT_COUNTRIES
import com.narcic.ng.ui.server.AetherEditorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The 4-box quick-setup dashboard of the اتر page (3-page redesign):
 *
 *   ┌ کادر ۱: انتخاب مسیر ────────────────────────────┐
 *   │ وایرگارد · WARP-in-WARP · MASQUE · فقط سایفون · فقط تور │
 *   └─────────────────────────────────────────────────┘
 *   ┌ کادر ۲: حمل‌ونقل (زنجیره) ──────────────────────┐
 *   │ wg/wiw/masque  → سایفون ✓ تور ✓ (+ تنظیمات هرکدام) │
 *   │ فقط سایفون      → فقط تنظیمات سایفون              │
 *   │ فقط تور         → فقط تنظیمات تور                 │
 *   └─────────────────────────────────────────────────┘
 *   ┌ کادر ۳: اتصال ──────────────────────────────────┐
 *   │ خودکار (اسکن خودکار اندپوینت هنگام اتصال)         │
 *   │ دستی  → آدرس/پورت + اسکن اندپوینت + کلید جدید WARP │
 *   │         + scan mode · obfuscation · ip version     │
 *   └─────────────────────────────────────────────────┘
 *   ┌ کادر ۴: لاگ ────────────────────────────────────┐
 *
 * Every change is written straight through to the selected Aether profile
 * in MMKV (decode → mutate → encode) — the same storage the connect flow
 * reads, so no extra "save" step is needed. Scanning / key renewal reuse
 * the exact same AetherEditorRepository paths the full editor uses, and are
 * disabled while a tunnel is up. When the user connects, MainScreen hides
 * this whole dashboard and the download/upload dashboard takes over.
 */
@Composable
fun AetherQuickSetupCard(
    guid: String?,
    profile: ProfileItem?,
    isBlocked: Boolean,
    onAddNew: () -> Unit,
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

    val logLines = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    fun log(line: String) {
        logLines.add(line)
        if (logLines.size > 60) logLines.removeRange(0, logLines.size - 60)
    }

    if (profile == null || guid == null) {
        // No Aether profile yet — the page only offers creating one.
        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(1.dp, stroke, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("هنوز کانفیگ Aether ندارید", color = txtMain, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "یک کانفیگ بسازید تا داشبورد راه‌اندازی سریع همین‌جا ظاهر شود",
                color = txtSub, fontSize = 10.5.sp,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Nc.BadgeAether)
                    .clickable(onClick = onAddNew)
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text("+ افزودن کانفیگ Aether", color = Color(0xFF0B1220), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        return
    }

    // ── local mirrors of the profile fields, seeded once per profile ──
    var protocol by remember(guid) { mutableStateOf(profile.aetherProtocol ?: "masque") }
    var psiphon by remember(guid) { mutableStateOf(profile.aetherPsiphon ?: "off") }
    var tor by remember(guid) { mutableStateOf(profile.aetherTor ?: "off") }
    var psiphonMode by remember(guid) { mutableStateOf(profile.aetherPsiphonMode ?: "auto") }
    var region by remember(guid) { mutableStateOf(profile.aetherPsiphonRegion ?: "") }
    var cdnIps by remember(guid) { mutableStateOf(profile.aetherPsiphonCdnIps ?: "") }
    var torBridges by remember(guid) { mutableStateOf(profile.aetherTorBridges ?: "auto") }
    var torRelays by remember(guid) { mutableStateOf(profile.aetherTorRelays ?: "auto") }
    var bridgeLines by remember(guid) { mutableStateOf(profile.aetherTorBridgeLines ?: "") }
    var scanMode by remember(guid) { mutableStateOf(profile.aetherScanMode ?: "balanced") }
    var obfuscation by remember(guid) { mutableStateOf(profile.aetherObfuscation ?: "auto") }
    var ipVersion by remember(guid) { mutableStateOf(profile.aetherIpVersion ?: "v4") }
    var address by remember(guid) { mutableStateOf(profile.server ?: "") }
    var port by remember(guid) { mutableStateOf(profile.serverPort ?: "") }
    var outer by remember(guid) { mutableStateOf(profile.aetherWiwOuter ?: "") }
    var inner by remember(guid) { mutableStateOf(profile.aetherWiwInner ?: "") }

    fun mutate(block: (ProfileItem) -> Unit) {
        MmkvManager.decodeServerConfig(guid)?.let { p ->
            block(p)
            MmkvManager.encodeServerConfig(guid, p)
        }
    }

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

    fun pickBox1(id: String) {
        val (proto, psi, tr) = when (id) {
            "wg" -> Triple("wg", if (psiphon == "only") "off" else psiphon, if (tor == "only") "off" else tor)
            "wiw" -> Triple("gool", if (psiphon == "only") "off" else psiphon, if (tor == "only") "off" else tor)
            "p_only" -> Triple("masque", "only", "off")
            "t_only" -> Triple("masque", "off", "only")
            else -> Triple("masque", if (psiphon == "only") "off" else psiphon, if (tor == "only") "off" else tor)
        }
        protocol = proto; psiphon = psi; tor = tr
        mutate {
            it.aetherProtocol = proto
            it.aetherPsiphon = psi
            it.aetherTor = tr
        }
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
        // ── header ──
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
                ChoiceChip(label, box1 == id, Nc.BadgeAether, Modifier.weight(1f)) { pickBox1(id) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            options.drop(3).forEach { (id, label) ->
                ChoiceChip(label, box1 == id, Nc.BadgeAether, Modifier.weight(1f)) { pickBox1(id) }
            }
        }

        // ════ کادر ۲: حمل‌ونقل ════
        if (!onlyPsiphon && !onlyTor) {
            SectionLabel("۲ · زنجیره‌ی حمل‌ونقل", txtSub)
            ToggleRow("سایفون (زنجیره)", psiphon == "chain", Nc.BadgeAether, txtMain) { on ->
                psiphon = if (on) "chain" else "off"
                mutate { it.aetherPsiphon = psiphon }
            }
            if (psiphon == "chain") {
                SubField("Psiphon connection") {
                    MiniDropdown(
                        value = psiphonMode,
                        options = listOf("خودکار" to "auto", "فقط CDN" to "cdn", "فقط مستقیم" to "direct"),
                    ) { psiphonMode = it; mutate { p -> p.aetherPsiphonMode = it } }
                }
                SubField("کشور خروجی") {
                    MiniDropdown(
                        value = if (region.isBlank()) "خودکار (Auto)"
                        else AETHER_EXIT_COUNTRIES.firstOrNull { it.second.equals(region.trim(), true) }?.first ?: "سفارشی…",
                        options = listOf("خودکار (Auto)" to "") + AETHER_EXIT_COUNTRIES.map { it.first to it.second },
                    ) { picked ->
                        region = AETHER_EXIT_COUNTRIES.firstOrNull { it.first == picked }?.second ?: ""
                        mutate { p -> p.aetherPsiphonRegion = region }
                    }
                }
                SubField("CDN fronting (اختیاری)") {
                    MiniTextField(cdnIps, "با کاما جدا کنید") { cdnIps = it; mutate { p -> p.aetherPsiphonCdnIps = it } }
                }
            }
            ToggleRow("تور (زنجیره)", tor == "chain", Nc.BadgeAether, txtMain) { on ->
                tor = if (on) "chain" else "off"
                mutate { it.aetherTor = tor }
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
                    ) { torBridges = it; mutate { p -> p.aetherTorBridges = it } }
                }
                SubField("Bridge sources") {
                    MiniDropdown(
                        value = torRelays,
                        options = listOf(
                            "bridgedb و رله‌های عمومی" to "auto",
                            "فقط رله‌های عمومی" to "only",
                            "خاموش" to "off",
                        ),
                    ) { torRelays = it; mutate { p -> p.aetherTorRelays = it } }
                }
                if (torBridges == "own") {
                    SubField("Bridge lines") {
                        MiniTextField(bridgeLines, "یک پل در هر خط (torrc)") { bridgeLines = it; mutate { p -> p.aetherTorBridgeLines = it } }
                    }
                }
            }
        } else if (onlyPsiphon) {
            SectionLabel("۲ · تنظیمات سایفون (فقط سایفون)", txtSub)
            SubField("Psiphon connection") {
                MiniDropdown(
                    value = psiphonMode,
                    options = listOf("خودکار" to "auto", "فقط CDN" to "cdn", "فقط مستقیم" to "direct"),
                ) { psiphonMode = it; mutate { p -> p.aetherPsiphonMode = it } }
            }
            SubField("کشور خروجی") {
                MiniDropdown(
                    value = if (region.isBlank()) "خودکار (Auto)"
                    else AETHER_EXIT_COUNTRIES.firstOrNull { it.second.equals(region.trim(), true) }?.first ?: "سفارشی…",
                    options = listOf("خودکار (Auto)" to "") + AETHER_EXIT_COUNTRIES.map { it.first to it.second },
                ) { picked ->
                    region = AETHER_EXIT_COUNTRIES.firstOrNull { it.first == picked }?.second ?: ""
                    mutate { p -> p.aetherPsiphonRegion = region }
                }
            }
            SubField("CDN fronting (اختیاری)") {
                MiniTextField(cdnIps, "با کاما جدا کنید") { cdnIps = it; mutate { p -> p.aetherPsiphonCdnIps = it } }
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
                ) { torBridges = it; mutate { p -> p.aetherTorBridges = it } }
            }
            SubField("Bridge sources") {
                MiniDropdown(
                    value = torRelays,
                    options = listOf(
                        "bridgedb و رله‌های عمومی" to "auto",
                        "فقط رله‌های عمومی" to "only",
                        "خاموش" to "off",
                    ),
                ) { torRelays = it; mutate { p -> p.aetherTorRelays = it } }
            }
            if (torBridges == "own") {
                SubField("Bridge lines") {
                    MiniTextField(bridgeLines, "یک پل در هر خط (torrc)") { bridgeLines = it; mutate { p -> p.aetherTorBridgeLines = it } }
                }
            }
        }

        // ════ کادر ۳: اتصال ════
        SectionLabel("۳ · اتصال", txtSub)
        val autoMode = if (twoHops) outer.isBlank() else (address.isBlank() || port.isBlank())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ChoiceChip("اتصال خودکار", autoMode, Nc.BadgeAether, Modifier.weight(1f)) {
                address = ""; port = ""; outer = ""; inner = ""
                mutate { p ->
                    p.server = ""; p.serverPort = ""
                    p.aetherWiwOuter = ""; p.aetherWiwInner = ""
                }
                log("حالت خودکار: اندپوینت هنگام اتصال اسکن می‌شود")
            }
            ChoiceChip("اتصال دستی", !autoMode, Nc.BadgeAether, Modifier.weight(1f)) { /* fields appear */ }
        }
        if (autoMode) {
            Text(
                "اندپوینت به‌صورت خودکار اسکن و اتصال برقرار می‌شود (ممکن است تا ۱۲۰ ثانیه طول بکشد).",
                color = txtSub, fontSize = 9.5.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        } else {
            if (twoHops) {
                SubField("مسیر بیرونی / درونی") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) {
                            MiniTextField(outer, "hop بیرونی") { outer = it; mutate { p -> p.aetherWiwOuter = it } }
                        }
                        Box(Modifier.weight(1f)) {
                            MiniTextField(inner, "hop درونی") { inner = it; mutate { p -> p.aetherWiwInner = it } }
                        }
                    }
                }
            } else {
                SubField("آدرس و پورت") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(2f)) {
                            MiniTextField(address, "162.159.192.1") { address = it; mutate { p -> p.server = it } }
                        }
                        Box(Modifier.weight(1f)) {
                            MiniTextField(port, "2408") { port = it; mutate { p -> p.serverPort = it } }
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
                        val result = repo.scan(profile) { line -> log(line) }
                        withContext(Dispatchers.Main) {
                            busy = false
                            if (result != null) {
                                if (AetherProtocol.fromString(protocol).twoHops) {
                                    outer = result.endpoint.toString()
                                    inner = result.innerHop?.toString().orEmpty()
                                    mutate { p ->
                                        p.aetherWiwOuter = outer
                                        p.aetherWiwInner = inner
                                    }
                                } else {
                                    address = result.endpoint.host
                                    port = result.endpoint.port.toString()
                                    mutate { p ->
                                        p.server = address
                                        p.serverPort = port
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
                        val status = repo.renewIdentity(profile) { line -> log(line) }
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
                ) { scanMode = it; mutate { p -> p.aetherScanMode = it } }
            }
            Box(Modifier.weight(1f)) {
                MiniDropdown(
                    value = obfuscation,
                    options = listOf(
                        "خودکار" to "auto", "خاموش" to "off", "سبک" to "light",
                        "فایروال" to "firewall", "متعادل" to "balanced", "GFW" to "gfw", "تهاجمی" to "aggressive",
                    ),
                    label = "Obfuscation",
                ) { obfuscation = it; mutate { p -> p.aetherObfuscation = it } }
            }
            Box(Modifier.weight(1f)) {
                MiniDropdown(
                    value = ipVersion,
                    options = listOf("IPv4" to "v4", "IPv6" to "v6", "هر دو" to "both"),
                    label = "IP",
                ) { ipVersion = it; mutate { p -> p.aetherIpVersion = it } }
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
                text = if (logLines.isEmpty()) "خالی — دکمه‌های اسکن/کلید اینجا گزارش می‌دهند" else logLines.joinToString("\n"),
                color = Color(0xFF9FB0C8),
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (isBlocked) {
            Text(
                "⚠ تا زمان اتصال، اسکن و کلید جدید قفل هستند",
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
        Switch(checked = checked, onCheckedChange = onToggle, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = accent))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniDropdown(
    value: String,
    options: List<Pair<String, String>>,
    label: String? = null,
    onPicked: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.second == value }?.first ?: value
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { if (label != null) Text(label, fontSize = 8.5.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Nc.Txt),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(10.dp),
        )
        ExposedDropdownMenu(
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

@Composable
private fun MiniTextField(
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
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
            .background(
                if (enabled) accent.copy(alpha = .16f) else Color.White.copy(alpha = .04f)
            )
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
