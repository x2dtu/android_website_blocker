package com.example.androidwebsiteblocker

import android.util.Log
import com.example.androidwebsiteblocker.core.model.Foreground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One foreground change. [foreground] null = screen off / locked. */
data class ForegroundLogEntry(val atMillis: Long, val foreground: Foreground?)

/**
 * In-process hub between the accessibility service and the UI: the current foreground and a
 * recent history of changes, so tracking can be checked against reality on the debug screen.
 */
object ForegroundMonitor {
    private const val MAX_LOG = 300

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _current = MutableStateFlow<Foreground?>(null)
    val current: StateFlow<Foreground?> = _current.asStateFlow()

    private val _log = MutableStateFlow<List<ForegroundLogEntry>>(emptyList())
    /** Newest first. */
    val log: StateFlow<List<ForegroundLogEntry>> = _log.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
    }

    /** Returns true if this was a change. */
    fun report(foreground: Foreground?, nowMillis: Long): Boolean {
        if (foreground == _current.value && _log.value.isNotEmpty()) return false
        _current.value = foreground
        _log.update { (listOf(ForegroundLogEntry(nowMillis, foreground)) + it).take(MAX_LOG) }
        Log.i(TAG, "Foreground: ${foreground?.packageName ?: "<screen off/locked>"}${foreground?.host?.let { " ($it)" } ?: ""}")
        return true
    }
}
