package com.example.androidwebsiteblocker.core.puzzle

import com.example.androidwebsiteblocker.core.model.PuzzleSettings
import com.example.androidwebsiteblocker.core.model.PuzzleType
import java.time.Duration
import java.time.LocalTime
import kotlin.random.Random

/** A puzzle shown on the soft block screen. Solving it starts the open. */
sealed interface Puzzle {
    val type: PuzzleType

    /** "47 + 38 − 19". */
    data class Math(val question: String, val answer: Long) : Puzzle {
        override val type get() = PuzzleType.MATH
        fun check(input: String): Boolean = input.trim().toLongOrNull() == answer
    }

    /** Shown terms; the user gives the next one. */
    data class Sequence(val terms: List<Long>, val answer: Long) : Puzzle {
        override val type get() = PuzzleType.SEQUENCE
        fun check(input: String): Boolean = input.trim().replace("−", "-").toLongOrNull() == answer
    }

    /**
     * [size]×[size] grid in boxes of [boxRows]×[boxCols], row by row. 0 = blank in [givens].
     * Always has exactly one solution ([solution]).
     */
    data class Sudoku(val size: Int, val boxRows: Int, val boxCols: Int, val givens: List<Int>, val solution: List<Int>) : Puzzle {
        override val type get() = PuzzleType.SUDOKU
        fun check(grid: List<Int>): Boolean = grid == solution
    }

    /** Type this sentence (case, punctuation and spacing don't matter). */
    data class Sentence(val text: String) : Puzzle {
        override val type get() = PuzzleType.SENTENCE
        fun check(input: String): Boolean = normalize(input) == normalize(text)
    }

    /** Digits shown for [showFor], then typed from memory. */
    data class Memory(val digits: String, val showFor: Duration) : Puzzle {
        override val type get() = PuzzleType.MEMORY
        fun check(input: String): Boolean = input.filter(Char::isDigit) == digits
    }
}

/** For the sentence puzzle: what's blocked, and whether it's night. */
data class PuzzleContext(val targetLabel: String, val night: Boolean)

/** 9 PM – 5 AM counts as night for the sentence wording. */
fun isNight(time: LocalTime): Boolean = time.hour >= 21 || time.hour < 5

object Puzzles {
    const val MAX_LEVEL = 4

    /**
     * 0 (easy) … [MAX_LEVEL] for the next open. With [PuzzleSettings.harderEachOpen], the climb from
     * the starting difficulty to [MAX_LEVEL] is spread over the window's [maxOpens] so the last open
     * is always the hardest (8 opens from easy: 0, 0, 1, 1, 2, 2, 3, 4). With unlimited opens, or
     * fewer opens than levels to climb, it's one level per open.
     */
    fun level(settings: PuzzleSettings, opensUsed: Int, maxOpens: Int?): Int {
        val start = settings.difficulty.ordinal
        if (!settings.harderEachOpen) return start
        val steps = MAX_LEVEL - start
        val climb = if (maxOpens == null || maxOpens <= steps + 1) opensUsed else opensUsed * steps / (maxOpens - 1)
        return (start + climb).coerceIn(0, MAX_LEVEL)
    }

    /** A random puzzle of one of the set's types. */
    fun generate(
        settings: PuzzleSettings,
        opensUsed: Int,
        maxOpens: Int?,
        context: PuzzleContext,
        random: Random = Random.Default,
    ): Puzzle = generate(settings.types.random(random), level(settings, opensUsed, maxOpens), context, random)

    fun generate(type: PuzzleType, level: Int, context: PuzzleContext, random: Random = Random.Default): Puzzle {
        val l = level.coerceIn(0, MAX_LEVEL)
        return when (type) {
            PuzzleType.MATH -> math(l, random)
            PuzzleType.SEQUENCE -> sequence(l, random)
            PuzzleType.SUDOKU -> Sudokus.generate(l, random)
            PuzzleType.SENTENCE -> sentence(l, context, random)
            PuzzleType.MEMORY -> Puzzle.Memory(
                digits = (1..5 + l).map { random.nextInt(10) }.joinToString(""),
                showFor = Duration.ofSeconds(if (l >= 3) 3 else 4),
            )
        }
    }

