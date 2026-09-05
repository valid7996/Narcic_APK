package com.narcic.ng.ui.main

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.narcic.ng.aether.core.ConnectionController
import com.narcic.ng.aether.service.AetherVpnService
import com.narcic.ng.aether.shared.model.AetherProtocol
import com.narcic.ng.aether.shared.model.ConnectionStatus
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc

/**
 * Third engine tab: the AetherST tunnel (MASQUE / WireGuard / Gool /
 * Cloudflare Zero Trust), ported from github.com/immaghzbad/AetherST.
 * Mirrors that client's minimal dashboard: protocol picker + big toggle +
 * live status, driven by the shared ConnectionController state.
 */
@Composable
fun AetherScreen() {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val card = if (isDark) Nc.Bg.copy(alpha = .55f) else androidx.compose.ui.graphics.Color.White.copy(alpha = .85f)
    val stroke = if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = .08f) else androidx.compose.ui.graphics.Color.Black.copy(alpha = .06f)
    val accent = Nc.Cyan

    // Protocol state is persisted through AetherConfigRepository (same path
    // the home-screen widget uses) so the picker actually reaches the engine —
    // a local remember{} alone would be discarded before the START intent.
    val configRepository = remember {
        com.narcic.ng.aether.shared.data.AetherConfigRepository.getInstance(
            com.narcic.ng.aether.platform.getSettings(
                com.narcic.ng.aether.platform.PlatformContext(context)
            )
        )
    }
    var protocol by remember {
        mutableStateOf(configRepository.config.value.protocol)
    }
    var status by remember { mutableStateOf(ConnectionStatus.STOPPED) }

    LaunchedEffect(Unit) {
        while (true) {
            status = ConnectionController.status.value
            kotlinx.coroutines.delay(700)
        }
    }

    val running = status != ConnectionStatus.STOPPED && status != ConnectionStatus.ERROR && status != ConnectionStatus.FAILED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Header card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(card)
                .border(1.dp, stroke, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Text("Aether Tunnel", color = Nc.Txt, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "ماسک (MASQUE)، وایرگارد، گول و کلادفلر Zero Trust — با زنجیره اختیاری Psiphon",
                color = Nc.Sub,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Protocol picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AetherProtocol.entries.forEach { p ->
                val selected = p == protocol
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) accent.copy(alpha = .14f) else card)
                        .border(
                            1.dp,
                            if (selected) accent else stroke,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable {
                            protocol = p
                            if (configRepository.config.value.protocol != p) {
                                configRepository.updateConfig(
                                    configRepository.config.value.copy(protocol = p)
                                )
                            }
                        }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        p.displayName,
                        color = if (selected) accent else Nc.Txt,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Status + toggle card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(card)
                .border(1.dp, stroke, RoundedCornerShape(20.dp))
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when (status) {
                    ConnectionStatus.STOPPED -> "خاموش"
                    ConnectionStatus.STARTING, ConnectionStatus.VALIDATING -> "در حال اتصال..."
                    ConnectionStatus.DATAPLANE_VALIDATED, ConnectionStatus.SOCKS_READY, ConnectionStatus.TUN_ACTIVE -> "در حال راه‌اندازی..."
                    ConnectionStatus.RUNNING -> "متصل"
                    ConnectionStatus.RECONNECTING -> "اتصال مجدد..."
                    ConnectionStatus.STOPPING -> "در حال قطع..."
                    ConnectionStatus.ERROR, ConnectionStatus.FAILED -> "خطا"
                },
                color = if (status == ConnectionStatus.RUNNING) Nc.Green else Nc.Sub,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            // Big round toggle
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (running) {
                            Brush.linearGradient(listOf(accent, Nc.Violet))
                        } else {
                            Brush.linearGradient(listOf(Nc.Stroke, Nc.Stroke))
                        }
                    )
                    .border(2.dp, if (running) accent.copy(alpha = .6f) else stroke, RoundedCornerShape(50))
                    .clickable {
                        val action = if (running) AetherVpnService.ACTION_STOP else AetherVpnService.ACTION_START
                        val intent = Intent(context, AetherVpnService::class.java).apply { this.action = action }
                        if (running) context.startService(intent) else ContextCompat.startForegroundService(context, intent)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (running) "قطع" else "اتصال",
                    color = if (running) androidx.compose.ui.graphics.Color(0xFF070B14) else Nc.Txt,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
