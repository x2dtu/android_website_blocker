package com.example.androidwebsiteblocker.blocking

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
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
import kotlin.math.abs

/**
 * Samsung-style time warning: a small banner at the top of the screen over the app in use
 * ("🌙 Instagram · 10 minutes left", block set name underneath). Swipe it sideways or up, or tap
 * it, to dismiss; otherwise it goes away after a few seconds. Its window is only as big as the
 * banner, so the rest of the screen stays usable. Uses an accessibility overlay: no extra
 * permission, and not affected by notification settings.
 *
 * No animations: on some devices they never run in overlay windows (that left earlier versions
 * invisible). Dragging moves the banner directly with the finger instead.
 */
class TimeWarningBanner(service: AccessibilityService) {
    private val context = service
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val density = service.resources.displayMetrics.density
    private var container: FrameLayout? = null
    private var owner: OverlayOwner? = null
    /** Auto-dismiss came due mid-drag: dismiss when the finger lifts. */
    private var dismissOnRelease = false
    private var dragging = false

    fun show(title: String, subtitle: String, colorArgb: Long) {
        remove()
        val o = OverlayOwner().also { it.start() }
        val accent = Color(colorArgb.toInt())
        val content = ComposeView(context).apply {
            setContent {
                Row(
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
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
        val frame = FrameLayout(context).apply {
            // Compose looks these up from the window's root view, so they go on the container.
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            clipChildren = false
            addView(content)
            setOnTouchListener(SwipeToDismiss(content))
        }
        // Banner-sized window at the top, just under the status bar. Not focusable, so it never
        // takes the keyboard, and touches outside it go to the app as usual.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight() + (TOP_OFFSET_DP * density).toInt()
            setTitle("NightBlocker time warning")
        }
        try {
            windowManager.addView(frame, params)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Couldn't show time warning banner: $title", e)
            o.destroy()
            return
        }
        Log.i(TAG, "Time warning banner: $title ($subtitle)")
        container = frame
        owner = o
        dismissOnRelease = false
        handler.postDelayed({ if (dragging) dismissOnRelease = true else remove() }, SHOW_MS)
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        container?.let { runCatching { windowManager.removeView(it) } }
        owner?.destroy()
        container = null
        owner = null
        dragging = false
    }

    /** Moves the banner with the finger; far enough sideways or up dismisses it, a tap does too. */
    private inner class SwipeToDismiss(private val target: android.view.View) : android.view.View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var moved = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: android.view.View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    moved = false
                    dragging = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = (e.rawY - downY).coerceAtMost(0f) // only upwards
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    target.translationX = dx
                    target.translationY = dy
                    target.alpha = (1f - abs(dx) / (target.width.coerceAtLeast(1))).coerceIn(0.2f, 1f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    val swipedAway = abs(dx) > target.width * DISMISS_FRACTION || dy < -DISMISS_UP_DP * density
                    val tapped = e.actionMasked == MotionEvent.ACTION_UP && !moved
                    if (swipedAway || tapped || dismissOnRelease) {
                        remove()
                    } else {
                        target.translationX = 0f
                        target.translationY = 0f
                        target.alpha = 1f
                    }
                }
            }
            return true
        }
    }

    private fun statusBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            return windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        }
        @SuppressLint("DiscouragedApi", "InternalInsetResource")
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else (24 * density).toInt()
    }

    private companion object {
        const val SHOW_MS = 4_500L
        const val TOP_OFFSET_DP = 8f
        const val DISMISS_FRACTION = 0.3f
        const val DISMISS_UP_DP = 24f
    }
}
