package com.example.androidwebsiteblocker.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.androidwebsiteblocker.TAG

/**
 * Shows the block screen as [BlockActivity]. If the activity doesn't appear within a moment
 * (e.g. the system refused to start it), falls back to the accessibility overlay so the app is
 * never left unblocked.
 */
class BlockPresenter(
    private val service: AccessibilityService,
    onLeave: () -> Unit,
    onContinue: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val fallback = BlockOverlay(service, onGoHome = onLeave, onContinue = onContinue)

    private val checkShown = Runnable {
        val model = BlockScreenHost.model.value ?: return@Runnable
        if (!BlockScreenHost.visible) {
            Log.w(TAG, "Block screen activity didn't appear; using overlay fallback")
            fallback.show(model)
        }
    }

    init {
        BlockScreenHost.onLeave = onLeave
        BlockScreenHost.onContinue = onContinue
    }

    fun show(model: BlockScreenModel) {
        BlockScreenHost.setModel(model)
        if (fallback.isShowing) {
            if (BlockScreenHost.visible) fallback.hide() else fallback.show(model)
        }
        if (BlockScreenHost.visible) return
        BlockScreenHost.markLaunching(LAUNCH_WINDOW_MS)
        service.startActivity(
            Intent(service, BlockActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            ),
        )
        handler.removeCallbacks(checkShown)
        handler.postDelayed(checkShown, FALLBACK_AFTER_MS)
    }

    fun hide() {
        handler.removeCallbacks(checkShown)
        BlockScreenHost.setModel(null) // the activity finishes itself
        fallback.hide()
    }

    fun relayout() = fallback.relayout()

    private companion object {
        const val LAUNCH_WINDOW_MS = 2_000L
        const val FALLBACK_AFTER_MS = 1_500L
    }
}
