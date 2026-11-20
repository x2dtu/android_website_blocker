package com.example.androidwebsiteblocker.blocking

/** Test hooks the UI can call into the running accessibility service (same process). Null when it isn't connected. */
object DebugHooks {
    /** Logs the current app's UI tree (for finding a browser's address bar) and returns a summary. */
    @Volatile var inspectScreen: (() -> String)? = null
}
