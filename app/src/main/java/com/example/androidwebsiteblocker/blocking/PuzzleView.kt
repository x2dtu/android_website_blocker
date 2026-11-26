package com.example.androidwebsiteblocker.blocking

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidwebsiteblocker.core.puzzle.Puzzle
import com.example.androidwebsiteblocker.core.puzzle.Sudokus
import kotlinx.coroutines.delay

private val Muted = Color(0xFFB4B9D2)
private val Ink = Color(0xFF0B0D18)
private val Wrong = Color(0xFFFF8A80)

/** One puzzle. [onAnswer] gets whether the attempt was right; the caller decides what's next. */
@Composable
fun PuzzleView(puzzle: Puzzle, accent: Color, onAnswer: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // key: a new puzzle (even of the same type) starts with empty fields.
        key(puzzle) { PuzzleBody(puzzle, accent, onAnswer) }
    }
}

@Composable
private fun PuzzleBody(puzzle: Puzzle, accent: Color, onAnswer: (Boolean) -> Unit) {
    when (puzzle) {
        is Puzzle.Math -> AnswerPuzzle(
            "Solve to continue", puzzle.question + " = ?", accent, numeric = true,
        ) { onAnswer(puzzle.check(it)) }
        is Puzzle.Sequence -> AnswerPuzzle(
            "What comes next?", puzzle.terms.joinToString(", ") + ", ?", accent, numeric = true,
        ) { onAnswer(puzzle.check(it)) }
        is Puzzle.Sentence -> SentencePuzzle(puzzle, accent) { onAnswer(puzzle.check(it)) }
        is Puzzle.Memory -> MemoryPuzzle(puzzle, accent) { onAnswer(puzzle.check(it)) }
        is Puzzle.Sudoku -> SudokuPuzzle(puzzle, accent) { onAnswer(puzzle.check(it)) }
    }
}

@Composable
private fun Title(text: String) = Text(text, color = Muted, style = MaterialTheme.typography.labelLarge)

@Composable
private fun AnswerPuzzle(title: String, question: String, accent: Color, numeric: Boolean, onSubmit: (String) -> Unit) {
    Title(title)
    Text(
        question, color = Color.White, style = MaterialTheme.typography.headlineSmall,
        fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
    )
    AnswerField(accent, numeric = numeric, singleLine = true, label = "Your answer", onSubmit = onSubmit)
}

@Composable
private fun SentencePuzzle(puzzle: Puzzle.Sentence, accent: Color, onSubmit: (String) -> Unit) {
    Title("Type this to continue")
    Text(puzzle.text, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    AnswerField(accent, numeric = false, singleLine = false, label = "Type it here", onSubmit = onSubmit)
}

@Composable
private fun MemoryPuzzle(puzzle: Puzzle.Memory, accent: Color, onSubmit: (String) -> Unit) {
    val showMs = puzzle.showFor.toMillis()
    val shownAt = remember(puzzle) { SystemClock.elapsedRealtime() }
    var progress by remember(puzzle) { mutableFloatStateOf(0f) }
    LaunchedEffect(puzzle) {
        while (progress < 1f) {
            progress = ((SystemClock.elapsedRealtime() - shownAt).toFloat() / showMs).coerceAtMost(1f)
            delay(50)
        }
    }
    if (progress < 1f) {
        Title("Remember these digits")
        Text(
            puzzle.digits.chunked(3).joinToString(" "), color = Color.White, fontSize = 34.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        )
        LinearProgressIndicator(progress = { 1f - progress }, color = accent, modifier = Modifier.fillMaxWidth())
    } else {
        Title("Type the ${puzzle.digits.length} digits")
        AnswerField(accent, numeric = true, singleLine = true, label = "Digits", onSubmit = onSubmit)
    }
}

@Composable
private fun AnswerField(accent: Color, numeric: Boolean, singleLine: Boolean, label: String, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            capitalization = if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSubmit(text) }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    CheckButton(enabled = text.isNotBlank(), accent = accent) { onSubmit(text) }
}

@Composable
private fun CheckButton(enabled: Boolean, accent: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent, contentColor = Ink,
            disabledContainerColor = Color.White.copy(alpha = 0.08f), disabledContentColor = Muted,
        ),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) { Text("Check and continue", style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun SudokuPuzzle(puzzle: Puzzle.Sudoku, accent: Color, onSubmit: (List<Int>) -> Unit) {
    val n = puzzle.size
    var grid by remember(puzzle) { mutableStateOf(puzzle.givens) }
    var selected by remember(puzzle) { mutableIntStateOf(puzzle.givens.indexOfFirst { it == 0 }) }
    val conflicts = Sudokus.conflicts(n, puzzle.boxRows, puzzle.boxCols, grid)

    Title("Fill the grid: 1–$n once in every row, column and box")
    Column(
        Modifier.widthIn(max = 320.dp).fillMaxWidth().border(2.dp, Color.White.copy(alpha = 0.7f)),
    ) {
        for (r in 0 until n) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until n) {
                    val i = r * n + c
                    val given = puzzle.givens[i] != 0
                    val bg = when {
                        i == selected -> accent.copy(alpha = 0.45f)
                        else -> Color.Transparent
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .border(0.5.dp, Color.White.copy(alpha = 0.25f))
                            .boxEdges(r, c, puzzle.boxRows, puzzle.boxCols, n)
                            .background(bg)
                            .then(if (given) Modifier else Modifier.clickable { selected = i }),
                        contentAlignment = Alignment.Center,
                    ) {
                        val v = grid[i]
                        if (v != 0) Text(
                            "$v",
                            color = when {
                                i in conflicts -> Wrong
                                given -> Color.White
                                else -> accent
                            },
                            fontSize = if (n == 4) 26.sp else 22.sp,
                            fontWeight = if (given) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
    // Number pad: sets the selected cell, then moves to the next blank one.
    Row(Modifier.widthIn(max = 320.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (v in 1..n) {
            OutlinedButton(
                onClick = {
                    if (selected >= 0 && puzzle.givens[selected] == 0) {
                        grid = grid.toMutableList().also { it[selected] = v }
                        val next = (1..grid.size).map { (selected + it) % grid.size }.firstOrNull { grid[it] == 0 }
                        if (next != null) selected = next
                    }
                },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.weight(1f),
            ) { Text("$v", color = Color.White, fontSize = 18.sp) }
        }
    }
    OutlinedButton(
        onClick = { if (selected >= 0 && puzzle.givens[selected] == 0) grid = grid.toMutableList().also { it[selected] = 0 } },
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
    ) { Text("Clear cell", color = Color.White) }
    CheckButton(enabled = grid.none { it == 0 }, accent = accent) { onSubmit(grid) }
}

/** Thicker lines between boxes. */
private fun Modifier.boxEdges(r: Int, c: Int, boxRows: Int, boxCols: Int, n: Int): Modifier {
    val right = c % boxCols == boxCols - 1 && c != n - 1
    val bottom = r % boxRows == boxRows - 1 && r != n - 1
    if (!right && !bottom) return this
    return drawBehind {
        val w = 2.dp.toPx()
        val color = Color.White.copy(alpha = 0.7f)
        if (right) drawRect(color, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
        if (bottom) drawRect(color, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
    }
}
