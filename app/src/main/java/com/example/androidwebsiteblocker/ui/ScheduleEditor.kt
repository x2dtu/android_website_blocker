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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Sunday first, matching the week definition. */
val WEEK_DAYS: List<DayOfWeek> = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }

fun DayOfWeek.short(): String = getDisplayName(TextStyle.SHORT, Locale.US)

fun clockLabel(minuteOfDay: Int): String {
    val h24 = (minuteOfDay / 60) % 24
    val m = minuteOfDay % 60
    val h12 = if (h24 % 12 == 0) 12 else h24 % 12
    return "%d:%02d %s".format(h12, m, if (h24 < 12) "AM" else "PM")
}

/** Seven stacked bars, one per day; blocked times are drawn in [color]. */
@Composable
fun WeekBars(windows: List<DayWindow>, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.outline
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEK_DAYS.forEach { day ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.short(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
                Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                    val r = CornerRadius(4.dp.toPx())
                    drawRoundRect(track, cornerRadius = r)
                    windows.filter { it.day == day }.forEach { w ->
                        val x0 = size.width * w.startMinute / MINUTES_PER_DAY
                        val x1 = size.width * w.endMinute / MINUTES_PER_DAY
                        drawRoundRect(color, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height), cornerRadius = r)
                    }
                    listOf(6, 12, 18).forEach { h ->
                        val x = size.width * h / 24f
                        drawLine(tick, Offset(x, 0f), Offset(x, size.height * 0.3f), strokeWidth = 1.dp.toPx())
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12a", "6a", "12p", "6p", "12a").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** Bars + list of windows with remove buttons + "add" dialog. */
@Composable
fun ScheduleEditor(windows: List<DayWindow>, color: Color, onChange: (List<DayWindow>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WeekBars(windows, color)
        WEEK_DAYS.forEach { day ->
            windows.filter { it.day == day }.sortedBy { it.startMinute }.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${day.short()}  ${clockLabel(w.startMinute)} – ${clockLabel(w.endMinute)}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange(windows - w) }) { Text("Remove") }
                }
            }
        }
        OutlinedButton(onClick = { adding = true }) { Text("Add time window") }
    }
    if (adding) {
        AddWindowDialog(onAdd = { onChange(windows + it); adding = false }, onDismiss = { adding = false })
    }
}

/**
 * Pick days and a start/end time. If the end is at or before the start, the window runs past
 * midnight into the next day (stored as two per-day pieces).
 */
@Composable
private fun AddWindowDialog(onAdd: (List<DayWindow>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var days by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var start by remember { mutableIntStateOf(22 * 60) }
    var end by remember { mutableIntStateOf(7 * 60) }
    fun pick(initial: Int, set: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, initial / 60, initial % 60, false).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add time window") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Days")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WEEK_DAYS.forEach { d ->
                        FilterChip(selected = d in days, onClick = { days = if (d in days) days - d else days + d }, label = { Text(d.short()) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick(start) { start = it } }) { Text("From ${clockLabel(start)}") }
                    OutlinedButton(onClick = { pick(end) { end = it } }) { Text("To ${clockLabel(end)}") }
                }
                if (end <= start) {
                    Text("Ends the next day.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = days.isNotEmpty(), onClick = { onAdd(days.flatMap { windowsFor(it, start, end) }) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun windowsFor(day: DayOfWeek, start: Int, end: Int): List<DayWindow> =
    if (end > start) listOf(DayWindow(day, start, end))
    else listOfNotNull(
        DayWindow(day, start, MINUTES_PER_DAY),
        if (end > 0) DayWindow(day.plus(1), 0, end) else null,
    )
