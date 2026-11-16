package com.example.androidwebsiteblocker.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Full-screen block screen drawn over the blocked app using an accessibility overlay window
 * (no "draw over other apps" permission needed). Back always goes home.
 */
class BlockOverlay(
    private val service: AccessibilityService,
    private val onGoHome: () -> Unit,
    private val onContinue: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val model = mutableStateOf<BlockScreenModel?>(null)
    private var view: OverlayView? = null
    private var owner: OverlayOwner? = null

    val isShowing: Boolean get() = view != null

    fun show(newModel: BlockScreenModel) {
        model.value = newModel
        if (view != null) return
        val o = OverlayOwner().also { it.start() }
        val v = OverlayView(service, onGoHome) {
            model.value?.let { BlockScreen(it, onGoHome = onGoHome, onContinue = onContinue) }
        }
        v.setViewTreeLifecycleOwner(o)
        v.setViewTreeSavedStateRegistryOwner(o)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        windowManager.addView(v, params)
        view = v
        owner = o
    }

    fun hide() {
        view?.let { windowManager.removeView(it) }
        owner?.destroy()
        view = null
        owner = null
        model.value = null
    }
}

/** Compose host that turns Back (key or gesture) into "go home". */
private class OverlayView(
    context: Context,
    private val onBack: () -> Unit,
    private val body: @Composable () -> Unit,
) : AbstractComposeView(context) {

    private val backCallback: Any? =
        if (Build.VERSION.SDK_INT >= 33) OnBackInvokedCallback { onBack() } else null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    @Composable
    override fun Content() = body()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
        if (Build.VERSION.SDK_INT >= 33) {
            findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY, backCallback as OnBackInvokedCallback,
            )
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 33) {
            findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(backCallback as OnBackInvokedCallback)
        }
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** Minimal lifecycle + saved state so Compose can run outside an Activity. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
