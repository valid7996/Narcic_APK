package com.narcic.ng.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.narcic.ng.R
import com.narcic.ng.ui.AboutActivity
import com.narcic.ng.ui.base.BaseComponentActivity
import com.narcic.ng.ui.checkupdate.CheckUpdateActivity
import com.narcic.ng.ui.compose.AppDivider
import com.narcic.ng.ui.compose.AppTopBar
import com.narcic.ng.ui.compose.SettingsMenuItem
import com.narcic.ng.ui.perappproxy.PerAppProxyActivity
import com.narcic.ng.ui.userasset.UserAssetActivity

/**
 * "تنظیمات" hub — reached from the bottom-nav تنظیمات tab. Groups the دقیق
 * technical settings screen together with the new تنظیم اتصال خودکار option,
 * and hosts what used to be the top-bar's سه‌نقطه menu (Per-app settings /
 * Asset files / Check for update / About) so the VPN screen stays uncluttered.
 */
class SettingsHubActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        SettingsHubScreen(
            onBackClick = { finish() },
            onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            onOpenAutoConnectSettings = { startActivity(Intent(this, AutoConnectSettingsActivity::class.java)) },
            onOpenNarcisSpoofSettings = { startActivity(Intent(this, NarcisSpoofSettingActivity::class.java)) },
            onOpenPerAppProxy = { startActivity(Intent(this, PerAppProxyActivity::class.java)) },
            onOpenUserAsset = { startActivity(Intent(this, UserAssetActivity::class.java)) },
            onOpenCheckUpdate = { startActivity(Intent(this, CheckUpdateActivity::class.java)) },
            onOpenAbout = { startActivity(Intent(this, AboutActivity::class.java)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onBackClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAutoConnectSettings: () -> Unit,
    onOpenNarcisSpoofSettings: () -> Unit,
    onOpenPerAppProxy: () -> Unit,
    onOpenUserAsset: () -> Unit,
    onOpenCheckUpdate: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_settings),
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
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_settings_24dp),
                title = stringResource(R.string.title_settings),
                subtitle = stringResource(R.string.title_settings_hub_settings_subtitle),
                onClick = onOpenSettings
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_flash_on_24dp),
                title = stringResource(R.string.title_auto_connect_settings),
                subtitle = stringResource(R.string.title_auto_connect_settings_subtitle),
                onClick = onOpenAutoConnectSettings
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.narcis_3d_shield),
                title = stringResource(R.string.title_narcis_spoof_setting),
                subtitle = stringResource(R.string.title_narcis_spoof_setting_subtitle),
                onClick = onOpenNarcisSpoofSettings
            )

            AppDivider()

            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_per_apps_24dp),
                title = stringResource(R.string.per_app_proxy_settings),
                onClick = onOpenPerAppProxy
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_file_24dp),
                title = stringResource(R.string.title_user_asset_setting),
                onClick = onOpenUserAsset
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_check_update_24dp),
                title = stringResource(R.string.update_check_for_update),
                onClick = onOpenCheckUpdate
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_about_24dp),
                title = stringResource(R.string.title_about),
                onClick = onOpenAbout
            )
        }
    }
}
