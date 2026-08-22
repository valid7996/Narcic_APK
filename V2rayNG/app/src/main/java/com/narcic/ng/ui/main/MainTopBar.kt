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
 * Simplified top bar for the connection screen: just the title and the
 * "fetch/update subscriptions" action. There is no drawer/menu button — the
 * connection screen is the app's root screen, so no navigation icon is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    isLoading: Boolean,
    onFetchConfig: () -> Unit,
) {
    AppTopBar(
        title = stringResource(R.string.title_server),
        onBackClick = {},
        isLoading = isLoading,
        navigationIcon = {},
        actions = {
            IconButton(onClick = onFetchConfig) {
                Icon(painterResource(R.drawable.ic_cloud_download_24dp), contentDescription = "Get configs")
            }
        }
    )
}
