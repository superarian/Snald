package com.bytemantis.snald.ludogame

object LudoBoardConfig {
    // The board is a 15x15 grid
    const val GRID_SIZE = 15

    // --- 1. BASE POSITIONS (The 4 circles in the corners) ---
    // Top-Left (Red), Top-Right (Green), Bottom-Right (Blue), Bottom-Left (Yellow)
    // Coordinates are (Column, Row) where (0,0) is top-left.

    val RED_BASE = listOf(Pair(2, 2), Pair(3, 2), Pair(2, 3), Pair(3, 3))
    val GREEN_BASE = listOf(Pair(11, 2), Pair(12, 2), Pair(11, 3), Pair(12, 3))
    val YELLOW_BASE = listOf(Pair(2, 11), Pair(3, 11), Pair(2, 12), Pair(3, 12))
    val BLUE_BASE = listOf(Pair(11, 11), Pair(12, 11), Pair(11, 12), Pair(12, 12))

    // --- 2. THE MAIN PATHS ---
    // Each path is a list of coordinates the token moves through step-by-step.

    // RED PATH (Starts Top-Left, Exits Right)
    val PATH_RED = listOf(
        Pair(1, 6), Pair(2, 6), Pair(3, 6), Pair(4, 6), Pair(5, 6), // Out
        Pair(6, 5), Pair(6, 4), Pair(6, 3), Pair(6, 2), Pair(6, 1), Pair(6, 0), // Up Green Arm
        Pair(7, 0), Pair(8, 0), // Top Turn
        Pair(8, 1), Pair(8, 2), Pair(8, 3), Pair(8, 4), Pair(8, 5), // Down Green Arm
        Pair(9, 6), Pair(10, 6), Pair(11, 6), Pair(12, 6), Pair(13, 6), Pair(14, 6), // Right Blue Arm
        Pair(14, 7), Pair(14, 8), // Right Turn
        Pair(13, 8), Pair(12, 8), Pair(11, 8), Pair(10, 8), Pair(9, 8), // Left Blue Arm
        Pair(8, 9), Pair(8, 10), Pair(8, 11), Pair(8, 12), Pair(8, 13), Pair(8, 14), // Down Yellow Arm
        Pair(7, 14), Pair(6, 14), // Bottom Turn
        Pair(6, 13), Pair(6, 12), Pair(6, 11), Pair(6, 10), Pair(6, 9), // Up Yellow Arm
        Pair(5, 8), Pair(4, 8), Pair(3, 8), Pair(2, 8), Pair(1, 8), Pair(0, 8), // Left Red Arm
        Pair(0, 7), // Final Turn
        // HOME STRETCH
        Pair(1, 7), Pair(2, 7), Pair(3, 7), Pair(4, 7), Pair(5, 7), Pair(6, 7) // WIN
    )

    // GREEN PATH (Starts Top-Right, Exits Down)
    val PATH_GREEN = listOf(
        Pair(8, 1), Pair(8, 2), Pair(8, 3), Pair(8, 4), Pair(8, 5),
        Pair(9, 6), Pair(10, 6), Pair(11, 6), Pair(12, 6), Pair(13, 6), Pair(14, 6),
        Pair(14, 7), Pair(14, 8),
        Pair(13, 8), Pair(12, 8), Pair(11, 8), Pair(10, 8), Pair(9, 8),
        Pair(8, 9), Pair(8, 10), Pair(8, 11), Pair(8, 12), Pair(8, 13), Pair(8, 14),
        Pair(7, 14), Pair(6, 14),
        Pair(6, 13), Pair(6, 12), Pair(6, 11), Pair(6, 10), Pair(6, 9),
        Pair(5, 8), Pair(4, 8), Pair(3, 8), Pair(2, 8), Pair(1, 8), Pair(0, 8),
        Pair(0, 7), Pair(0, 6),
        Pair(1, 6), Pair(2, 6), Pair(3, 6), Pair(4, 6), Pair(5, 6),
        Pair(6, 5), Pair(6, 4), Pair(6, 3), Pair(6, 2), Pair(6, 1), Pair(6, 0),
        Pair(7, 0),
        // HOME STRETCH
        Pair(7, 1), Pair(7, 2), Pair(7, 3), Pair(7, 4), Pair(7, 5), Pair(7, 6) // WIN
    )

    // YELLOW PATH (Starts Bottom-Left, Exits Up)
    val PATH_YELLOW = listOf(
        Pair(6, 13), Pair(6, 12), Pair(6, 11), Pair(6, 10), Pair(6, 9),
        Pair(5, 8), Pair(4, 8), Pair(3, 8), Pair(2, 8), Pair(1, 8), Pair(0, 8),
        Pair(0, 7), Pair(0, 6),
        Pair(1, 6), Pair(2, 6), Pair(3, 6), Pair(4, 6), Pair(5, 6),
        Pair(6, 5), Pair(6, 4), Pair(6, 3), Pair(6, 2), Pair(6, 1), Pair(6, 0),
        Pair(7, 0), Pair(8, 0),
        Pair(8, 1), Pair(8, 2), Pair(8, 3), Pair(8, 4), Pair(8, 5),
        Pair(9, 6), Pair(10, 6), Pair(11, 6), Pair(12, 6), Pair(13, 6), Pair(14, 6),
        Pair(14, 7), Pair(14, 8),
        Pair(13, 8), Pair(12, 8), Pair(11, 8), Pair(10, 8), Pair(9, 8),
        Pair(8, 9), Pair(8, 10), Pair(8, 11), Pair(8, 12), Pair(8, 13), Pair(8, 14),
        Pair(7, 14),
        // HOME STRETCH
        Pair(7, 13), Pair(7, 12), Pair(7, 11), Pair(7, 10), Pair(7, 9), Pair(7, 8) // WIN
    )

    // BLUE PATH (Starts Bottom-Right, Exits Left)
    val PATH_BLUE = listOf(
        Pair(13, 8), Pair(12, 8), Pair(11, 8), Pair(10, 8), Pair(9, 8),
        Pair(8, 9), Pair(8, 10), Pair(8, 11), Pair(8, 12), Pair(8, 13), Pair(8, 14),
        Pair(7, 14), Pair(6, 14),
        Pair(6, 13), Pair(6, 12), Pair(6, 11), Pair(6, 10), Pair(6, 9),
        Pair(5, 8), Pair(4, 8), Pair(3, 8), Pair(2, 8), Pair(1, 8), Pair(0, 8),
        Pair(0, 7), Pair(0, 6),
        Pair(1, 6), Pair(2, 6), Pair(3, 6), Pair(4, 6), Pair(5, 6),
        Pair(6, 5), Pair(6, 4), Pair(6, 3), Pair(6, 2), Pair(6, 1), Pair(6, 0),
        Pair(7, 0), Pair(8, 0),
        Pair(8, 1), Pair(8, 2), Pair(8, 3), Pair(8, 4), Pair(8, 5),
        Pair(9, 6), Pair(10, 6), Pair(11, 6), Pair(12, 6), Pair(13, 6), Pair(14, 6),
        Pair(14, 7),
        // HOME STRETCH
        Pair(13, 7), Pair(12, 7), Pair(11, 7), Pair(10, 7), Pair(9, 7), Pair(8, 7) // WIN
    )
}