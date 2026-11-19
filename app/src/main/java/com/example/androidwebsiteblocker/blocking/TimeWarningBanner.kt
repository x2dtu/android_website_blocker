package com.example.androidwebsiteblocker.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.androidwebsiteblocker.ui.theme.Typography

/**
 * Samsung-style time warning: a small banner over the app in use ("🌙 Instagram · 10 minutes
 * left", block set name underneath) that fades out after a few seconds. It ignores touches, so
 * you can keep using the app through it. Uses an accessibility overlay: no extra permission, and
 * not affected by notification settings.
 */
class TimeWarningBanner(service: AccessibilityService) {
    private val context = service
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var visibility: MutableTransitionState<Boolean>? = null

    fun show(title: String, subtitle: String, colorArgb: Long) {
        remove()
        val state = MutableTransitionState(false).apply { targetState = true }
        val o = OverlayOwner().also { it.start() }
        val accent = Color(colorArgb.toInt())
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                AnimatedVisibility(
                    visibleState = remember { state },
                    enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
                    exit = fadeOut(tween(400)),
                ) {
                    Row(
                        Modifier
                            .padding(horizontal = 24.dp)
                            .background(Color(0xF0151929), RoundedCornerShape(20.dp))
                            .padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.width(4.dp).height(36.dp).fillMaxHeight().background(accent, RoundedCornerShape(2.dp)))
                        Column {
                            Text(title, color = Color.White, style = Typography.titleSmall)
                            Text(subtitle, color = Color(0xFFB4B9D2), style = Typography.labelMedium)
                        }
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (context.resources.displayMetrics.density * BOTTOM_OFFSET_DP).toInt() // clear of the nav bar
            setTitle("NightBlocker time warning")
        }
        windowManager.addView(v, params)
        view = v
        owner = o
        visibility = state
        handler.postDelayed({ visibility?.targetState = false }, SHOW_MS)
        handler.postDelayed(::remove, SHOW_MS + FADE_MS)
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { windowManager.removeView(it) } }
        owner?.destroy()
        view = null
        owner = null
        visibility = null
    }

    private companion object {
        const val SHOW_MS = 4_500L
        const val FADE_MS = 500L
        const val BOTTOM_OFFSET_DP = 96f
    }
}
