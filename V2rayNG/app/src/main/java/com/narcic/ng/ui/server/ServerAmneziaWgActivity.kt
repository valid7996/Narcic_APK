package com.narcic.ng.ui.server

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.extension.toast
import com.narcic.ng.extension.toastSuccess
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.ui.base.BaseComponentActivity
import com.narcic.ng.ui.compose.AppTopBar
import com.narcic.ng.ui.compose.DeleteConfirmDialog
import com.narcic.ng.ui.compose.FormTextField
import com.narcic.ng.util.LogUtil
import org.amnezia.awg.config.Config
import java.io.BufferedReader
import java.io.StringReader

/**
 * Dedicated editor for AmneziaWG profiles ([EConfigType.AMNEZIAWG]).
 *
 * These profiles store their entire connection info as raw wg-quick-style .conf text
 * ([ProfileItem.awgConfigText]) rather than in the usual structured fields, and are
 * connected via the separate [AwgManager] engine — never through Xray. Previously this
 * config type had no branch in [MainActivity.editServer]'s activity-class lookup, so it
 * fell through to the generic HTTP editor, which showed the config as if it were an
 * HTTP proxy. This screen edits the raw config text directly instead.
 */
class ServerAmneziaWgActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }

    private var initialRemarks: String = ""
    private var initialConfigText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = MmkvManager.decodeServerConfig(editGuid)
        initialRemarks = config?.remarks?.ifBlank { "AmneziaWG" } ?: "AmneziaWG"
        initialConfigText = config?.awgConfigText.orEmpty()
    }

    @Composable
    override fun ScreenContent() {
        ServerAmneziaWgScreen(
            editGuid = editGuid,
            isRunning = isRunning,
            initialRemarks = initialRemarks,
            initialConfigText = initialConfigText,
            onBackClick = { finish() },
            onSave = { remarks, configText -> saveServer(remarks, configText) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(remarks: String, rawConfigText: String): Boolean {
        if (remarks.isBlank()) {
            toast(R.string.server_lab_remarks)
            return false
        }
        if (rawConfigText.isBlank()) {
            toast(R.string.toast_config_file_invalid)
            return false
        }

        // Strip stray CR / trailing whitespace / invisible zero-width characters that often
        // ride along when a config is pasted from Telegram bots or web pages — these look fine
        // visually but break base64 key decoding (org.amnezia.awg.crypto.KeyFormatException)
        // for some configs and not others depending on the source.
        val configText = com.narcic.ng.awg.AwgManager.sanitizeConfigText(rawConfigText)

        try {
            // Validate with the same parser used to actually connect, so a malformed
            // config is caught here rather than at connect time.
            Config.parse(BufferedReader(StringReader(configText)))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse AmneziaWG configuration", e)
            toast("${getString(R.string.toast_config_file_invalid)} ${e.message.orEmpty()}")
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid)
            ?: ProfileItem.create(EConfigType.AMNEZIAWG)

        // configType is a val (immutable) on ProfileItem; the loaded profile is already
        // AMNEZIAWG (this screen is only reached for that type) and the fallback above
        // is already constructed with it, so no reassignment is needed here.
        config.remarks = remarks
        config.awgConfigText = configText

        val savedGuid = MmkvManager.encodeServerConfig(editGuid, config)

        toastSuccess(R.string.toast_success)

        ProfileEditorResult.run {
            // AmneziaWG runs on its own engine (AwgManager), never on Xray/CoreVpnService,
            // so there is no Xray "restart" to trigger here regardless of running state.
            finishSaved(guid = savedGuid, restartService = false)
        }

        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isEmpty()) {
            return false
        }

        if (editGuid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return false
        }

        MmkvManager.removeServer(editGuid)

        ProfileEditorResult.run {
            finishDeleted(editGuid)
        }

        return true
    }
}

@Composable
fun ServerAmneziaWgScreen(
    editGuid: String,
    isRunning: Boolean,
    initialRemarks: String,
    initialConfigText: String,
    onBackClick: () -> Unit,
    onSave: (String, String) -> Boolean,
    onDelete: () -> Unit
) {
    var remarks by rememberSaveable { mutableStateOf(initialRemarks) }
    var configText by rememberSaveable { mutableStateOf(initialConfigText) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val showDelete = editGuid.isNotEmpty() && !isRunning

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = "AmneziaWG",
                onBackClick = onBackClick,
                actions = {
                    if (showDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                painterResource(R.drawable.ic_delete_24dp),
                                contentDescription = stringResource(R.string.menu_item_del_config)
                            )
                        }
                    }
                    IconButton(onClick = { onSave(remarks, configText) }) {
                        Icon(
                            painterResource(R.drawable.ic_fab_check),
                            contentDescription = stringResource(R.string.menu_item_save_config)
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            FormTextField(
                label = stringResource(R.string.server_lab_remarks),
                value = remarks,
                onValueChange = { remarks = it }
            )

            OutlinedTextField(
                value = configText,
                onValueChange = { configText = it },
                label = { Text("AmneziaWG .conf") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
