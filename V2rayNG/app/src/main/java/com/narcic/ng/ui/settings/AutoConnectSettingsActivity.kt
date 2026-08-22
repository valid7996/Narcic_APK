package com.narcic.ng.ui.settings

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.handler.MmkvManager.rememberMmkvBool
import com.narcic.ng.handler.MmkvManager.rememberMmkvString
import com.narcic.ng.ui.base.BaseComponentActivity
import com.narcic.ng.ui.compose.AppTopBar
import com.narcic.ng.ui.compose.SettingsEditItem
import com.narcic.ng.ui.compose.SettingsSwitchItem

/**
 * "تنظیم اتصال خودکار": lets the person decide how long گزینه‌ی خودکار is
 * allowed to keep testing a subscription's configs before it just connects.
 *
 * Off (default): خودکار tests every config in the subscription and connects
 * to the single fastest one — same as before, just now scoped to only the
 * current subscription instead of every subscription.
 *
 * On: خودکار stops testing and connects the moment it finds any config at
 * or under the configured ping, which is much faster on subscriptions with
 * a lot of configs.
 */
class AutoConnectSettingsActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        AutoConnectSettingsScreen(onBackClick = { finish() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoConnectSettingsScreen(onBackClick: () -> Unit) {
    var limitEnabled by rememberMmkvBool(AppConfig.PREF_AUTO_CONNECT_PING_LIMIT_ENABLED, false)
    var limitMs by rememberMmkvString(AppConfig.PREF_AUTO_CONNECT_PING_LIMIT_MS, "300")

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_auto_connect_settings),
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
                text = stringResource(R.string.summary_auto_connect_ping_limit_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsSwitchItem(
                title = stringResource(R.string.title_auto_connect_ping_limit_enabled),
                summary = stringResource(R.string.summary_auto_connect_ping_limit_enabled),
                checked = limitEnabled,
                onCheckedChange = { limitEnabled = it }
            )
            SettingsEditItem(
                title = stringResource(R.string.title_auto_connect_ping_limit_ms),
                value = limitMs,
                enabled = limitEnabled,
                keyboardNumber = true,
                onValueChanged = { limitMs = it.filter(Char::isDigit) }
            )

            Text(
                text = stringResource(R.string.summary_auto_connect_ping_limit_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}
