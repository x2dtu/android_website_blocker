package com.example.androidwebsiteblocker.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.androidwebsiteblocker.TAG
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

    fun show(title: String, subtitle: String, colorArgb: Long) {
        remove()
        val o = OverlayOwner().also { it.start() }
        val accent = Color(colorArgb.toInt())
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            alpha = 0f
            setContent {
                // Full-screen, see-through window; the banner sits near the bottom.
                Box(
                    Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = BOTTOM_OFFSET_DP.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Row(
                        Modifier
                            .padding(horizontal = 24.dp)
                            .background(Color(0xF0151929), RoundedCornerShape(20.dp))
                            .padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.width(4.dp).height(36.dp).background(accent, RoundedCornerShape(2.dp)))
                        Column {
                            Text(title, color = Color.White, style = Typography.titleSmall)
                            Text(subtitle, color = Color(0xFFB4B9D2), style = Typography.labelMedium)
                        }
                    }
                }
            }
        }
        // Same window type and size as the (known-good) fallback block screen, but see-through and
        // ignoring touches, so the app underneath stays fully usable.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { setTitle("NightBlocker time warning") }
        try {
            windowManager.addView(v, params)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Couldn't show time warning banner: $title", e)
            o.destroy()
            return
        }
        Log.i(TAG, "Time warning banner: $title ($subtitle)")
        view = v
        owner = o
        v.animate().alpha(1f).setDuration(FADE_IN_MS).start()
        // Diagnostics: confirms the window really laid out and is visible.
        v.postDelayed({
            Log.i(TAG, "Banner window: ${v.width}x${v.height}, shown=${v.isShown}, attached=${v.isAttachedToWindow}, alpha=${v.alpha}")
        }, 1_000)
        handler.postDelayed({
            view?.animate()?.alpha(0f)?.setDuration(FADE_OUT_MS)?.withEndAction(::remove)?.start()
        }, SHOW_MS)
        handler.postDelayed(::remove, SHOW_MS + FADE_OUT_MS + 200) // in case the animation is skipped
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        view?.let {
            it.animate().cancel()
            runCatching { windowManager.removeView(it) }
        }
        owner?.destroy()
        view = null
        owner = null
    }

    private companion object {
        const val SHOW_MS = 4_500L
        const val FADE_IN_MS = 250L
        const val FADE_OUT_MS = 400L
        const val BOTTOM_OFFSET_DP = 96f
    }
}
