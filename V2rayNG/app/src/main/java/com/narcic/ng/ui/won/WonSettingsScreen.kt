package com.narcic.ng.ui.won

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import com.narcic.ng.won.PsiphonRegions
import com.narcic.ng.won.SecureStore
import com.narcic.ng.won.SplitTunnelSettings
import com.narcic.ng.won.TorBridges
import com.narcic.ng.won.TorRegions

/**
 * همهٔ تنظیمات موتور W on N — پورت مستقیم صفحه‌های تنظیمات MSN-GUARD
 * (Tunnel / Psiphon / Tor / Zero Trust / Security) روی زبان طراحی Narcic.
 *
 * منبعِ داده همان SharedPreferences("settings") است که هستهٔ پورت‌شده
 * (WonConfig / TorManager / WonVpnService) می‌خواند؛ کلیدها عیناً همان‌ها هستند.
 */
private const val PREFS = "settings"

@Composable
fun WonSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
    val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
    val cardBg = if (isDark) Color.White.copy(alpha = 0.045f) else Color.White.copy(alpha = 0.9f)
    val stroke = if (isDark) Nc.Stroke else Color(0x148B95A9)
    val accent = Nc.Cyan

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "بازگشت",
                tint = txtSub,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onBack() },
            )
            Spacer(Modifier.width(12.dp))
            Text("تنظیمات W on N", color = txtMain, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(14.dp))

        // ------------------------------------------------------------- تونل
        SectionHeader("تونل", accent, txtSub)
        SettingCard(cardBg, stroke) {
            ChipsRow(
                "حالت اسکن گیت‌وی",
                listOf("balanced", "thorough", "turbo", "stealth"),
                PREFS to "default_scan_mode",
                accent, txtSub, context,
            )
            ChipsRow(
                "اسکن IP",
                listOf("v4", "v6"),
                PREFS to "default_scan",
                accent, txtSub, context,
            )
            ChipsRow(
                "ترابری MASQUE",
                listOf("h3", "h2"),
                PREFS to "default_masque_transport",
                accent, txtSub, context,
            )
            ChipsRow(
                "کشف endpoint",
                listOf("cache", "fresh", "known", "loc"),
                PREFS to "endpoint_discovery",
                accent, txtSub, context,
            )
            TextRow("Endpoint دستی", "manual_endpoint", "مثلاً engage.cloudflareclient.com:2408", txtSub, context)
            ChipsRow(
                "پروفایل مبهم‌سازی",
                listOf("balanced", "light", "medium", "high", "ironclad"),
                PREFS to "obfuscation_profile",
                accent, txtSub, context,
            )
            SwitchRow("تلاش مجدد با پروفایل‌های دیگر", "retry_obfuscation_profiles", true, txtSub, context)
            TextRow("منحنی TLS", "tls_curve_preset", "chrome", txtSub, context)
            ChipsRow(
                "پروفایل کارایی",
                listOf("auto", "low", "medium", "high"),
                PREFS to "perf_profile",
                accent, txtSub, context,
            )
            SwitchRow("تکه‌تکه‌کردن H2", "h2_fragmentation", true, txtSub, context, storeAsString = true)
            SwitchRow("بررسی دادهٔ WireGuard", "wireguard_data_check", true, txtSub, context)
            TextRow("سرورهای DNS", "dns_servers", "با کاما جدا کنید", txtSub, context)
            TextRow("مسیرهای مستقیم", "route_direct", "CIDR با کاما", txtSub, context)
            TextRow("مسیرهای بلاک", "route_block", "CIDR با کاما", txtSub, context)
            ChipsRow(
                "سطح لاگ",
                listOf("info", "warn", "error", "debug", "trace"),
                PREFS to "log_level",
                accent, txtSub, context,
            )
        }

        // ------------------------------------------------------------ امنیت
        SectionHeader("امنیت", accent, txtSub)
        SettingCard(cardBg, stroke) {
            SwitchRow("اتصال مجدد خودکار", "auto_reconnect", true, txtSub, context)
            SwitchRow("کلید اضطراری (Kill Switch)", "kill_switch", false, txtSub, context)
            SwitchRow("دسترسی LAN (دور زدن شبکهٔ محلی)", "lan_bypass", false, txtSub, context)
            SplitTunnelRow(cardBg, stroke, txtMain, txtSub, accent, context)
        }

        // ----------------------------------------------------------- Psiphon
        SectionHeader("Psiphon", accent, txtSub)
        SettingCard(cardBg, stroke) {
            CountryRow(
                "کشور خروجی (اولویت)",
                PsiphonRegions.options(context) + listOf("auto"),
                "psiphon_egress_region",
                txtSub, accent, context,
            )
            SwitchRow("اشتراک پراکسی در شبکهٔ محلی", "psiphon_lan_sharing", false, txtSub, context)
            ChipsRow(
                "ترابری بیرونیِ Psiphon over WARP",
                listOf("auto", "masque", "wireguard", "gool"),
                PREFS to "chain_outer_mode",
                accent, txtSub, context,
            )
        }

        // --------------------------------------------------------------- Tor
        SectionHeader("Tor", accent, txtSub)
        SettingCard(cardBg, stroke) {
            ChipsRow(
                "حالت اتصال",
                listOf("auto", "direct", "obfs4", "meek", "snowflake"),
                PREFS to "tor_mode",
                accent, txtSub, context,
            )
            CountryRow(
                "کشور خروجی",
                TorRegions.options() + listOf("auto"),
                TorRegions.REGION_PREF,
                txtSub, accent, context,
            )
            SwitchRow("Tor روی WARP (زنجیره)", "tor_chain_armed", true, txtSub, context)
            var showBridges by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showBridges = !showBridges }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            ) {
                Text("پل‌های داخلی", color = txtSub, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(if (showBridges) "بستن" else "نمایش", color = accent, fontSize = 12.sp)
            }
            if (showBridges) {
                Text(
                    "obfs4: ${TorBridges.OBFS4.size} پل · meek: ${TorBridges.MEEK.size} · snowflake: ${TorBridges.SNOWFLAKE.size}",
                    color = txtSub, fontSize = 11.sp,
                )
            }
        }

        // -------------------------------------------------------- Zero Trust
        SectionHeader("Zero Trust (اختیاری)", accent, txtSub)
        SettingCard(cardBg, stroke) {
            SecureRow("Team", "zero_trust_team", txtSub, context)
            SecureRow("Email", "zero_trust_email", txtSub, context)
            SecureRow("Client ID", "zero_trust_client_id", txtSub, context)
            SecureRow("Client Secret", "zero_trust_client_secret", txtSub, context)
            SecureRow("Access Token", "zero_trust_token", txtSub, context)
            SwitchRow("فیلترینگ گیت‌وی", "zero_trust_gateway", false, txtSub, context)
        }

        Spacer(Modifier.height(86.dp))
    }
}

