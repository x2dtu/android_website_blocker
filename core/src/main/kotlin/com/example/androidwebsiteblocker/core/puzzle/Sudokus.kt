package com.example.androidwebsiteblocker.core.puzzle

import kotlin.random.Random

/** Mini sudoku: 4×4 (2×2 boxes) at levels 0–1, 6×6 (2×3 boxes) from level 2. Always one solution. */
object Sudokus {

    /** (size, boxRows, boxCols, target clue count) per level. */
    private val LEVELS = listOf(
        Spec(4, 2, 2, 8),
        Spec(4, 2, 2, 5),
        Spec(6, 2, 3, 18),
        Spec(6, 2, 3, 14),
        Spec(6, 2, 3, 11),
    )

    private data class Spec(val size: Int, val boxRows: Int, val boxCols: Int, val clues: Int)

    fun generate(level: Int, random: Random = Random.Default): Puzzle.Sudoku {
        val spec = LEVELS[level.coerceIn(0, LEVELS.lastIndex)]
        val grid = Grid(spec.size, spec.boxRows, spec.boxCols)
        val solution = IntArray(spec.size * spec.size)
        check(grid.fill(solution, random)) { "couldn't fill a grid" }

        // Remove clues in random order, keeping each removal only if the solution stays unique.
        val puzzle = solution.copyOf()
        var clues = puzzle.size
        for (cell in puzzle.indices.shuffled(random)) {
            if (clues <= spec.clues) break
            val saved = puzzle[cell]
            puzzle[cell] = 0
            if (grid.countSolutions(puzzle.copyOf(), limit = 2) == 1) clues-- else puzzle[cell] = saved
        }
        return Puzzle.Sudoku(spec.size, spec.boxRows, spec.boxCols, puzzle.toList(), solution.toList())
    }

    /** Indices of cells clashing with another cell in their row, column or box (for highlighting). */
    fun conflicts(size: Int, boxRows: Int, boxCols: Int, grid: List<Int>): Set<Int> {
        val g = Grid(size, boxRows, boxCols)
        return grid.indices.filter { i -> grid[i] != 0 && !g.allowed(grid.toIntArray().also { it[i] = 0 }, i, grid[i]) }.toSet()
    }

    private class Grid(val size: Int, val boxRows: Int, val boxCols: Int) {
        fun allowed(cells: IntArray, index: Int, value: Int): Boolean {
            val row = index / size
            val col = index % size
            for (k in 0 until size) {
                if (cells[row * size + k] == value || cells[k * size + col] == value) return false
            }
            val br = row / boxRows * boxRows
            val bc = col / boxCols * boxCols
            for (r in br until br + boxRows) for (c in bc until bc + boxCols) {
                if (cells[r * size + c] == value) return false
            }
            return true
        }

        /** Fills blanks with a random valid solution. */
        fun fill(cells: IntArray, random: Random): Boolean {
            val i = cells.indexOf(0)
            if (i < 0) return true
            for (v in (1..size).shuffled(random)) {
                if (allowed(cells, i, v)) {
                    cells[i] = v
                    if (fill(cells, random)) return true
                    cells[i] = 0
                }
            }
            return false
        }

        /** Number of solutions, stopping at [limit]. */
        fun countSolutions(cells: IntArray, limit: Int): Int {
            val i = cells.indexOf(0)
            if (i < 0) return 1
            var count = 0
            for (v in 1..size) {
                if (allowed(cells, i, v)) {
                    cells[i] = v
                    count += countSolutions(cells, limit - count)
                    cells[i] = 0
                    if (count >= limit) break
                }
            }
            return count
        }
    }
}
