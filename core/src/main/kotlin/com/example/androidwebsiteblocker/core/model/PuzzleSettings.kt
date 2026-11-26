package com.example.androidwebsiteblocker.core.model

import java.time.Duration

/** Kinds of puzzle the soft block screen can ask for. One is picked at random each time. */
enum class PuzzleType { MATH, SEQUENCE, SUDOKU, SENTENCE, MEMORY }

/** Starting difficulty. Higher = stricter (lowering it is a loosening change). */
enum class PuzzleDifficulty { EASY, MEDIUM, HARD }

/**
 * Optional puzzle on the soft block screen. When on, the unlock wait leads to a puzzle instead of
 * the Continue button, and solving it starts the open.
 */
data class PuzzleSettings(
    val enabled: Boolean = false,
    val types: Set<PuzzleType> = PuzzleType.entries.toSet(),
    val difficulty: PuzzleDifficulty = PuzzleDifficulty.MEDIUM,
    /** Each open already used in the window makes the next puzzle one level harder. */
    val harderEachOpen: Boolean = false,
    /** After a wrong answer, how long until the next (new) puzzle appears. */
    val wrongAnswerLockout: Duration = Duration.ofSeconds(10),
) {
    init {
        require(types.isNotEmpty()) { "pick at least one puzzle type" }
        require(!wrongAnswerLockout.isNegative)
    }
}
