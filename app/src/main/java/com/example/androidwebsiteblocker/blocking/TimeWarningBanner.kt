package com.example.androidwebsiteblocker.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
        // Content is laid out at full size from the first frame (a window that starts empty can
        // stay 0×0 on some devices); fading is done on the View instead.
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            alpha = 0f
            translationY = 24f * resources.displayMetrics.density
            setContent {
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
        v.animate().alpha(1f).translationY(0f).setDuration(FADE_IN_MS).start()
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
