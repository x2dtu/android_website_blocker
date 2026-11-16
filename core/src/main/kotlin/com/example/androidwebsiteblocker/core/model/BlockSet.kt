package com.example.androidwebsiteblocker.core.model

import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.time.Duration
import java.time.Instant

/** Something a block set can block. */
sealed interface Target {
    data class App(val packageName: String) : Target

    /** Matches [domain] and all of its subdomains (`reddit.com` covers `old.reddit.com`). */
    data class Website(val domain: String) : Target {
        init {
            require(domain.isNotBlank() && domain == domain.lowercase()) { "domain must be lowercase and non-blank: $domain" }
        }
    }

    fun matches(foreground: Foreground): Boolean = when (this) {
        is App -> foreground.packageName == packageName
        is Website -> foreground.host?.let { it == domain || it.endsWith(".$domain") } == true
    }
}

/** What the user is looking at right now: the foreground app and, if it's the browser, the site's host. */
data class Foreground(val packageName: String, val host: String? = null)

enum class BlockMode { HARD, SOFT }

/**
 * Soft block restrictions. Every limit is optional; any combination is allowed.
 * They apply to each target in the set separately, and reset each window occurrence.
 */
data class SoftLimits(
    /** How long the "continue" button stays disabled on the block screen. Restarts if you leave the screen. */
    val unlockDelay: Duration = Duration.ZERO,
    /** Max opens per window occurrence. */
    val maxOpens: Int? = null,
    /** Max length of one open. Each open starts fresh at this value. */
    val perOpenLimit: Duration? = null,
    /** Max total time per window occurrence, across all opens. */
    val totalLimit: Duration? = null,
    /** Returning to the target within this time continues the same open instead of starting a new one. */
    val graceTime: Duration = Duration.ofSeconds(15),
) {
    init {
        require(!unlockDelay.isNegative && !graceTime.isNegative)
        require(maxOpens == null || maxOpens > 0) { "maxOpens must be positive" }
        require(perOpenLimit == null || perOpenLimit > Duration.ZERO) { "perOpenLimit must be positive" }
        require(totalLimit == null || totalLimit > Duration.ZERO) { "totalLimit must be positive" }
    }
}

/** Barriers against turning a block set off on impulse. All configurable. */
data class ProtectionSettings(
    /** How long an emergency suspension lasts. */
    val suspensionDuration: Duration = Duration.ofMinutes(1),
    /** Max suspensions per window occurrence; null = unlimited. */
    val maxSuspensions: Int? = 3,
    /** Wait after a suspension ends before another is allowed. */
    val suspensionCooldown: Duration = Duration.ofSeconds(30),
    /** Wait before a disable or loosening edit takes effect. */
    val changeWait: Duration = Duration.ofMinutes(5),
) {
    init {
        require(suspensionDuration > Duration.ZERO)
        require(maxSuspensions == null || maxSuspensions >= 0)
        require(!suspensionCooldown.isNegative && !changeWait.isNegative)
    }
}

data class BlockSet(
    val id: String,
    val name: String,
    val mode: BlockMode,
    val targets: Set<Target>,
    val schedule: WeeklySchedule,
    val softLimits: SoftLimits = SoftLimits(),
    val protection: ProtectionSettings = ProtectionSettings(),
    /** Motivational message shown on the block screen. */
    val message: String = "",
    val colorArgb: Long = 0xFF5C6BC0,
    /** See [com.example.androidwebsiteblocker.core.protection.PinHasher]. Null only before setup completes. */
    val pinHash: String? = null,
    val enabled: Boolean = true,
    /** While now is before this, the whole set is ignored (temporary emergency suspension). */
    val suspendedUntil: Instant? = null,
) {
    fun isSuspended(now: Instant): Boolean = suspendedUntil?.isAfter(now) == true
}
