package com.example.androidwebsiteblocker.blocking

/**
 * Test hooks the UI can call into the running accessibility service (same process). Null when
 * the service isn't connected.
 */
object DebugHooks {
    /** Shows a sample time warning banner using the real banner code. */
    @Volatile var previewTimeWarning: (() -> Unit)? = null
}
