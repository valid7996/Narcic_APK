package com.narcic.ng.ui.shortcut

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.narcic.ng.core.CoreServiceManager
import com.narcic.ng.core.LauncherManager
import com.narcic.ng.ui.base.BaseComponentActivity

class ScStartActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            moveTaskToBack(true)
            if (!CoreServiceManager.isRunning()) {
                LauncherManager.startServiceFromToggle(this@ScStartActivity)
            }
            finish()
        }
    }
}
