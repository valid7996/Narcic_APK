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
 * Top bar for the connection screen: a drawer/menu button on the top-left
 * (import config via link/QR + bulk manage actions live in that drawer),
 * the title, and the "fetch/update subscriptions" action on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    isLoading: Boolean,
    onFetchConfig: () -> Unit,
    onMenuClick: () -> Unit,
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
        }
    )
}
