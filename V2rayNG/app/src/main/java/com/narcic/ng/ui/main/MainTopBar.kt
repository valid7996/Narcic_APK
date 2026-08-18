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
 * Simplified top bar for the connection screen: just the drawer menu and the
 * "fetch/update subscriptions" action. Search, manual import, and the config
 * management menu have moved into the drawer (see MainDrawerContent) to keep
 * this screen uncluttered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    isLoading: Boolean,
    onMenuClick: () -> Unit,
    onFetchConfig: () -> Unit,
) {
    AppTopBar(
        title = stringResource(R.string.title_server),
        onBackClick = {},
        isLoading = isLoading,
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(painterResource(R.drawable.ic_menu_24dp), contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = onFetchConfig) {
                Icon(painterResource(R.drawable.ic_cloud_download_24dp), contentDescription = "Get configs")
            }
        }
    )
}