// ---------------------------------------------------------------------------
// Building blocks (Narcic-styled, minimal)
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, accent: Color, txtSub: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Box(
            Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
        Spacer(Modifier.width(9.dp))
        Text(title, color = txtSub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun SettingCard(cardBg: Color, stroke: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        content = content,
    )
}

private fun prefs(context: android.content.Context) =
    context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

@Composable
private fun ChipsRow(
    label: String,
    options: List<String>,
    prefPair: Pair<String, String>,
    accent: Color,
    txtSub: Color,
    context: android.content.Context,
) {
    var selected by rememberSaveable(prefPair) {
        mutableStateOf(prefs(context).getString(prefPair.second, null) ?: options.first())
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = txtSub, fontSize = 12.5.sp, modifier = Modifier.weight(0.9f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1.4f)) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
                        .border(1.dp, if (active) accent.copy(alpha = 0.7f) else Nc.Stroke, RoundedCornerShape(9.dp))
                        .clickable {
                            selected = option
                            prefs(context).edit().putString(prefPair.second, option).apply()
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Text(
                        option,
                        color = if (active) accent else txtSub,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    key: String,
    default: Boolean,
    txtSub: Color,
    context: android.content.Context,
    storeAsString: Boolean = false,
) {
    var checked by rememberSaveable(key) {
        mutableStateOf(
            if (storeAsString) prefs(context).getString(key, if (default) "on" else "off") == "on"
            else prefs(context).getBoolean(key, default)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = txtSub, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 22.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) Nc.Green.copy(alpha = 0.25f) else Nc.Stroke)
                .clickable {
                    val new = !checked
                    checked = new
                    if (storeAsString) prefs(context).edit().putString(key, if (new) "on" else "off").apply()
                    else prefs(context).edit().putBoolean(key, new).apply()
                }
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (checked) Nc.Green else Nc.Sub),
            )
        }
    }
}

