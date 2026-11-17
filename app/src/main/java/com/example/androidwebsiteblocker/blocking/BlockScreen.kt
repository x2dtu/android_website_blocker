package com.example.androidwebsiteblocker.blocking

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.ui.TargetIcon
import com.example.androidwebsiteblocker.ui.theme.Typography
import java.time.Duration
import kotlinx.coroutines.delay

/** Everything the block screen shows. A new [resetKey] restarts the unlock countdown. */
data class BlockScreenModel(
    val resetKey: String,
    val setName: String,
    val colorArgb: Long,
    val target: Target,
    val targetLabel: String,
    val message: String,
    val streakDays: Int,
    /** "Why" lines, primary first. */
    val reasons: List<String>,
    /** Null = hard block (only the exit button). */
    val soft: SoftGateInfo?,
    /** Exit button text: "Go to home screen", or "Open a new tab" for a blocked website. */
    val leaveLabel: String = "Go to home screen",
)

data class SoftGateInfo(
    val unlockDelay: Duration,
    val opensUsed: Int,
    val maxOpens: Int?,
    /** e.g. "3 min per open · 12 min total". */
    val limitsSummary: String,
)

private val Muted = Color(0xFFB4B9D2)
private val Ink = Color(0xFF0B0D18)

@Composable
fun BlockScreen(model: BlockScreenModel, onGoHome: () -> Unit, onContinue: () -> Unit) {
    val accent = Color(model.colorArgb.toInt())
    MaterialTheme(colorScheme = darkColorScheme(primary = accent), typography = Typography) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(lerp(Ink, accent, 0.35f), Ink, Ink)))
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(96.dp).background(Color.White.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { TargetIcon(model.target, 64.dp) }

                Text(model.targetLabel, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)

                Row(
                    Modifier.background(accent.copy(alpha = 0.2f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).background(accent, CircleShape))
                    Text(
                        "${model.setName} · ${if (model.soft == null) "blocked" else "limited"}",
                        color = Color.White, style = MaterialTheme.typography.labelLarge,
                    )
                }

                if (model.message.isNotBlank()) {
                    Text(
                        "“${model.message}”", color = Color.White, fontStyle = FontStyle.Italic,
                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }

                if (model.streakDays > 0) {
                    Text("🔥 ${model.streakDays}-day streak — don't break it", color = accent, style = MaterialTheme.typography.titleSmall)
                }

                if (model.reasons.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Why you're seeing this", color = Muted, style = MaterialTheme.typography.labelMedium)
                        model.reasons.forEach { Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                val soft = model.soft
                if (soft == null) {
                    Button(
                        onClick = onGoHome,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Ink),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) { Text(model.leaveLabel, style = MaterialTheme.typography.titleMedium) }
                } else {
                    SoftControls(model.resetKey, soft, accent, model.leaveLabel, onGoHome, onContinue)
                }
            }
        }
    }
}

@Composable
private fun SoftControls(
    resetKey: String,
    soft: SoftGateInfo,
    accent: Color,
    leaveLabel: String,
    onGoHome: () -> Unit,
    onContinue: () -> Unit,
) {
    if (soft.maxOpens != null) {
        OpenDots(used = soft.opensUsed, max = soft.maxOpens, accent = accent)
        Text("${soft.maxOpens - soft.opensUsed}/${soft.maxOpens} opens left", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
    if (soft.limitsSummary.isNotBlank()) {
        Text(soft.limitsSummary, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    var secondsLeft by remember(resetKey) { mutableLongStateOf(soft.unlockDelay.seconds) }
    LaunchedEffect(resetKey) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }
    Button(
        onClick = onContinue,
        enabled = secondsLeft <= 0,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent, contentColor = Ink,
            disabledContainerColor = Color.White.copy(alpha = 0.08f), disabledContentColor = Muted,
        ),
        modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
        Text(if (secondsLeft > 0) "Continue in ${secondsLeft}s" else "Continue to app", style = MaterialTheme.typography.titleMedium)
    }
    TextButton(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) { Text(leaveLabel, color = Color.White) }
}

/**
 * One circle per open, like lives: used opens are hollow, remaining are filled, and the one this
 * attempt would use blinks between filled and hollow.
 */
@Composable
private fun OpenDots(used: Int, max: Int, accent: Color) {
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "blinkAlpha",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(max) { i ->
            Box(Modifier.size(22.dp)) {
                Box(Modifier.fillMaxSize().border(2.dp, accent, CircleShape))
                when {
                    i < used -> Unit
                    i == used -> Box(Modifier.fillMaxSize().alpha(blink).background(accent, CircleShape))
                    else -> Box(Modifier.fillMaxSize().background(accent, CircleShape))
                }
            }
        }
    }
}
