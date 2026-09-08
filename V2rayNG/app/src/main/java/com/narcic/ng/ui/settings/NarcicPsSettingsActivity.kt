package com.narcic.ng.ui.settings

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnLock
import com.narcic.ng.R
import com.narcic.ng.aether.core.ConnectionController
import com.narcic.ng.aether.shared.data.AetherConfigRepository
import com.narcic.ng.aether.shared.data.AutoDetectRepository
import com.narcic.ng.aether.shared.data.DnsBenchmarkRepository
import com.narcic.ng.aether.shared.model.ConnectionStatus
import com.narcic.ng.aether.shared.model.ConnectionMode
import com.narcic.ng.aether.shared.model.TunnelEngine
import com.narcic.ng.aether.platform.PlatformContext
import com.narcic.ng.aether.platform.getSettings
import com.narcic.ng.ui.base.BaseComponentActivity
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * "Narcic PS" settings — one dedicated top-level entry inside the existing
 * settings hub. Hosts all PS-tunnel (AetherST-derived) configuration in ten
 * subsections, backed exclusively by the real AetherConfigRepository /
 * AutoDetectRepository / DnsBenchmarkRepository. No duplicate config store.
 */
class NarcicPsSettingsActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        NarcicPsSettingsScreen(
            onBackClick = { finish() }
        )
    }
}

private enum class NpsPage {
    ROOT,
    TOOLS,
    AUTO_DETECT,
    DNS_BENCH,
    PROFILES,
    PROTOCOL,
    CHAIN,
    ZEROTRUST,
    SPLIT,
    NETWORK,
    SECURITY,
    DIAG,
    SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NarcicPsSettingsScreen(onBackClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val configRepository = remember {
        AetherConfigRepository.getInstance(
            getSettings(PlatformContext(context))
        )
    }
    val config by configRepository.config.collectAsStateWithLifecycle()
    val connectionStatus by ConnectionController.status.collectAsStateWithLifecycle()
    val isDark = LocalDarkTheme.current

    var page by rememberSaveable { mutableStateOf(NpsPage.ROOT.name) }
    val currentPage = runCatching { NpsPage.valueOf(page) }.getOrDefault(NpsPage.ROOT)

    fun back() {
        if (currentPage == NpsPage.ROOT) onBackClick() else page = NpsPage.ROOT.name
    }

    // DnsBenchmarkRepository needs the Settings backend once for persistence.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { DnsBenchmarkRepository.initialize(getSettings(PlatformContext(context))) }
    }

    val bgColor = if (isDark) Nc.Bg else MaterialTheme.colorScheme.background
    val cardColor = if (isDark) Nc.Bg.copy(alpha = .55f) else MaterialTheme.colorScheme.surface
    val subColor = if (isDark) Nc.Sub else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = if (isDark) Nc.Txt else MaterialTheme.colorScheme.onSurface
    val accent = Nc.Cyan

    Scaffold(
        containerColor = bgColor,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentPage) {
                            NpsPage.ROOT -> stringResource(R.string.nps_title)
                            NpsPage.TOOLS, NpsPage.AUTO_DETECT, NpsPage.DNS_BENCH -> stringResource(R.string.nps_section_tools)
                            NpsPage.PROFILES -> stringResource(R.string.nps_section_profiles)
                            NpsPage.PROTOCOL -> stringResource(R.string.nps_section_protocol)
                            NpsPage.CHAIN -> stringResource(R.string.nps_section_chain)
                            NpsPage.ZEROTRUST -> stringResource(R.string.nps_section_zt)
                            NpsPage.SPLIT -> stringResource(R.string.nps_section_split)
                            NpsPage.NETWORK -> stringResource(R.string.nps_section_network)
                            NpsPage.SECURITY -> stringResource(R.string.nps_section_security)
                            NpsPage.DIAG -> stringResource(R.string.nps_section_diag)
                            NpsPage.SYSTEM -> stringResource(R.string.nps_section_system)
                        },
                        color = titleColor,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nps_back), tint = titleColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentPage) {
                NpsPage.ROOT -> NpsRootPage(
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent,
                    divider = subColor.copy(alpha = 0.15f),
                    onOpen = { page = it.name }
                )
                NpsPage.TOOLS -> NpsToolsPage(
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent,
                    onOpen = { page = it.name }
                )
                NpsPage.AUTO_DETECT -> NarcicAutoDetectPage(
                    connectionStatus = connectionStatus,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent,
                    onApplyResult = { result ->
                        val old = config
                        configRepository.applyDetectedConfig(
                            old.copy(
                                protocol = result.recommendedProtocol,
                                noise = result.recommendedNoise,
                                scanMode = result.recommendedScanMode,
                                mtu = if (result.recommendedMtu > 0) result.recommendedMtu else old.mtu,
                                ipMode = result.recommendedIpMode,
                                h2Mode = result.recommendedH2Mode,
                                echEnabled = result.recommendedEch,
                                h2Fragment = result.recommendedFragment,
                                noDataCheck = result.recommendedNoDataCheck
                            )
                        )
                    }
                )
                NpsPage.DNS_BENCH -> NarcicDnsBenchmarkPage(
                    configRepository = configRepository,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent
                )
                NpsPage.PROFILES -> NarcicProfilesPage(
                    config = config,
                    configRepository = configRepository,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent
                )
                NpsPage.PROTOCOL -> NarcicProtocolPage(
                    config = config,
                    configRepository = configRepository,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent,
                    inner = subColor.copy(alpha = 0.08f)
                )
                NpsPage.CHAIN -> NarcicChainPage(
                    config = config,
                    configRepository = configRepository,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent
                )
                NpsPage.ZEROTRUST -> NarcicZeroTrustPage(
                    config = config,
                    configRepository = configRepository,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent,
                    inner = subColor.copy(alpha = 0.08f)
                )
                NpsPage.SPLIT -> NarcicSplitPage(
                    config = config,
                    configRepository = configRepository,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent
                )
                NpsPage.NETWORK -> NarcicNetworkPage(
                    config = config,
                    configRepository = configRepository,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent,
                    inner = subColor.copy(alpha = 0.08f)
                )
                NpsPage.SECURITY -> NarcicSecurityPage(
                    config = config,
                    configRepository = configRepository,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent
                )
                NpsPage.DIAG -> NarcicDiagPage(
                    config = config,
                    configRepository = configRepository,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent
                )
                NpsPage.SYSTEM -> NarcicSystemPage(
                    configRepository = configRepository,
                    cardColor = cardColor,
                    subColor = subColor,
                    titleColor = titleColor,
                    accent = accent
                )
            }
        }
    }
}

