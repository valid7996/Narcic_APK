package com.narcic.ng.ui.server

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.core.AetherCore
import com.narcic.ng.core.AetherScanResult
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.enums.AetherProtocol
import com.narcic.ng.enums.AetherPsiphon
import com.narcic.ng.enums.AetherPsiphonMode
import com.narcic.ng.enums.AetherTor
import com.narcic.ng.enums.AetherTorBridges
import com.narcic.ng.enums.AetherTorRelays
import com.narcic.ng.enums.AetherTransport
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.extension.toast
import com.narcic.ng.extension.toastError
import com.narcic.ng.extension.toastSuccess
import com.narcic.ng.fmt.AetherFmt
import com.narcic.ng.handler.SettingsManager
import com.narcic.ng.ui.compose.CollapsiblePreferenceGroupHeader
import com.narcic.ng.ui.compose.ConfirmDialog
import com.narcic.ng.ui.compose.FormDropdownField
import com.narcic.ng.ui.compose.FormTextField
import com.narcic.ng.ui.compose.SettingsSwitchItem
import com.narcic.ng.ui.compose.verticalScrollbar
import com.narcic.ng.util.Utils
import kotlinx.coroutines.launch

class ServerAetherActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.AETHER

    private val viewModel: ServerAetherViewModel by viewModels {
        viewModelFactory {
            initializer { ServerAetherViewModel(application, AetherEditorRepository(application)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A session can start or stop while this screen is away, e.g. from the notification.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshSession()
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(initialConfig = initialConfig)
        }.apply {
            configType = serverConfigType
        }
        val isCoreAvailable by viewModel.isCoreAvailable.collectAsStateWithLifecycle()
        val isPsiphonAvailable by viewModel.isPsiphonAvailable.collectAsStateWithLifecycle()
        val isTorTransportsAvailable by viewModel.isTorTransportsAvailable.collectAsStateWithLifecycle()
        val scanState by viewModel.scanState.collectAsStateWithLifecycle()
        val isRenewingIdentity by viewModel.isRenewingIdentity.collectAsStateWithLifecycle()
        val session by viewModel.session.collectAsStateWithLifecycle()
        val log by viewModel.log.collectAsStateWithLifecycle()
        var showRenewConfirm by rememberSaveable { mutableStateOf(false) }
        // Folded away unless one of its settings holds a value, so a profile that set one shows it at once.
        var showAdvanced by rememberSaveable { mutableStateOf(uiState.hasAdvancedAetherSettings) }
        val isScanning = scanState == AetherScanState.Scanning
        val isBusy = isScanning || isRenewingIdentity
        // The key files are shared by every Aether profile, so a live session on any of them blocks renewal.
        // Only the daemon-side evidence counts: a running non-Aether profile leaves both actions open.
        val renewBlocked = session != null

        val protocol = AetherProtocol.fromString(uiState.aetherProtocol)
        val psiphon = AetherPsiphon.fromString(uiState.aetherPsiphon)
        val psiphonMode = AetherPsiphonMode.fromString(uiState.aetherPsiphonMode)
        val tor = AetherTor.fromString(uiState.aetherTor)
        val torBridges = AetherTorBridges.fromString(uiState.aetherTorBridges)
        // With Psiphon or Tor alone there is no WARP tunnel, and nothing about one to set.
        val warpUsed = psiphon != AetherPsiphon.ONLY && tor != AetherTor.ONLY
        val usesHttp2 = protocol.overMasque &&
            AetherTransport.fromString(uiState.aetherTransport) == AetherTransport.HTTP2
        // A scan opens a second tunnel on this protocol's key; a live session on that key must not be disturbed.
        val scanBlocked = session?.disturbedByScanOf(protocol) == true

        LaunchedEffect(protocol) {
            viewModel.showIdentity(protocol)
        }

        LaunchedEffect(scanState) {
            when (val state = scanState) {
                is AetherScanState.Found -> {
                    applyScanResult(uiState, state.result)
                    toastSuccess(R.string.aether_scan_success)
                    viewModel.onScanHandled()
                }

                AetherScanState.NotFound -> {
                    toastError(R.string.aether_scan_failed)
                    viewModel.onScanHandled()
                }

                AetherScanState.Idle, AetherScanState.Scanning -> Unit
            }
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            FormTextField(
                stringResource(R.string.server_lab_remarks),
                uiState.remarks,
                { uiState.remarks = it }
            )
            if (warpUsed) {
                AetherDropdownField(
                    label = R.string.aether_lab_protocol,
                    value = uiState.aetherProtocol,
                    entries = R.array.aether_protocol_entries,
                    values = R.array.aether_protocol_values,
                    enabled = !isBusy,
                    onValueChange = { uiState.aetherProtocol = it }
                )
                if (protocol.overMasque) {
                    AetherDropdownField(
                        label = R.string.aether_lab_transport,
                        value = uiState.aetherTransport,
                        entries = R.array.aether_transport_entries,
                        values = R.array.aether_transport_values,
                        onValueChange = { uiState.aetherTransport = it }
                    )
                }
                if (usesHttp2) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.aether_lab_fragment),
                        checked = uiState.aetherFragment,
                        onCheckedChange = { uiState.aetherFragment = it }
                    )
                    if (uiState.aetherFragment) {
                        FormTextField(
                            stringResource(R.string.aether_lab_fragment_size),
                            uiState.aetherFragmentSize,
                            { uiState.aetherFragmentSize = it },
                            placeholder = stringResource(R.string.aether_hint_fragment_size)
                        )
                        FormTextField(
                            stringResource(R.string.aether_lab_fragment_delay),
                            uiState.aetherFragmentDelay,
                            { uiState.aetherFragmentDelay = it },
                            placeholder = stringResource(R.string.aether_hint_fragment_delay)
                        )
                    }
                }
                if (protocol.overMasque) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.aether_lab_ech),
                        summary = stringResource(R.string.aether_hint_ech),
                        checked = uiState.aetherEch,
                        onCheckedChange = { uiState.aetherEch = it }
                    )
                }
                AetherDropdownField(
                    label = R.string.aether_lab_scan_mode,
                    value = uiState.aetherScanMode,
                    entries = R.array.aether_scan_entries,
                    values = R.array.aether_scan_values,
                    onValueChange = { uiState.aetherScanMode = it }
                )
                AetherDropdownField(
                    label = R.string.aether_lab_obfuscation,
                    value = uiState.aetherObfuscation,
                    entries = R.array.aether_obfuscation_entries,
                    values = R.array.aether_obfuscation_values,
                    onValueChange = { uiState.aetherObfuscation = it }
                )
                AetherDropdownField(
                    label = R.string.aether_lab_ip_version,
                    value = uiState.aetherIpVersion,
                    entries = R.array.aether_ip_entries,
                    values = R.array.aether_ip_values,
                    onValueChange = { uiState.aetherIpVersion = it }
                )
            }
            AetherDropdownField(
                label = R.string.aether_lab_psiphon,
                value = uiState.aetherPsiphon,
                entries = R.array.aether_psiphon_entries,
                values = R.array.aether_psiphon_values,
                enabled = !isBusy,
                onValueChange = { uiState.aetherPsiphon = it }
            )
            if (psiphon != AetherPsiphon.OFF) {
                if (!isPsiphonAvailable) {
                    Text(
                        text = stringResource(R.string.aether_psiphon_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                AetherDropdownField(
                    label = R.string.aether_lab_psiphon_mode,
                    value = uiState.aetherPsiphonMode,
                    entries = R.array.aether_psiphon_mode_entries,
                    values = R.array.aether_psiphon_mode_values,
                    onValueChange = { uiState.aetherPsiphonMode = it }
                )
                // The CDN lists feed the fronted transports alone, which the direct shape never uses; the
                // server names count only beside an IP list of one's own, since the built-in list comes whole.
                if (psiphonMode != AetherPsiphonMode.DIRECT) {
                    FormTextField(
                        stringResource(R.string.aether_lab_psiphon_cdn_ips),
                        uiState.aetherPsiphonCdnIps,
                        { uiState.aetherPsiphonCdnIps = it },
                        placeholder = stringResource(R.string.aether_hint_psiphon_list)
                    )
                    if (uiState.aetherPsiphonCdnIps.isNotBlank()) {
                        FormTextField(
                            stringResource(R.string.aether_lab_psiphon_cdn_sni),
                            uiState.aetherPsiphonCdnSni,
                            { uiState.aetherPsiphonCdnSni = it },
                            placeholder = stringResource(R.string.aether_hint_psiphon_list)
                        )
                    }
                }
                // Exit country: a picker over the known Psiphon exit countries
                // instead of hand-typing a code. "خودکار" stores the blank
                // (any-country) value; "سفارشی…" reveals the raw field for the
                // advanced shapes the core accepts (multi "DE,SE" or "!IR,RU").
                var showCustomExitCountry by rememberSaveable { mutableStateOf(false) }
                val exitAutoLabel = "خودکار (Auto)"
                val exitCustomLabel = "سفارشی… (Custom)"
                val exitIsKnownSingle = AETHER_EXIT_COUNTRIES.any {
                    it.second.equals(uiState.aetherPsiphonRegion.trim(), ignoreCase = true)
                }
                val exitSelectedLabel = when {
                    uiState.aetherPsiphonRegion.isBlank() -> exitAutoLabel
                    exitIsKnownSingle -> AETHER_EXIT_COUNTRIES.first {
                        it.second.equals(uiState.aetherPsiphonRegion.trim(), ignoreCase = true)
                    }.first
                    else -> exitCustomLabel
                }
                FormDropdownField(
                    label = stringResource(R.string.aether_lab_psiphon_region),
                    value = exitSelectedLabel,
                    options = listOf(exitAutoLabel) + AETHER_EXIT_COUNTRIES.map { it.first } + listOf(exitCustomLabel),
                    onValueChange = { picked ->
                        when (picked) {
                            exitAutoLabel -> {
                                uiState.aetherPsiphonRegion = ""
                                showCustomExitCountry = false
                            }
                            exitCustomLabel -> showCustomExitCountry = true
                            else -> {
                                uiState.aetherPsiphonRegion =
                                    AETHER_EXIT_COUNTRIES.firstOrNull { it.first == picked }?.second
                                        ?: uiState.aetherPsiphonRegion
                                showCustomExitCountry = false
                            }
                        }
                    }
                )
                if (showCustomExitCountry || (uiState.aetherPsiphonRegion.isNotBlank() && !exitIsKnownSingle)) {
                    FormTextField(
                        stringResource(R.string.aether_lab_psiphon_region),
                        uiState.aetherPsiphonRegion,
                        { uiState.aetherPsiphonRegion = it },
                        placeholder = stringResource(R.string.aether_hint_psiphon_region)
                    )
                }
                SettingsSwitchItem(
                    title = stringResource(R.string.aether_lab_psiphon_bundled_list),
                    summary = stringResource(R.string.aether_hint_psiphon_bundled_list),
                    checked = uiState.aetherPsiphonBundledList,
                    onCheckedChange = { uiState.aetherPsiphonBundledList = it }
                )
                // What Psiphon has learned is shared by every profile, like the WARP key, and goes only while no session runs on it.
                OutlinedButton(
                    onClick = viewModel::clearPsiphonData,
                    enabled = isCoreAvailable && !isBusy && !renewBlocked,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.aether_action_clear_psiphon))
                }
                Text(
                    text = stringResource(R.string.aether_hint_clear_psiphon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            AetherDropdownField(
                label = R.string.aether_lab_tor,
                value = uiState.aetherTor,
                entries = R.array.aether_tor_entries,
                values = R.array.aether_tor_values,
                enabled = !isBusy,
                onValueChange = { uiState.aetherTor = it }
            )
            if (tor != AetherTor.OFF) {
                // Inside the tunnel Tor is never blocked and asks for no bridges unless told to; around it or
                // alone it has to reach Tor first, and where Tor is blocked that takes the transport program.
                val bridgesUsed = torBridges != AetherTorBridges.NEVER && !(tor == AetherTor.CHAIN && torBridges == AetherTorBridges.AUTO)
                if (bridgesUsed && !isTorTransportsAvailable) {
                    Text(
                        text = stringResource(R.string.aether_tor_transports_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                AetherDropdownField(
                    label = R.string.aether_lab_tor_bridges,
                    value = uiState.aetherTorBridges,
                    entries = R.array.aether_tor_bridges_entries,
                    values = R.array.aether_tor_bridges_values,
                    onValueChange = { uiState.aetherTorBridges = it }
                )
                if (torBridges == AetherTorBridges.AUTO || torBridges == AetherTorBridges.FIRST) {
                    AetherDropdownField(
                        label = R.string.aether_lab_tor_relays,
                        value = uiState.aetherTorRelays,
                        entries = R.array.aether_tor_relays_entries,
                        values = R.array.aether_tor_relays_values,
                        onValueChange = { uiState.aetherTorRelays = it }
                    )
                }
                if (torBridges == AetherTorBridges.OWN) {
                    FormTextField(
                        stringResource(R.string.aether_lab_tor_bridge_lines),
                        uiState.aetherTorBridgeLines,
                        { uiState.aetherTorBridgeLines = it },
                        placeholder = stringResource(R.string.aether_hint_tor_bridge_lines),
                        maxLines = 6
                    )
                }
            }
            if (warpUsed) {
                if (protocol.twoHops) {
                    FormTextField(
                        stringResource(R.string.aether_lab_wiw_outer),
                        uiState.aetherWiwOuter,
                        { uiState.aetherWiwOuter = it },
                        placeholder = stringResource(R.string.aether_hint_endpoint)
                    )
                    FormTextField(
                        stringResource(R.string.aether_lab_wiw_inner),
                        uiState.aetherWiwInner,
                        { uiState.aetherWiwInner = it },
                        placeholder = stringResource(R.string.aether_hint_endpoint)
                    )
                } else {
                    FormTextField(
                        stringResource(R.string.server_lab_address),
                        uiState.address,
                        { uiState.address = it },
                        placeholder = stringResource(R.string.aether_hint_endpoint)
                    )
                    FormTextField(
                        stringResource(R.string.server_lab_port),
                        uiState.port,
                        { uiState.port = it },
                        keyboardType = KeyboardType.Number
                    )
                }
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.scan(uiState.toProfileItem(initialConfig)) },
                        enabled = isCoreAvailable && !isBusy && !scanBlocked
                    ) {
                        if (isScanning) {
                            ProgressMark()
                        }
                        Text(stringResource(if (isScanning) R.string.aether_action_scanning else R.string.aether_action_scan))
                    }
                    if (isScanning) {
                        TextButton(onClick = viewModel::cancelScan) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                }
                if (scanBlocked) {
                    Text(
                        text = stringResource(R.string.aether_scan_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                OutlinedButton(
                    onClick = { showRenewConfirm = true },
                    enabled = isCoreAvailable && !isBusy && !renewBlocked,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    if (isRenewingIdentity) {
                        ProgressMark()
                    }
                    Text(
                        stringResource(
                            if (isRenewingIdentity) R.string.aether_action_renewing_key else R.string.aether_action_renew_key
                        )
                    )
                }
                if (renewBlocked) {
                    Text(
                        text = stringResource(R.string.aether_renew_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            CollapsiblePreferenceGroupHeader(
                title = stringResource(R.string.aether_lab_advanced),
                expanded = showAdvanced,
                onExpandedChange = { showAdvanced = it }
            )
            if (showAdvanced) {
                if (warpUsed) {
                    FormTextField(
                        stringResource(R.string.aether_lab_dns),
                        uiState.aetherDns,
                        { uiState.aetherDns = it },
                        placeholder = stringResource(R.string.aether_hint_dns)
                    )
                    FormTextField(
                        stringResource(R.string.aether_lab_exit_loc),
                        uiState.aetherExitLoc,
                        { uiState.aetherExitLoc = it },
                        placeholder = stringResource(R.string.aether_hint_exit_loc)
                    )
                }
                FormTextField(
                    stringResource(R.string.aether_lab_listen_port),
                    uiState.aetherListenPort,
                    { uiState.aetherListenPort = it },
                    keyboardType = KeyboardType.Number,
                    placeholder = AppConfig.PORT_AETHER_SOCKS
                )
            }
            if (!isCoreAvailable) {
                Text(
                    text = stringResource(R.string.aether_unsupported_abi),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            // The command the core is started with, built from the settings above and open to a hand
            // that needs an option the settings have no field for.
            val builtCommand = AetherCore.of(uiState.toProfileItem(initialConfig).copy(aetherCommand = null)).command
            val customCommand = uiState.aetherCommand.isNotBlank() && uiState.aetherCommand.trim() != builtCommand
            FormTextField(
                stringResource(R.string.aether_lab_command),
                uiState.aetherCommand.ifBlank { builtCommand },
                { uiState.aetherCommand = it },
                maxLines = 8,
                supportingText = if (customCommand) stringResource(R.string.aether_command_custom) else null
            )
            if (customCommand) {
                TextButton(
                    onClick = { uiState.aetherCommand = "" },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.aether_action_use_settings))
                }
            }
            AetherLogPanel(entries = log)
        }

        if (showRenewConfirm) {
            ConfirmDialog(
                message = stringResource(R.string.aether_confirm_renew_key),
                confirmText = stringResource(R.string.aether_action_renew_key),
                onConfirm = {
                    showRenewConfirm = false
                    viewModel.renewIdentity(uiState.toProfileItem(initialConfig))
                },
                onDismiss = { showRenewConfirm = false }
            )
        }
    }

    override fun validateBasicConfig(state: ServerUiState): Boolean {
        if (state.remarks.isBlank()) {
            toast(R.string.server_lab_remarks)
            return false
        }
        return true
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        // The core cannot listen where the local proxy of the app does; Xray would get the port first.
        val problem = AetherFmt.normalize(config, SettingsManager.getLocalProxyPorts()) ?: return true
        toast(
            when (problem) {
                AetherFmt.Problem.INVALID_PEER -> R.string.aether_invalid_endpoint
                AetherFmt.Problem.INVALID_HOP -> R.string.aether_invalid_hop
                AetherFmt.Problem.SHARED_HOP -> R.string.aether_same_hop
                AetherFmt.Problem.INVALID_FRAGMENT -> R.string.aether_invalid_fragment
                AetherFmt.Problem.INVALID_DNS -> R.string.aether_invalid_dns
                AetherFmt.Problem.INVALID_EXIT_LOC -> R.string.aether_invalid_exit_loc
                AetherFmt.Problem.INVALID_LISTEN_PORT -> R.string.aether_invalid_listen_port
                AetherFmt.Problem.LISTEN_PORT_TAKEN -> R.string.aether_listen_port_taken
                AetherFmt.Problem.PSIPHON_NEEDS_MASQUE -> R.string.aether_psiphon_needs_masque
                AetherFmt.Problem.NEXT_PORT_TAKEN -> R.string.aether_next_port_taken
                AetherFmt.Problem.TOR_NEEDS_MASQUE -> R.string.aether_tor_needs_masque
                AetherFmt.Problem.TOR_PSIPHON_CONFLICT -> R.string.aether_tor_psiphon_conflict
                AetherFmt.Problem.TOR_BRIDGES_MISSING -> R.string.aether_tor_bridges_missing
                AetherFmt.Problem.INVALID_COMMAND -> R.string.aether_invalid_command
            }
        )
        return false
    }

    private fun applyScanResult(state: ServerUiState, result: AetherScanResult) {
        if (AetherProtocol.fromString(state.aetherProtocol).twoHops) {
            state.aetherWiwOuter = result.endpoint.toString()
            state.aetherWiwInner = result.innerHop?.toString().orEmpty()
        } else {
            state.address = result.endpoint.host
            state.port = result.endpoint.port.toString()
        }
    }
}

@Composable
private fun AetherDropdownField(
    @StringRes label: Int,
    value: String,
    @ArrayRes entries: Int,
    @ArrayRes values: Int,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    val labels = stringArrayResource(entries)
    val options = stringArrayResource(values)
    FormDropdownField(
        label = stringResource(label),
        value = labels.getOrElse(options.indexOf(value).coerceAtLeast(0)) { "" },
        options = labels.toList(),
        onValueChange = { picked ->
            val index = labels.indexOf(picked)
            if (index >= 0) onValueChange(options[index])
        },
        enabled = enabled
    )
}

@Composable
private fun ProgressMark() {
    CircularProgressIndicator(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(18.dp),
        strokeWidth = 2.dp
    )
}

@Composable
private fun AetherLogPanel(entries: List<AetherLogEntry>) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(entries.lastOrNull()?.id) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.aether_log_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    Utils.setClipboard(context, entries.joinToString("\n") { it.text.resolve(context) })
                    context.toastSuccess(R.string.toast_success)
                },
                enabled = entries.isNotEmpty()
            ) {
                Text(stringResource(R.string.logcat_copy))
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.aether_log_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollbar(listState),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(items = entries, key = { it.id }) { entry ->
                        Text(
                            text = entry.text.asString(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = logColor(entry)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AetherLogText.asString(): String = when (this) {
    is AetherLogText.Raw -> value
    is AetherLogText.Resource -> stringResource(id, *args.toTypedArray())
}

private fun AetherLogText.resolve(context: Context): String = when (this) {
    is AetherLogText.Raw -> value
    is AetherLogText.Resource -> context.getString(id, *args.toTypedArray())
}

@Composable
private fun logColor(entry: AetherLogEntry): Color = when {
    entry.priority >= Log.ERROR -> MaterialTheme.colorScheme.error
    entry.priority == Log.WARN -> MaterialTheme.colorScheme.tertiary
    entry.text is AetherLogText.Resource -> MaterialTheme.colorScheme.onSurface
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * The exit countries the bundled Psiphon fleet actually serves (flag + label
 * فارسی + ISO code), shown as a picker on the Aether editor's "Exit country"
 * field so the code never has to be typed by hand. Blank/خودکار = any country.
 */
private val AETHER_EXIT_COUNTRIES: List<Pair<String, String>> = listOf(
    "🇦🇹 اتریش (Austria)" to "AT",
    "🇦🇺 استرالیا (Australia)" to "AU",
    "🇧🇪 بلژیک (Belgium)" to "BE",
    "🇧🇷 برزیل (Brazil)" to "BR",
    "🇨🇦 کانادا (Canada)" to "CA",
    "🇨🇭 سوئیس (Switzerland)" to "CH",
    "🇨🇿 جمهوری چک (Czechia)" to "CZ",
    "🇩🇪 آلمان (Germany)" to "DE",
    "🇩🇰 دانمارک (Denmark)" to "DK",
    "🇪🇸 اسپانیا (Spain)" to "ES",
    "🇫🇮 فنلاند (Finland)" to "FI",
    "🇫🇷 فرانسه (France)" to "FR",
    "🇬🇧 انگلستان (United Kingdom)" to "GB",
    "🇮🇩 اندونزی (Indonesia)" to "ID",
    "🇮🇪 ایرلند (Ireland)" to "IE",
    "🇮🇳 هند (India)" to "IN",
    "🇮🇹 ایتالیا (Italy)" to "IT",
    "🇯🇵 ژاپن (Japan)" to "JP",
    "🇱🇹 لیتوانی (Lithuania)" to "LT",
    "🇳🇱 هلند (Netherlands)" to "NL",
    "🇳🇴 نروژ (Norway)" to "NO",
    "🇵🇱 لهستان (Poland)" to "PL",
    "🇷🇴 رومانی (Romania)" to "RO",
    "🇷🇸 صربستان (Serbia)" to "RS",
    "🇸🇪 سوئد (Sweden)" to "SE",
    "🇸🇬 سنگاپور (Singapore)" to "SG",
    "🇺🇸 ایالات متحده آمریکا (United States)" to "US",
)
