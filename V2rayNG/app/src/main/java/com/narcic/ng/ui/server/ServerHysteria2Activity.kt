package com.narcic.ng.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.extension.toast
import com.narcic.ng.ui.compose.FormTextField
import com.narcic.ng.ui.compose.SettingsSwitchItem

class ServerHysteria2Activity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.HYSTERIA2

    @Composable
    override fun ScreenContent() {
        val scope = rememberCoroutineScope()
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.HYSTERIA2
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState, showPort = false)
            Hysteria2ProtocolFields(uiState)

        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.password.isNullOrBlank()) {
            toast(R.string.server_lab_id3)
            return false
        }
        if (config.security.isNullOrBlank()) {
            config.security = AppConfig.TLS
        }
        return true
    }

    @Composable
    private fun Hysteria2ProtocolFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_id3),
            state.password,
            { state.password = it }
        )
        FormTextField(
            stringResource(R.string.server_obfs_password),
            state.obfsPassword,
            { state.obfsPassword = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_port_hop),
            state.portHopping,
            { state.portHopping = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_port_hop_interval),
            state.portHoppingInterval,
            { state.portHoppingInterval = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_bandwidth_down),
            state.bandwidthDown,
            { state.bandwidthDown = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_bandwidth_up),
            state.bandwidthUp,
            { state.bandwidthUp = it }
        )

        SettingsSwitchItem(
            title = stringResource(R.string.server_lab_allow_insecure),
            checked = state.allowInsecure,
            onCheckedChange = { state.allowInsecure = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_sni),
            state.sni,
            { state.sni = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_pinned_ca256),
            state.pinnedCA256,
            { state.pinnedCA256 = it }
        )
    }
}

