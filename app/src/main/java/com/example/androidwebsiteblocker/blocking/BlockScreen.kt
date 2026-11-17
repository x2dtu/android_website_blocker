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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Duration
import kotlinx.coroutines.delay

/** Everything the block screen shows. A new [resetKey] restarts the unlock countdown. */
data class BlockScreenModel(
    val resetKey: String,
    val setName: String,
    val colorArgb: Long,
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

@Composable
fun BlockScreen(model: BlockScreenModel, onGoHome: () -> Unit, onContinue: () -> Unit) {
    val accent = Color(model.colorArgb.toInt())
    MaterialTheme(colorScheme = darkColorScheme(primary = accent)) {
        Box(
            Modifier.fillMaxSize().background(lerp(Color(0xFF101014), accent, 0.15f)).padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(model.setName.uppercase(), color = accent, style = MaterialTheme.typography.labelLarge)
                Text(model.targetLabel, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                if (model.message.isNotBlank()) {
                    Text(
                        "“${model.message}”", color = Color.White, fontStyle = FontStyle.Italic,
                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                if (model.streakDays > 0) {
                    Text("${model.streakDays}-day streak — keep it going", color = accent, style = MaterialTheme.typography.titleSmall)
                }
                model.reasons.forEach {
                    Text(it, color = Color(0xFFBBBBC4), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                val soft = model.soft
                if (soft == null) {
                    Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) { Text(model.leaveLabel) }
                } else {
                    SoftControls(model.resetKey, soft, accent, model.leaveLabel, onGoHome, onContinue)
                }
            }
        }
    }
}

@Composable
private fun SoftControls(resetKey: String, soft: SoftGateInfo, accent: Color, leaveLabel: String, onGoHome: () -> Unit, onContinue: () -> Unit) {
    if (soft.maxOpens != null) {
        OpenDots(used = soft.opensUsed, max = soft.maxOpens, accent = accent)
        Text("${soft.maxOpens - soft.opensUsed}/${soft.maxOpens} left", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
    if (soft.limitsSummary.isNotBlank()) {
        Text(soft.limitsSummary, color = Color(0xFFBBBBC4), style = MaterialTheme.typography.bodySmall)
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
        colors = ButtonDefaults.buttonColors(containerColor = accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (secondsLeft > 0) "Continue in ${secondsLeft}s" else "Continue to app")
    }
    TextButton(onClick = onGoHome) { Text(leaveLabel, color = Color.White) }
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(max) { i ->
            Box(Modifier.size(20.dp)) {
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
