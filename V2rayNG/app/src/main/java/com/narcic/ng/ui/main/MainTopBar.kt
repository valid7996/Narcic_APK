package com.narcic.ng.ui.main

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.narcic.ng.R
import com.narcic.ng.ui.compose.AppTopBar

/**
 * Top bar for the connection screen: the title, the "fetch/update
 * subscriptions" action, the ⋯ "more" sheet (subscriptions / statistics /
 * logs / backup / routing / import drawer) and the settings gear — the three
 * entries that used to live in the 4-item bottom nav before the 3-page
 * redesign moved the bottom bar to the engine tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    isLoading: Boolean,
    onFetchConfig: () -> Unit,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
) {
    AppTopBar(
        title = stringResource(R.string.title_server),
        onBackClick = {},
        isLoading = isLoading,
        navigationIcon = {
// Menu button hidden per UI redesign
        },
        actions = {
            IconButton(onClick = onFetchConfig) {
                Icon(painterResource(R.drawable.ic_cloud_download_24dp), contentDescription = "Get configs")
            }
            IconButton(onClick = onMoreClick) {
                Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = "More")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = "Settings")
            }
        }
    )
}
