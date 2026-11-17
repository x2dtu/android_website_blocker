package com.example.androidwebsiteblocker.data

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.util.AtomicFile
import com.example.androidwebsiteblocker.TAG
import com.example.androidwebsiteblocker.core.storage.AppState
import com.example.androidwebsiteblocker.core.storage.AppStateCodec
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for persisted state, shared by the UI and the accessibility service
 * (same process). Writes are atomic so a crash mid-write can't corrupt the file.
 *
 * Saving happens on a background thread: on slower phones a write (which waits for the disk)
 * can take long enough to freeze the block screen if done on the main thread. The in-memory
 * state updates immediately; the newest state is always the one written last.
 */
class StateStore(private val file: File) {
    private val atomic = AtomicFile(file)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Applies [transform] and persists if anything changed. Returns the new state. */
    @Synchronized
    fun update(transform: (AppState) -> AppState): AppState {
        val old = _state.value
        val new = transform(old)
        if (new != old) {
            _state.value = new
            pendingWrite.set(new)
            writer.execute(::writeLatest)
        }
        return new
    }

    /** The state waiting to be written; null once a writer has picked it up. */
    private val pendingWrite = AtomicReference<AppState?>(null)

    /** One thread, so writes never overlap or land out of order. */
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "state-writer") }

    /** Writes whatever is newest. Several queued updates collapse into one write. */
    private fun writeLatest() {
        val state = pendingWrite.getAndSet(null) ?: return
        try {
            save(state)
        } catch (e: Exception) {
            Log.e(TAG, "Could not save state", e)
        }
    }

    /** Blocks until all queued writes are on disk. For tests. */
    @VisibleForTesting
    fun awaitWrites() {
        writer.submit {}.get()
    }

    private fun load(): AppState {
        if (!file.exists()) return AppState()
        return try {
            AppStateCodec.decode(atomic.readFully().decodeToString())
        } catch (e: Exception) {
            // Keep the unreadable file for debugging rather than silently overwriting it.
            val backup = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            file.copyTo(backup, overwrite = true)
            Log.e(TAG, "Could not read state; backed up to ${backup.name} and starting fresh", e)
            AppState()
        }
    }

    private fun save(state: AppState) {
        val out = atomic.startWrite()
        try {
            out.write(AppStateCodec.encode(state).encodeToByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    companion object {
        @Volatile private var instance: StateStore? = null

        fun get(context: Context): StateStore = instance ?: synchronized(this) {
            instance ?: StateStore(File(context.applicationContext.filesDir, "state.json")).also { instance = it }
        }
    }
}
