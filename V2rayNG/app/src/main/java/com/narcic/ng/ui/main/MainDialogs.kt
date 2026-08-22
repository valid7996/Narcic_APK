package com.narcic.ng.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.narcic.ng.R
import com.narcic.ng.ui.compose.DeleteConfirmDialog

@Composable
fun MainDialogs(
    showRemoveConfirm: String?,
    onDismissRemove: () -> Unit,
    onConfirmRemove: (String) -> Unit,
    showDelSubscriptionConfirm: String?,
    onDismissDelSubscription: () -> Unit,
    onConfirmDelSubscription: (String) -> Unit,
) {
    if (showRemoveConfirm != null) {
        val guid = showRemoveConfirm
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            onConfirm = { onConfirmRemove(guid) },
            onDismiss = onDismissRemove
        )
    }
    if (showDelSubscriptionConfirm != null) {
        val groupId = showDelSubscriptionConfirm
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_subscription_group),
            onConfirm = { onConfirmDelSubscription(groupId) },
            onDismiss = onDismissDelSubscription
        )
    }
}
