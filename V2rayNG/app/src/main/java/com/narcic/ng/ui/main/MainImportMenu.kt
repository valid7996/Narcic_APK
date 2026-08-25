package com.narcic.ng.ui.main

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.narcic.ng.R
import com.narcic.ng.AppConfig
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.extension.isComplexType
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.ui.compose.SelectListDialog
import com.narcic.ng.util.Utils
import com.narcic.ng.enums.EConfigType

internal enum class ServerMenuAction(
    @StringRes val labelRes: Int,
    val isShareAction: Boolean,
    val supportsComplexProfiles: Boolean,
) {
    ShareQRCode(R.string.share_method_qrcode, isShareAction = true, supportsComplexProfiles = false),
    ShareClipboard(R.string.share_method_clipboard, isShareAction = true, supportsComplexProfiles = false),
    ShareFullContent(R.string.share_method_full_content, isShareAction = true, supportsComplexProfiles = true),
    ShareLink(R.string.share_method_link, isShareAction = true, supportsComplexProfiles = true),
    Edit(R.string.action_edit, isShareAction = false, supportsComplexProfiles = true),
    Delete(R.string.action_delete, isShareAction = false, supportsComplexProfiles = true),
}

internal fun serverMenuActions(
    isComplexProfile: Boolean,
    includeManagementActions: Boolean,
    isFromDefaultSubscription: Boolean = false,
): List<ServerMenuAction> = ServerMenuAction.entries.filter { action ->
    (includeManagementActions || action.isShareAction)
        && (!isComplexProfile || action.supportsComplexProfiles)
        && (action == ServerMenuAction.Delete || !isFromDefaultSubscription)
}

private fun profileIsFromDefaultSubscription(profile: ProfileItem): Boolean {
    val subUrl = MmkvManager.decodeSubscription(profile.subscriptionId)?.url ?: return false
    return AppConfig.isDefaultSubscriptionUrl(subUrl)
}

@Composable
fun ShareMethodDialog(
    guid: String,
    profile: ProfileItem,
    more: Boolean,
    onDismiss: () -> Unit,
    onAction: (MainAction) -> Unit,
    onRemove: (String) -> Unit,
) {
    val menuActions = serverMenuActions(
        isComplexProfile = profile.configType.isComplexType(),
        includeManagementActions = more,
        isFromDefaultSubscription = profileIsFromDefaultSubscription(profile),
    )
    SelectListDialog(
        options = menuActions.map { stringResource(it.labelRes) },
        onSelected = { index: Int, _ ->  
            onDismiss()
            when (menuActions[index]) {
                ServerMenuAction.ShareQRCode -> onAction(MainAction.ShareQRCode(guid))
                ServerMenuAction.ShareClipboard -> onAction(MainAction.ShareClipboard(guid))
                ServerMenuAction.ShareFullContent -> onAction(MainAction.ShareFullContent(guid))
                ServerMenuAction.ShareLink -> onAction(MainAction.ShareLink(guid))
                ServerMenuAction.Edit -> onAction(MainAction.EditServer(guid, profile))
                ServerMenuAction.Delete -> onRemove(guid)
            }
        },
        onDismiss = onDismiss
    )
}
