package com.narcic.ng.ui.shortcut

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.narcic.ng.core.CoreServiceManager
import com.narcic.ng.core.LauncherManager
import com.narcic.ng.ui.base.BaseComponentActivity

class ScSwitchActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            moveTaskToBack(true)
            if (CoreServiceManager.isRunning()) {
                LauncherManager.stopService(this@ScSwitchActivity)
            } else {
                LauncherManager.startServiceFromToggle(this@ScSwitchActivity)
            }
            finish()
        }
    }
}
