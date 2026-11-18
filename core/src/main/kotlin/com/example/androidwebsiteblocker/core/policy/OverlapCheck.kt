package com.example.androidwebsiteblocker.core.policy

import com.example.androidwebsiteblocker.core.model.AdultSites
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.schedule.WeekSpan

/** [target] in the set being edited is also covered by [otherSet] during [spans]. */
data class OverlapWarning(val target: Target, val otherTarget: Target, val otherSet: BlockSet, val spans: List<WeekSpan>) {
    fun describe(targetLabel: String): String =
        "$targetLabel is also in ${otherSet.name}: " + spans.joinToString(", ") { it.describe() }
}

/**
 * Warnings for targets of [edited] that another set also blocks at overlapping times.
 * Disabled sets are included since they can be re-enabled. Websites overlap when one domain
 * is the same as or a subdomain of the other.
 */
fun findOverlaps(edited: BlockSet, others: List<BlockSet>): List<OverlapWarning> = buildList {
    for (other in others) {
        if (other.id == edited.id) continue
        val spans = edited.schedule.overlapWith(other.schedule)
        if (spans.isEmpty()) continue
        for (target in edited.targets) {
            for (otherTarget in other.targets) {
                if (targetsOverlap(target, otherTarget)) add(OverlapWarning(target, otherTarget, other, spans))
            }
        }
    }
}

internal fun targetsOverlap(a: Target, b: Target): Boolean = when {
    a is Target.App && b is Target.App -> a.packageName == b.packageName
    a is Target.Website && b is Target.Website ->
        a.domain == b.domain || a.domain.endsWith(".${b.domain}") || b.domain.endsWith(".${a.domain}")
    a == Target.AdultWebsites && b == Target.AdultWebsites -> true
    a == Target.AdultWebsites && b is Target.Website -> AdultSites.contains(b.domain)
    a is Target.Website && b == Target.AdultWebsites -> AdultSites.contains(a.domain)
    else -> false
}
