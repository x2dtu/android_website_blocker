package com.example.androidwebsiteblocker.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.protection.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val SET_COLORS: List<Long> = listOf(
    0xFF5C6BC0, 0xFF26A69A, 0xFFEF5350, 0xFFFFA726, 0xFFAB47BC, 0xFF42A5F5, 0xFF66BB6A, 0xFF8D6E63,
)

data class InstalledApp(val packageName: String, val label: String)

/** Launchable apps, excluding this one, sorted by name. */
suspend fun loadInstalledApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }
        .map { InstalledApp(it, appLabel(context, it)) }
        .sortedBy { it.label.lowercase() }
}

fun appLabel(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) {
    packageName
}

fun targetLabel(context: Context, target: Target): String = when (target) {
    is Target.App -> appLabel(context, target.packageName)
    is Target.Website -> target.domain
    Target.AdultWebsites -> "Adult websites"
    Target.WholeSet -> "All apps & sites together"
}

private val iconCache = LruCache<String, ImageBitmap>(200)

@Composable
fun TargetIcon(target: Target, size: Dp = 32.dp) {
    when (target) {
        is Target.App -> AppIcon(target.packageName, size)
        is Target.Website -> LetterIcon(target.domain, size)
        Target.AdultWebsites -> BadgeIcon("18+", size)
        Target.WholeSet -> BadgeIcon("All", size)
    }
}

@Composable
fun AppIcon(packageName: String, size: Dp = 32.dp) {
    val context = LocalContext.current
    val icon by produceState(iconCache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
                        .also { iconCache.put(packageName, it) }
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
        }
    }
    val bitmap = icon
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
    else LetterIcon(packageName.substringAfterLast('.'), size)
}

/** Text badge in a circle, e.g. "18+". */
@Composable
fun BadgeIcon(text: String, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.errorContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = (size.value * 0.32f).sp)
    }
}

/** Placeholder for websites (no favicons yet — that would need network access). */
@Composable
fun LetterIcon(text: String, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = (size.value * 0.45f).sp,
        )
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) { Text("← Back") }
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Asks for the set's PIN; calls [onVerified] only when it matches. */
@Composable
fun PinDialog(title: String, pinHash: String, onVerified: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(pin, { pin = it; error = false }, "PIN")
                if (error) Text("Wrong PIN", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (PinHasher.verify(pin, pinHash)) onVerified() else error = true }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PinField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(PinHasher.MAX_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Simple confirm dialog. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun Long.asColor(): Color = Color(this.toInt())
