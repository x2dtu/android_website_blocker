package com.example.androidwebsiteblocker.blocking

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import android.graphics.Color as AndroidColor

/**
 * The block screen, as a regular full-screen app screen in its own task (hidden from Recents).
 * Being a real activity means the status bar and navigation bar behave like in any app, and
 * Back works natively. Content comes from [BlockScreenHost]; it closes itself when the model
 * goes null.
 */
class BlockActivity : ComponentActivity() {

    /** Counts each time the screen comes back into view; part of the reset key so the wait and puzzle start over. */
    private var visits by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark scrims → light status/nav icons over the dark block screen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        noTransitions()
        setContent {
            val model by BlockScreenHost.model.collectAsState()
            BackHandler { BlockScreenHost.onLeave() }
            val m = model
            if (m == null) {
                LaunchedEffect(Unit) { finishQuietly() }
            } else {
                BlockScreen(m.copy(resetKey = "${m.resetKey}#$visits"), onGoHome = { BlockScreenHost.onLeave() }, onContinue = { BlockScreenHost.onContinue() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        BlockScreenHost.visible = true
        visits++
    }

    override fun onStop() {
        super.onStop()
        BlockScreenHost.visible = false
        if (!isFinishing && BlockScreenHost.model.value != null) BlockScreenHost.onHiddenWhileBlocking()
    }

    private fun finishQuietly() {
        finish()
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    /** No slide in/out: the block screen should just appear and disappear. */
    private fun noTransitions() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
