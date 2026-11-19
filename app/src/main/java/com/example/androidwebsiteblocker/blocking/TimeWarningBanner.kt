package com.example.androidwebsiteblocker.blocking

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.androidwebsiteblocker.TAG
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.ui.theme.Typography
import kotlin.math.abs

/**
 * Samsung-style time warning: a small banner at the top of the screen over the app in use
 * (the app's icon, "Instagram · 10 minutes left", block set name underneath). Swipe it sideways or up, or tap
 * it, to dismiss; otherwise it goes away after a few seconds. Its window is only as big as the
 * banner, so the rest of the screen stays usable. Uses an accessibility overlay: no extra
 * permission, and not affected by notification settings.
 *
 * The system animation frameworks don't run in overlay windows on some devices (that left
 * earlier versions invisible), so fading is done with Handler steps and dragging moves the
 * banner directly with the finger.
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

    /** [target] supplies the icon: the app's own icon, or a letter / "18+" badge for websites. */
    fun show(target: Target, title: String, subtitle: String, colorArgb: Long) {
        remove()
        val o = OverlayOwner().also { it.start() }
        val accent = Color(colorArgb.toInt())
        // Loaded up front (not asynchronously inside Compose) so the first frame is complete.
        val appIcon = (target as? Target.App)?.let { loadAppIcon(it.packageName) }
        val badge = when (target) {
            is Target.App -> target.packageName.substringAfterLast('.').take(1).uppercase()
            is Target.Website -> target.domain.take(1).uppercase()
            Target.AdultWebsites -> "18+"
            Target.WholeSet -> "•" // callers pass the concrete app/site instead
        }
        val content = ComposeView(context).apply {
            setContent {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(Color(0xF2151929), RoundedCornerShape(24.dp))
                        .padding(start = 16.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.width(5.dp).height(48.dp).background(accent, RoundedCornerShape(3.dp)))
                    if (appIcon != null) {
                        Image(appIcon, contentDescription = null, modifier = Modifier.size(44.dp))
                    } else {
                        Box(
                            Modifier.size(44.dp).background(accent.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(badge, color = Color.White, style = if (badge.length > 1) Typography.labelLarge else Typography.titleLarge)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, color = Color.White, style = Typography.titleMedium.copy(fontSize = 18.sp))
                        Text(subtitle, color = Color(0xFFB4B9D2), style = Typography.bodyMedium)
                    }
                }
            }
        }
        val frame = FrameLayout(context).apply {
            alpha = 0f
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
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
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
        fade(frame, to = 1f, durationMs = FADE_IN_MS)
        handler.postDelayed({ if (dragging) dismissOnRelease = true else fadeOutAndRemove() }, SHOW_MS)
    }

    private fun fadeOutAndRemove() {
        val frame = container ?: return
        fade(frame, to = 0f, durationMs = FADE_OUT_MS) { remove() }
    }

    /**
     * Fades with plain Handler steps (~60 fps). The system animation frameworks don't run in
     * overlay windows on some devices, but Handler callbacks and redraws do.
     */
    private fun fade(view: android.view.View, to: Float, durationMs: Long, then: (() -> Unit)? = null) {
        handler.removeCallbacksAndMessages(FADE_TOKEN)
        val from = view.alpha
        val start = SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                if (container !== view) return
                val t = ((SystemClock.uptimeMillis() - start).toFloat() / durationMs).coerceIn(0f, 1f)
                view.alpha = from + (to - from) * t
                if (t < 1f) handler.postAtTime(this, FADE_TOKEN, SystemClock.uptimeMillis() + FRAME_MS) else then?.invoke()
            }
        }
        handler.postAtTime(step, FADE_TOKEN, SystemClock.uptimeMillis())
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
                    if (swipedAway) {
                        remove()
                    } else if (tapped || dismissOnRelease) {
                        fadeOutAndRemove()
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

    private fun loadAppIcon(packageName: String): ImageBitmap? = try {
        context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
    } catch (_: PackageManager.NameNotFoundException) {
        null
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
        const val ICON_PX = 144
        const val FADE_IN_MS = 200L
        const val FADE_OUT_MS = 300L
        const val FRAME_MS = 16L
        val FADE_TOKEN = Any()
        const val TOP_OFFSET_DP = 8f
        const val DISMISS_FRACTION = 0.3f
        const val DISMISS_UP_DP = 24f
    }
}
