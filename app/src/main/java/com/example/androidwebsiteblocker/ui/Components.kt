package com.example.androidwebsiteblocker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.androidwebsiteblocker.isTv
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keyboard behavior for form fields. On TVs, Fire TV's full-screen keyboard sends "Next" even for
 * Done; the default jumped into the next *text box* and kept the keyboard open, and clearing focus
 * sent focus back above the field. So on TVs, Next/Done close the keyboard and move focus down to
 * whatever comes next (a button, card, or field). On phones, Next works as usual.
 */
@Composable
fun formKeyboardActions(): KeyboardActions {
    val context = LocalContext.current
    if (!isTv(context)) return KeyboardActions.Default
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val close: KeyboardActionScope.() -> Unit = {
        keyboard?.hide()
        focusManager.moveFocus(FocusDirection.Down)
    }
    return KeyboardActions(onDone = close, onNext = close, onGo = close, onSearch = close, onSend = close)
}

/** "Done" on TVs (see [formKeyboardActions]), the keyboard's default elsewhere. */
@Composable
fun formImeAction(): ImeAction = if (isTv(LocalContext.current)) ImeAction.Done else ImeAction.Default

/** Rounded card with a title, optional subtitle and ⓘ explanation. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    info: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (info != null) InfoButton(title, info)
            }
            content()
        }
    }
}

/** Small ⓘ that explains a setting in a dialog. */
@Composable
fun InfoButton(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.size(28.dp).clip(CircleShape).clickable { open = true }
            .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("i", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
}

/**
 * Number setting: label, unit suffix, a short hint underneath, and an ⓘ for the full explanation.
 * Digits only; empty is allowed when [blankMeans] is given (e.g. "No limit").
 */
@Composable
fun NumberSetting(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    unit: String,
    hint: String,
    info: String,
    blankMeans: String? = null,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(6)) },
            label = { Text(label) },
            placeholder = blankMeans?.let { { Text(it) } },
            suffix = { Text(unit) },
            supportingText = { Text(if (value.isBlank() && blankMeans != null) blankMeans else hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = formImeAction()),
            keyboardActions = formKeyboardActions(),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.padding(top = 14.dp)) { InfoButton(label, info) }
    }
}

/** Two big tappable options, e.g. Hard vs Soft. */
@Composable
fun ChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/** Small rounded label. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Colored dot used as a set's identity marker. */
@Composable
fun ColorDot(color: Color, size: Dp = 12.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** Muted explanatory text. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