// ============================== shared pieces ==============================

@Composable
internal fun NpsGroupCard(
    cardColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(vertical = 4.dp)
    ) { content() }
}

@Composable
internal fun NpsRow(
    title: String,
    subtitle: String?,
    value: String?,
    icon: ImageVector?,
    iconTint: Color,
    titleColor: Color,
    subColor: Color,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = titleColor.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = subColor, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        if (!value.isNullOrEmpty()) {
            Text(value, color = subColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = subColor, modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun NpsSwitchRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    iconTint: Color,
    titleColor: Color,
    subColor: Color,
    accent: Color,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onChecked(!checked) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = titleColor.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = subColor, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChecked else null,
            enabled = enabled,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF070B14),
                checkedTrackColor = accent
            )
        )
    }
}

@Composable
internal fun NpsDivider(color: Color) {
    HorizontalDivider(color = color, thickness = 0.5.dp, modifier = Modifier.padding(start = 52.dp))
}

// ============================== ROOT ==============================

@Composable
private fun NpsRootPage(
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color,
    divider: Color,
    onOpen: (NpsPage) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                NpsRow(
                    title = stringResource(R.string.nps_sub_auto_detect),
                    subtitle = stringResource(R.string.nps_sub_auto_detect_sub),
                    value = null,
                    icon = Icons.Default.Radar,
                    iconTint = accent,
                    titleColor = titleColor,
                    subColor = subColor,
                    onClick = { onOpen(NpsPage.AUTO_DETECT) }
                )
                NpsDivider(divider)
                NpsRow(
                    title = stringResource(R.string.nps_sub_dns_bench),
                    subtitle = stringResource(R.string.nps_sub_dns_bench_sub),
                    value = null,
                    icon = Icons.Default.Dns,
                    iconTint = accent,
                    titleColor = titleColor,
                    subColor = subColor,
                    onClick = { onOpen(NpsPage.DNS_BENCH) }
                )
            }
        }
        item {
            NpsGroupCard(cardColor) {
                NpsRow(stringResource(R.string.nps_section_profiles), null, null, Icons.Default.Tune, subColor, titleColor, subColor, onClick = { onOpen(NpsPage.PROFILES) })
                NpsDivider(divider)
                NpsRow(stringResource(R.string.nps_section_protocol), null, null, Icons.Default.Shield, subColor, titleColor, subColor, onClick = { onOpen(NpsPage.PROTOCOL) })
                NpsDivider(divider)
                NpsRow(stringResource(R.string.nps_section_chain), null, null, Icons.Default.AllInclusive, subColor, titleColor, subColor, onClick = { onOpen(NpsPage.CHAIN) })
                NpsDivider(divider)
                NpsRow(stringResource(R.string.nps_section_zt), null, null, Icons.Default.Business, subColor, titleColor, subColor, onClick = { onOpen(NpsPage.ZEROTRUST) })
                NpsDivider(divider)
                NpsRow(stringResource(R.string.nps_section_split), null, null, Icons.AutoMirrored.Filled.Rule, subColor, titleColor, subColor, onClick = { onOpen(NpsPage.SPLIT) })
                NpsDivider(divider)
                NpsRow(stringResource(R.string.nps_section_network), null, null, Icons.Default.Public, subColor, titleColor, subColor, onClick = { onOpen(NpsPage.NETWORK) })
                NpsDivider(divider)
                NpsRow(stringResource(R.string.nps_section_security), null, null, Icons.Default.Lock, subColor, titleColor, subColor, onClick = { onOpen(NpsPage.SECURITY) })
                NpsDivider(divider)
                NpsRow(stringResource(R.string.nps_section_diag), null, null, Icons.Default.BugReport, subColor, titleColor, subColor, onClick = { onOpen(NpsPage.DIAG) })
                NpsDivider(divider)
                NpsRow(stringResource(R.string.nps_section_system), null, null, Icons.Default.Settings, subColor, titleColor, subColor, onClick = { onOpen(NpsPage.SYSTEM) })
            }
        }
    }
}

// ============================== TOOLS (landing) ==============================

@Composable
private fun NpsToolsPage(
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color,
    onOpen: (NpsPage) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                NpsRow(
                    stringResource(R.string.nps_sub_auto_detect),
                    stringResource(R.string.nps_sub_auto_detect_sub), null,
                    Icons.Default.Radar, accent, titleColor, subColor,
                    onClick = { onOpen(NpsPage.AUTO_DETECT) }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                NpsRow(
                    stringResource(R.string.nps_sub_dns_bench),
                    stringResource(R.string.nps_sub_dns_bench_sub), null,
                    Icons.Default.Dns, accent, titleColor, subColor,
                    onClick = { onOpen(NpsPage.DNS_BENCH) }
                )
            }
        }
    }
}