@Composable
private fun TextRow(label: String, key: String, hint: String, txtSub: Color, context: android.content.Context) {
    var editing by rememberSaveable(key) { mutableStateOf(false) }
    var value by rememberSaveable(key) { mutableStateOf(prefs(context).getString(key, "").orEmpty()) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = txtSub, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            Text(
                if (value.isBlank()) hint else value,
                color = if (value.isBlank()) Nc.Sub.copy(alpha = 0.6f) else txtSub,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clickable { editing = true },
            )
        }
        if (editing) {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = txtSub),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                trailingIcon = {
                    Text(
                        "ذخیره",
                        color = Nc.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                prefs(context).edit().putString(key, value.trim()).apply()
                                editing = false
                            }
                            .padding(8.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun SecureRow(label: String, key: String, txtSub: Color, context: android.content.Context) {
    var editing by rememberSaveable(key) { mutableStateOf(false) }
    var value by rememberSaveable(key) { mutableStateOf(SecureStore.getSecret(context, key)) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = txtSub, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            Text(
                if (value.isBlank()) "تنظیم نشده" else "••••••••",
                color = txtSub,
                fontSize = 11.sp,
                modifier = Modifier
                    .weight(1f)
                    .clickable { editing = !editing },
            )
        }
        if (editing) {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = txtSub),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                trailingIcon = {
                    Text(
                        "ذخیره",
                        color = Nc.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                SecureStore.putSecret(context, key, value.trim())
                                editing = false
                            }
                            .padding(8.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun CountryRow(label: String, options: List<String>, key: String, txtSub: Color, accent: Color, context: android.content.Context) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var selected by rememberSaveable(key) {
        mutableStateOf(prefs(context).getString(key, "auto") ?: "auto")
    }
    fun nameOf(code: String): String {
        if (code == "auto") return "🌍 خودکار"
        // Regional-indicator flag emoji straight from the ISO code (Narcic's
        // CountryFlags works off remarks, not raw codes).
        val flag = code.take(2).map { Character.toChars(0x1F1E6 + (it - 'A'))[0] }.joinToString("")
        val name = PsiphonRegions.name(code).takeIf { it != code }
            ?: TorRegions.name(code).takeIf { it != code }
            ?: code
        return "$flag $name"
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Text(label, color = txtSub, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            Text(
                nameOf(selected) + if (expanded) " ▲" else " ▼",
                color = accent,
                fontSize = 12.sp,
            )
        }
        if (expanded) {
            options.forEach { code ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val toStore = if (code == "auto") "auto" else code
                            selected = toStore
                            prefs(context).edit().putString(key, toStore).apply()
                            expanded = false
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(nameOf(code), color = if (code == selected) accent else txtSub, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SplitTunnelRow(
    cardBg: Color,
    stroke: Color,
    txtMain: Color,
    txtSub: Color,
    accent: Color,
    context: android.content.Context,
) {
    var mode by rememberSaveable {
        mutableStateOf(prefs(context).getString("won_split_mode", "ALL") ?: "ALL")
    }
    val settings = remember { SplitTunnelSettings(context) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text("تونل تفکیکی", color = txtSub, fontSize = 12.5.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "ALL" to "همهٔ اپ‌ها",
                "INCLUDE" to "فقط انتخاب‌شده",
                "EXCLUDE" to "مستثنی‌شده",
            ).forEach { (key, label) ->
                val active = mode == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
                        .border(1.dp, if (active) accent.copy(alpha = 0.7f) else Nc.Stroke, RoundedCornerShape(9.dp))
                        .clickable {
                            mode = key
                            prefs(context).edit().putString("won_split_mode", key).apply()
                            settings.save(
                                when (key) {
                                    "INCLUDE" -> SplitTunnelSettings.Mode.INCLUDE
                                    "EXCLUDE" -> SplitTunnelSettings.Mode.EXCLUDE
                                    else -> SplitTunnelSettings.Mode.ALL
                                },
                                settings.packages(),
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(label, color = if (active) accent else txtSub, fontSize = 10.5.sp)
                }
            }
        }
        Text(
            "لیست اپ‌ها: ${settings.packages().size} اپ انتخاب شده (همان انتخاب‌های per-app سیستم)",
            color = txtSub.copy(alpha = 0.7f),
            fontSize = 10.5.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
