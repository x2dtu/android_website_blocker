package com.example.androidwebsiteblocker.blocking

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared, in-process link between the accessibility service (which decides what to block) and
 * [BlockActivity] (which shows it). The activity's lifecycle is the reliable signal for whether
 * the block screen is actually on screen.
 */
object BlockScreenHost {
    private val _model = MutableStateFlow<BlockScreenModel?>(null)
    /** What to show; null = the block screen should close. */
    val model: StateFlow<BlockScreenModel?> = _model.asStateFlow()

    @Volatile var onLeave: () -> Unit = {}
    @Volatile var onContinue: () -> Unit = {}
    /** The activity stopped while still supposed to be blocking (e.g. user switched away). */
    @Volatile var onHiddenWhileBlocking: () -> Unit = {}

    /** True between the activity's onStart and onStop. */
    @Volatile var visible = false
        internal set

    @Volatile private var launchPendingUntil = 0L

    internal fun setModel(m: BlockScreenModel?) {
        _model.value = m
        if (m == null) launchPendingUntil = 0
    }

    internal fun markLaunching(windowMs: Long) {
        launchPendingUntil = SystemClock.uptimeMillis() + windowMs
    }

    /**
     * While the block screen is up (or about to be), our own window is on top of the blocked app;
     * the service must not mistake that for the user switching to Night Blocker.
     */
    fun coversForeground(): Boolean = visible || SystemClock.uptimeMillis() < launchPendingUntil
}
