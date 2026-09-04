package com.narcic.ng.ui.statistics

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.R
import com.narcic.ng.dto.entities.DailyTrafficStat
import com.narcic.ng.extension.toTrafficString
import com.narcic.ng.handler.TrafficStatsManager
import com.narcic.ng.ui.base.BaseComponentActivity
import com.narcic.ng.ui.compose.AppTopBar
import com.narcic.ng.ui.compose.Nc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val HISTORY_DAYS = 7
private const val REFRESH_INTERVAL_MS = 5000L

class StatisticsActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        StatisticsScreen(onBackClick = { finish() })
    }
}

/**
 * Daily/weekly VPN usage: connected time, download, upload and combined
 * total, backed by TrafficStatsManager (which the VPN service process
 * feeds from the same traffic-query loop that drives the live speed
 * notification, see NotificationManager.updateSpeedNotificationOnce).
 *
 * Refreshes every few seconds while this screen is open so numbers keep
 * moving if the VPN is connected right now; the refresh loop is scoped to
 * the composition, so it stops automatically once the user leaves.
 *
 * All numbers here come straight from [TrafficStatsManager] -- there is no
 * per-server or per-test-run breakdown recorded anywhere in the app, so
 * (unlike the connect screen's redesign spec) this screen does not attempt
 * a "by server" or "speed-test history" section; inventing numbers for
 * those would just be misleading.
 */
@Composable
fun StatisticsScreen(onBackClick: () -> Unit) {
    var days by remember { mutableStateOf<List<DailyTrafficStat>?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            days = withContext(Dispatchers.IO) { TrafficStatsManager.getRecentDays(HISTORY_DAYS) }
            delay(REFRESH_INTERVAL_MS)
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        containerColor = Nc.Bg,
        topBar = {
            AppTopBar(
                title = "آمار",
                onBackClick = onBackClick,
            )
        }
    ) { innerPadding ->
        val currentDays = days
        if (currentDays == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Nc.Cyan)
            }
            return@Scaffold
        }

        val today = currentDays.lastOrNull() ?: DailyTrafficStat()
        val week = TrafficStatsManager.sum(currentDays)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatSummaryCard(title = "امروز", stat = today)
            StatSummaryCard(title = "۷ روز اخیر", stat = week)

            SectionTitle("مصرف ۷ روز اخیر")
            WeekChartCard(days = currentDays)

            SectionTitle("روزهای اخیر")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // currentDays is oldest-first; reversed() shows today first
                // with daysAgo matching each row's position (0, 1, 2, ...).
                currentDays.asReversed().forEachIndexed { daysAgo, stat ->
                    DailyStatRow(daysAgo = daysAgo, stat = stat)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Nc.Sub,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
    )
}

@Composable
private fun StatSummaryCard(title: String, stat: DailyTrafficStat) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text(
            text = title,
            color = Nc.Txt,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricChip(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_arrow_downward_24dp,
                accent = Nc.Cyan,
                label = "دانلود",
                value = stat.downloadBytes.toTrafficString(),
            )
            MetricChip(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_arrow_upward_24dp,
                accent = Nc.Violet,
                label = "آپلود",
                value = stat.uploadBytes.toTrafficString(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricChip(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_stats_24dp,
                accent = Nc.Amber,
                label = "مجموع مصرف",
                value = stat.totalBytes.toTrafficString(),
            )
            MetricChip(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_timer_24dp,
                accent = Nc.Amber,
                label = "زمان اتصال",
                value = formatConnectedDuration(stat.connectedMillis),
            )
        }
    }
}

/**
 * 7-day download/upload bar chart, gradients per the redesign spec
 * (download: #67E8F9→#0891B2, upload: #A78BFA→#6D28D9). Backed by the same
 * real [DailyTrafficStat] list as the rest of the screen.
 */
@Composable
private fun WeekChartCard(days: List<DailyTrafficStat>) {
    val maxVal = days.maxOfOrNull { maxOf(it.downloadBytes, it.uploadBytes) }?.coerceAtLeast(1L) ?: 1L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = .06f))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { i, day ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        WeekBar(day.downloadBytes.toFloat() / maxVal, Brush.verticalGradient(listOf(Nc.ChartDownloadTop, Nc.ChartDownloadBottom)))
                        WeekBar(day.uploadBytes.toFloat() / maxVal, Brush.verticalGradient(listOf(Nc.ChartUploadTop, Nc.ChartUploadBottom)))
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        TrafficStatsManager.dayLabel(days.size - 1 - i),
                        color = Nc.Sub, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            LegendDot(Nc.ChartDownloadTop, "دانلود")
            Spacer(Modifier.width(16.dp))
            LegendDot(Nc.ChartUploadTop, "آپلود")
        }
    }
}

@Composable
private fun WeekBar(fraction: Float, brush: Brush) {
    Box(
        Modifier
            .width(10.dp)
            .fillMaxHeight(fraction.coerceIn(.02f, 1f))
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
            .background(brush)
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Nc.Sub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DailyStatRow(daysAgo: Int, stat: DailyTrafficStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = TrafficStatsManager.dayLabel(daysAgo),
            color = Nc.Txt,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatConnectedDuration(stat.connectedMillis),
            color = Nc.Sub,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stat.totalBytes.toTrafficString(),
            color = Nc.Cyan,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricChip(
    modifier: Modifier = Modifier,
    icon: Int,
    accent: Color,
    label: String,
    value: String,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                color = Nc.Sub,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = Nc.Txt,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** e.g. 0 -> "0 دقیقه", 45 min -> "45 دقیقه", 125 min -> "2 ساعت 5 دقیقه". */
private fun formatConnectedDuration(connectedMillis: Long): String {
    val totalMinutes = connectedMillis / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours ساعت $minutes دقیقه"
        hours > 0 -> "$hours ساعت"
        else -> "$minutes دقیقه"
    }
}