    private fun math(level: Int, r: Random): Puzzle.Math {
        fun n(range: IntRange) = r.nextInt(range.first, range.last + 1).toLong()
        return when (level) {
            0 -> {
                val a = n(20..99); val b = n(20..99); val c = n(10..(a + b - 1).toInt().coerceAtMost(99))
                Puzzle.Math("$a + $b − $c", a + b - c)
            }
            1 -> {
                val a = n(12..49); val b = n(3..9); val c = n(10..99)
                Puzzle.Math("$a × $b + $c", a * b + c)
            }
            2 -> {
                val a = n(12..99); val b = n(3..9); val c = n(10..(a * b - 1).toInt().coerceAtMost(199))
                Puzzle.Math("$a × $b − $c", a * b - c)
            }
            3 -> {
                val a = n(12..49); val b = n(3..9); val c = n(12..49); val d = n(3..9)
                Puzzle.Math("$a × $b + $c × $d", a * b + c * d)
            }
            else -> {
                val a = n(12..39); val b = n(12..39); val c = n(100..(a * b - 1).toInt().coerceAtMost(999))
                Puzzle.Math("$a × $b − $c", a * b - c)
            }
        }
    }

    /** Five terms shown; the sixth is the answer. Patterns get less obvious with level. */
    private fun sequence(level: Int, r: Random): Puzzle.Sequence {
        fun n(range: IntRange) = r.nextInt(range.first, range.last + 1).toLong()
        val all: List<Long> = when (level) {
            0 -> {
                val a = n(1..20); val d = n(2..9)
                (0 until 6).map { a + it * d }
            }
            1 -> when (r.nextInt(3)) {
                0 -> { // growing steps: +d, +(d+k), +(d+2k)…
                    val a = n(1..10); val d = n(1..4); val k = n(1..3)
                    (0 until 6).runningFold(a) { acc, i -> acc + d + i * k }.take(6)
                }
                1 -> { val a = n(1..9); (0 until 6).map { a shl it } } // doubling
                else -> { val a = n(1..4); (0 until 6).runningFold(a) { acc, _ -> acc * 3 }.take(6) }
            }
            2 -> when (r.nextInt(3)) {
                0 -> { val c = n(0..9); (1..6).map { it.toLong() * it + c } } // squares + c
                1 -> { val a = n(1..5); (0 until 6).runningFold(a) { acc, _ -> acc * 2 + 1 }.take(6) }
                else -> { // alternating +x, −y
                    val a = n(5..20); val x = n(4..9); val y = n(1..3)
                    (0 until 6).runningFold(a) { acc, i -> if (i % 2 == 0) acc + x else acc - y }.take(6)
                }
            }
            3 -> when (r.nextInt(3)) {
                0 -> { // Fibonacci-style
                    val s = mutableListOf(n(1..9), n(1..9))
                    while (s.size < 6) s += s[s.size - 1] + s[s.size - 2]
                    s
                }
                1 -> { val c = n(0..5); (1..6).map { it.toLong() * (it + 1) + c } }
                else -> { val a = n(4..9); val c = n(1..3); (0 until 6).runningFold(a) { acc, _ -> acc * 2 - c }.take(6) }
            }
            else -> when (r.nextInt(3)) {
                0 -> { val c = n(0..5); (1..6).map { it.toLong() * it * it + c } } // cubes + c
                1 -> { // steps double: +d, +2d, +4d…
                    val a = n(1..9); val d = n(1..3)
                    (0 until 6).runningFold(a) { acc, i -> acc + (d shl i) }.take(6)
                }
                else -> { val a = n(3..6); val c = n(1..2); (0 until 6).runningFold(a) { acc, _ -> acc * 3 - c }.take(6) }
            }
        }
        return Puzzle.Sequence(all.take(5), all[5])
    }

    private fun sentence(level: Int, ctx: PuzzleContext, r: Random): Puzzle.Sentence {
        val app = ctx.targetLabel
        val first = if (ctx.night) listOf(
            "I'm choosing to spend my sleep time on $app.",
            "I'm opening $app instead of going to sleep.",
            "Tomorrow I'll be tired because I chose $app tonight.",
            "Sleep matters more than $app, and I'm picking $app anyway.",
        ) else listOf(
            "I'm choosing to spend my time on $app right now.",
            "I'm opening $app instead of doing something that matters to me.",
            "I'm giving my attention to $app on purpose.",
            "Right now, $app is more important to me than anything else I could do.",
        )
        val second = if (ctx.night) listOf(
            "I know I'll regret it in the morning.",
            "My future self would rather I went to bed.",
        ) else listOf(
            "I could be doing something better with this time.",
            "I know I'll wish I had this time back later.",
        )
        val text = first.random(r) + if (level >= 2) " " + second.random(r) else ""
        return Puzzle.Sentence(text)
    }
}

/** Lowercase letters/digits/single spaces; curly apostrophes and punctuation ignored. */
internal fun normalize(s: String): String =
    s.lowercase().replace("'", "").replace("\u2019", "")
        .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
        .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
