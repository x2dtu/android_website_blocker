package com.example.androidwebsiteblocker.core.puzzle

import com.example.androidwebsiteblocker.core.model.PuzzleDifficulty
import com.example.androidwebsiteblocker.core.model.PuzzleSettings
import com.example.androidwebsiteblocker.core.model.PuzzleType
import java.time.LocalTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzlesTest {
    private val ctx = PuzzleContext("Reddit", night = true)

    @Test
    fun `level starts at difficulty and climbs with opens when enabled`() {
        val s = PuzzleSettings(difficulty = PuzzleDifficulty.EASY)
        assertEquals(0, Puzzles.level(s, opensUsed = 3))
        assertEquals(3, Puzzles.level(s.copy(harderEachOpen = true), opensUsed = 3))
        assertEquals(Puzzles.MAX_LEVEL, Puzzles.level(s.copy(difficulty = PuzzleDifficulty.HARD, harderEachOpen = true), opensUsed = 9))
        assertEquals(1, Puzzles.level(PuzzleSettings(), opensUsed = 0)) // medium
    }

    @Test
    fun `only picks enabled types`() {
        val s = PuzzleSettings(types = setOf(PuzzleType.MATH, PuzzleType.MEMORY))
        val r = Random(1)
        repeat(50) { assertTrue(Puzzles.generate(s, 0, ctx, r).type in s.types) }
    }

    @Test
    fun `math answers are correct and positive at every level`() {
        val r = Random(2)
        for (level in 0..Puzzles.MAX_LEVEL) repeat(200) {
            val p = Puzzles.generate(PuzzleType.MATH, level, ctx, r) as Puzzle.Math
            assertEquals(p.question, evaluate(p.question), p.answer)
            assertTrue(p.answer > 0)
            assertTrue(p.check(" ${p.answer} "))
            assertFalse(p.check("${p.answer + 1}"))
        }
    }

    @Test
    fun `sequences show five terms and are non-negative`() {
        val r = Random(3)
        for (level in 0..Puzzles.MAX_LEVEL) repeat(200) {
            val p = Puzzles.generate(PuzzleType.SEQUENCE, level, ctx, r) as Puzzle.Sequence
            assertEquals(5, p.terms.size)
            assertTrue((p.terms + p.answer).all { it >= 0 })
            assertTrue(p.check("${p.answer}"))
        }
    }

    @Test
    fun `sudoku has givens consistent with a valid unique-solution grid`() {
        val r = Random(4)
        for (level in 0..Puzzles.MAX_LEVEL) repeat(20) {
            val p = Sudokus.generate(level, r)
            val n = p.size
            assertEquals(n * n, p.solution.size)
            assertTrue(Sudokus.conflicts(n, p.boxRows, p.boxCols, p.solution).isEmpty())
            for (row in 0 until n) assertEquals((1..n).toSet(), (0 until n).map { p.solution[row * n + it] }.toSet())
            p.givens.forEachIndexed { i, v -> if (v != 0) assertEquals(p.solution[i], v) }
            assertTrue(p.check(p.solution))
            assertFalse(p.check(p.givens))
        }
    }

    @Test
    fun `sudoku gets bigger and sparser with level`() {
        val r = Random(5)
        assertEquals(4, Sudokus.generate(0, r).size)
        assertEquals(6, Sudokus.generate(2, r).size)
        val easy = Sudokus.generate(2, r).givens.count { it != 0 }
        val hard = Sudokus.generate(4, r).givens.count { it != 0 }
        assertTrue("$easy vs $hard", hard < easy)
    }

    @Test
    fun `conflicts flags duplicates in a row`() {
        val grid = listOf(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(setOf(0, 1), Sudokus.conflicts(4, 2, 2, grid))
    }

    @Test
    fun `sentence names the app and matches night or day`() {
        val r = Random(6)
        val night = Puzzles.generate(PuzzleType.SENTENCE, 0, ctx, r) as Puzzle.Sentence
        assertTrue(night.text.contains("Reddit"))
        val days = (1..30).map { (Puzzles.generate(PuzzleType.SENTENCE, 0, PuzzleContext("Reddit", false), r) as Puzzle.Sentence).text }
        assertTrue(days.none { it.contains("sleep", ignoreCase = true) || it.contains("tonight") })
        val long = Puzzles.generate(PuzzleType.SENTENCE, 2, ctx, r) as Puzzle.Sentence
        assertTrue(long.text.count { it == '.' } >= 2)
    }

    @Test
    fun `sentence check ignores case, punctuation, spacing and apostrophe style`() {
        val p = Puzzle.Sentence("I'm choosing to spend my sleep time on Reddit.")
        assertTrue(p.check("im choosing to  spend my sleep time on reddit"))
        assertTrue(p.check("I’m choosing to spend my sleep time on Reddit!"))
        assertFalse(p.check("I'm choosing to spend my time on Reddit."))
    }

    @Test
    fun `memory length grows with level`() {
        val r = Random(7)
        assertEquals(5, (Puzzles.generate(PuzzleType.MEMORY, 0, ctx, r) as Puzzle.Memory).digits.length)
        val p = Puzzles.generate(PuzzleType.MEMORY, 4, ctx, r) as Puzzle.Memory
        assertEquals(9, p.digits.length)
        assertTrue(p.check(p.digits.chunked(3).joinToString(" ")))
    }

    @Test
    fun `night is 9 PM to 5 AM`() {
        assertTrue(isNight(LocalTime.of(21, 0)))
        assertTrue(isNight(LocalTime.of(4, 59)))
        assertFalse(isNight(LocalTime.of(5, 0)))
        assertFalse(isNight(LocalTime.of(20, 59)))
    }

    /** Evaluates "a op b op c [op d]" with × before + and −. */
    private fun evaluate(q: String): Long {
        val tokens = q.split(" ")
        val terms = mutableListOf<Long>()
        val signs = mutableListOf(1L)
        var i = 0
        var current = tokens[0].toLong()
        while (i + 2 <= tokens.lastIndex) {
            val op = tokens[i + 1]
            val v = tokens[i + 2].toLong()
            when (op) {
                "×" -> current *= v
                else -> { terms += current; signs += if (op == "+") 1 else -1; current = v }
            }
            i += 2
        }
        terms += current
        return terms.indices.sumOf { signs[it] * terms[it] }
    }
}
