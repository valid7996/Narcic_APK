package com.narcic.ng.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.handler.MmkvManager.rememberMmkvBool
import com.narcic.ng.handler.MmkvManager.rememberMmkvString
import com.narcic.ng.rsta.NarcisSpoofConfig
import com.narcic.ng.rsta.NarcisSpoofEngine
import com.narcic.ng.service.NarcisSpoofService
import com.narcic.ng.ui.base.BaseComponentActivity
import com.narcic.ng.ui.compose.AppTopBar
import com.narcic.ng.ui.compose.SettingsEditItem
import com.narcic.ng.ui.compose.SettingsListItem
import com.narcic.ng.ui.compose.SettingsMenuItem
import com.narcic.ng.ui.compose.SettingsSwitchItem
import kotlinx.coroutines.delay

/**
 * تنظیمات «نرسیس اسپوف» — a local SNI-spoofing / TLS-fragmentation forwarder
 * ported from the RSTA Spoof feature. Lets the person pick a real remote
 * IP:port, a fake SNI to present during the TLS handshake, and an obfuscation
 * method; the engine then listens on 127.0.0.1:[NarcisSpoofConfig.LISTEN_PORT].
 * To route a server through it, create a server entry whose address/port
 * point at that loopback listener — [com.narcic.ng.core.CoreServiceManager]
 * starts/stops the engine automatically around the VPN connection.
 */
class NarcisSpoofSettingActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        NarcisSpoofSettingScreen(onBackClick = { finish() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NarcisSpoofSettingScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current

    var connectIp by rememberMmkvString(AppConfig.PREF_NARCIS_SPOOF_CONNECT_IP, NarcisSpoofConfig.DEFAULT_CONNECT_IP)
    var connectPort by rememberMmkvString(AppConfig.PREF_NARCIS_SPOOF_CONNECT_PORT, NarcisSpoofConfig.DEFAULT_CONNECT_PORT)
    var fakeSni by rememberMmkvString(AppConfig.PREF_NARCIS_SPOOF_FAKE_SNI, NarcisSpoofConfig.DEFAULT_FAKE_SNI)
    var method by rememberMmkvString(AppConfig.PREF_NARCIS_SPOOF_METHOD, NarcisSpoofConfig.DEFAULT_METHOD)
    var enabled by rememberMmkvBool(AppConfig.PREF_NARCIS_SPOOF_ENABLED, NarcisSpoofConfig.DEFAULT_ENABLED)

    var statusText by remember { mutableStateOf("") }
    var showLogDialog by remember { mutableStateOf(false) }

    // Poll status every second while this screen is visible.
    LaunchedEffect(Unit) {
        while (true) {
            statusText = NarcisSpoofEngine.statusSummary()
            delay(1000)
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_narcis_spoof_setting),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.title_narcis_spoof_explain, NarcisSpoofConfig.LISTEN_PORT),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsEditItem(
                title = stringResource(R.string.title_narcis_spoof_connect_ip),
                value = connectIp,
                onValueChanged = { connectIp = it.trim() }
            )
            SettingsEditItem(
                title = stringResource(R.string.title_narcis_spoof_connect_port),
                value = connectPort,
                keyboardNumber = true,
                onValueChanged = { connectPort = it.filter(Char::isDigit) }
            )
            SettingsEditItem(
                title = stringResource(R.string.title_narcis_spoof_fake_sni),
                value = fakeSni,
                onValueChanged = { fakeSni = it.trim() }
            )
            SettingsListItem(
                title = stringResource(R.string.title_narcis_spoof_method),
                entries = NarcisSpoofConfig.METHODS,
                values = NarcisSpoofConfig.METHODS,
                selectedValue = method,
                onSelected = { method = it }
            )

            Text(
                text = stringResource(R.string.category_narcis_spoof_status),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SettingsMenuItem(
                title = stringResource(R.string.title_narcis_spoof_status),
                subtitle = statusText,
                onClick = {}
            )

            SettingsSwitchItem(
                title = stringResource(R.string.title_narcis_spoof_enabled),
                summary = stringResource(R.string.summary_narcis_spoof_enabled),
                checked = enabled,
                onCheckedChange = { on ->
                    enabled = on
                    val intent = Intent(context, NarcisSpoofService::class.java)
                    if (on) {
                        val port = connectPort.toIntOrNull()
                        if (port == null || port !in 1..65535) {
                            enabled = false
                            return@SettingsSwitchItem
                        }
                        intent.action = "START"
                        intent.putExtra("IP", connectIp)
                        intent.putExtra("PORT", port)
                        intent.putExtra("SNI", fakeSni)
                        intent.putExtra("METHOD", method)
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        intent.action = "STOP"
                        context.startService(intent)
                    }
                }
            )

            SettingsMenuItem(
                title = stringResource(R.string.title_narcis_spoof_view_log),
                subtitle = stringResource(R.string.summary_narcis_spoof_view_log),
                onClick = { showLogDialog = true }
            )

            Text(
                text = stringResource(
                    R.string.summary_narcis_spoof_footer,
                    NarcisSpoofConfig.LISTEN_HOST,
                    NarcisSpoofConfig.LISTEN_PORT
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }

    if (showLogDialog) {
        val lines = NarcisSpoofEngine.recentLogLines()
        val text = if (lines.isEmpty()) {
            stringResource(R.string.title_narcis_spoof_log_empty)
        } else {
            lines.joinToString("\n")
        }
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text(stringResource(R.string.title_narcis_spoof_view_log)) },
            text = {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}
