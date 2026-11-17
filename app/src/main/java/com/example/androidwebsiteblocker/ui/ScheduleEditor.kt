package com.example.androidwebsiteblocker.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.MINUTES_PER_DAY
import com.example.androidwebsiteblocker.core.schedule.WindowGroup
import com.example.androidwebsiteblocker.core.schedule.clockLabel
import com.example.androidwebsiteblocker.core.schedule.groupWindows
import com.example.androidwebsiteblocker.core.schedule.shortName
import java.time.DayOfWeek

/** Sunday first, matching the week definition. */
val WEEK_DAYS: List<DayOfWeek> = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }

/** Seven stacked bars, one per day; blocked times are drawn in [color]. */
@Composable
fun WeekBars(windows: List<DayWindow>, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.outline
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        WEEK_DAYS.forEach { day ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day.shortName(), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(38.dp),
                )
                Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = r)
                    listOf(6, 12, 18).forEach { h ->
                        val x = size.width * h / 24f
                        drawLine(tick, Offset(x, size.height * 0.25f), Offset(x, size.height * 0.75f), strokeWidth = 1.dp.toPx())
                    }
                    windows.filter { it.day == day }.forEach { w ->
                        val x0 = size.width * w.startMinute / MINUTES_PER_DAY
                        val x1 = size.width * w.endMinute / MINUTES_PER_DAY
                        drawRoundRect(color, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height), cornerRadius = r)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 38.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12a", "6a", "12p", "6p", "12a").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One row per time range ("11:30 PM – 8:00 AM (next day)" / "Sun–Thu"). */
@Composable
fun WindowGroupRow(group: WindowGroup, color: Color, onClick: (() -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color, 10.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(group.describeTimes(), style = MaterialTheme.typography.bodyLarge)
                Hint(group.describeDays())
            }
            if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

/** Bars + grouped time ranges (tap to edit) + "add" dialog. */
@Composable
fun ScheduleEditor(windows: List<DayWindow>, color: Color, onChange: (List<DayWindow>) -> Unit) {
    var editing by remember { mutableStateOf<WindowGroup?>(null) }
    var adding by remember { mutableStateOf(false) }
    val groups = remember(windows) { groupWindows(windows) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WeekBars(windows, color)
        groups.forEach { g ->
            WindowGroupRow(g, color, onClick = { editing = g }, onRemove = { onChange(windows - g.toWindows().toSet()) })
        }
        if (groups.isEmpty()) Hint("No times yet. Add when this block set should be active.")
        FilledTonalButton(onClick = { adding = true }) { Text("+ Add time window") }
    }
    if (adding) {
        WindowDialog(null, onSave = { onChange(windows + it.toWindows()); adding = false }, onDismiss = { adding = false })
    }
    editing?.let { g ->
        WindowDialog(
            g,
            onSave = { onChange(windows - g.toWindows().toSet() + it.toWindows()); editing = null },
            onDismiss = { editing = null },
        )
    }
}

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val SCHOOL_NIGHTS = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)

/**
 * Pick days and start/end times. If the end is at or before the start, the window runs past
 * midnight and ends the next morning.
 */
@Composable
private fun WindowDialog(initial: WindowGroup?, onSave: (WindowGroup) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var days by remember { mutableStateOf(initial?.days ?: emptySet()) }
    var start by remember { mutableIntStateOf(initial?.start ?: (22 * 60)) }
    var end by remember { mutableIntStateOf(initial?.end?.let { if (it == MINUTES_PER_DAY) 0 else it } ?: (7 * 60)) }
    fun pick(initialMinute: Int, set: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, initialMinute / 60, initialMinute % 60, false).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add time window" else "Edit time window") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Days", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEK_DAYS.forEach { d ->
                        FilterChip(selected = d in days, onClick = { days = if (d in days) days - d else days + d }, label = { Text(d.shortName()) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { days = DayOfWeek.entries.toSet() }, label = { Text("Every day") })
                    AssistChip(onClick = { days = WEEKDAYS }, label = { Text("Weekdays") })
                    AssistChip(onClick = { days = SCHOOL_NIGHTS }, label = { Text("Sun–Thu") })
                }
                Text("Time", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick(start) { start = it } }, modifier = Modifier.weight(1f)) { Text("From ${clockLabel(start)}") }
                    OutlinedButton(onClick = { pick(end) { end = it } }, modifier = Modifier.weight(1f)) { Text("To ${clockLabel(end)}") }
                }
                Hint(
                    when {
                        end == 0 -> "Runs until midnight."
                        end <= start -> "Runs overnight — ends at ${clockLabel(end)} the next morning."
                        else -> "Same-day window."
                    },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = days.isNotEmpty(), onClick = { onSave(WindowGroup(days, start, if (end == 0) MINUTES_PER_DAY else end)) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
