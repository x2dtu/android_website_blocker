package com.example.androidwebsiteblocker.data

import android.content.Context
import android.util.Log
import com.example.androidwebsiteblocker.TAG
import com.example.androidwebsiteblocker.core.model.AdultSites
import com.example.androidwebsiteblocker.core.model.DomainHashSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Loads the bundled adult-site list (assets/adult_domains.bin, ~7.5 MB) into [AdultSites] once,
 * off the main thread. Source: The Block List Project porn list (public domain / MIT),
 * https://github.com/blocklistproject/Lists — regenerate with tools/build_domain_list.py.
 */
object AdultSiteList {
    /** When the bundled copy of the list was last updated upstream. Update with the asset. */
    const val LIST_DATE = "July 2026"

    private val started = AtomicBoolean(false)

    fun ensureLoaded(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        thread(name = "adult-list-loader", isDaemon = true) {
            try {
                val list = app.assets.open("adult_domains.bin").use(DomainHashSet::read)
                AdultSites.list = list
                Log.i(TAG, "Loaded adult-site list: ${list.size} domains")
            } catch (e: Exception) {
                started.set(false)
                Log.e(TAG, "Couldn't load adult-site list", e)
            }
        }
    }

    /** Approximate size for display, e.g. "940,000". */
    fun approxSizeLabel(): String {
        val n = AdultSites.list?.size ?: return "about 940,000"
        return "%,d".format(n / 1000 * 1000)
    }
}
