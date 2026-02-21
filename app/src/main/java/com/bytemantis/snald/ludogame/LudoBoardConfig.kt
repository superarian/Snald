package com.bytemantis.snald.ludogame

object LudoBoardConfig {
    const val GRID_SIZE = 15

    // ==========================================
    // OWNER FIX: Dynamic Token Spread System
    // Change this one number to push tokens closer together or further apart.
    // 1.35f is mathematically ideal for a standard 15x15 board graphic.
    const val TOKEN_SPREAD = 0.75f
    // ==========================================

    // The exact visual centers of the 6x6 bases on a 15x15 grid
    private val CENTER_RED = Pair(3.0f, 3.0f)     // Top Left
    private val CENTER_GREEN = Pair(12.0f, 3.0f)  // Top Right
    private val CENTER_BLUE = Pair(12.0f, 12.0f)  // Bottom Right
    private val CENTER_YELLOW = Pair(3.0f, 12.0f) // Bottom Left

    fun getBasePreciseCoord(playerIndex: Int, tokenIndex: Int): Pair<Float, Float> {
        val center = when (playerIndex) {
            0 -> CENTER_RED
            1 -> CENTER_GREEN
            2 -> CENTER_BLUE
            else -> CENTER_YELLOW
        }

        // Project the 4 tokens evenly around the exact center of the base
        return when (tokenIndex) {
            0 -> Pair(center.first - TOKEN_SPREAD, center.second - TOKEN_SPREAD) // Top Left
            1 -> Pair(center.first + TOKEN_SPREAD, center.second - TOKEN_SPREAD) // Top Right
            2 -> Pair(center.first - TOKEN_SPREAD, center.second + TOKEN_SPREAD) // Bottom Left
            else -> Pair(center.first + TOKEN_SPREAD, center.second + TOKEN_SPREAD) // Bottom Right
        }
    }

    // --- GLOBAL SAFE ZONES ---
    val SAFE_ZONES = setOf(
        Pair(1, 6),  // Red Start
        Pair(8, 1),  // Green Start
        Pair(6, 13), // Yellow Start
        Pair(13, 8), // Blue Start
        Pair(2, 8), Pair(6, 2), Pair(12, 6), Pair(8, 12)
    )

    // --- PATHS ---
    val PATH_RED = listOf(
        Pair(1, 6), Pair(2, 6), Pair(3, 6), Pair(4, 6), Pair(5, 6), Pair(6, 5), Pair(6, 4), Pair(6, 3), Pair(6, 2), Pair(6, 1), Pair(6, 0), Pair(7, 0), Pair(8, 0),
        Pair(8, 1), Pair(8, 2), Pair(8, 3), Pair(8, 4), Pair(8, 5), Pair(9, 6), Pair(10, 6), Pair(11, 6), Pair(12, 6), Pair(13, 6), Pair(14, 6), Pair(14, 7), Pair(14, 8),
        Pair(13, 8), Pair(12, 8), Pair(11, 8), Pair(10, 8), Pair(9, 8), Pair(8, 9), Pair(8, 10), Pair(8, 11), Pair(8, 12), Pair(8, 13), Pair(8, 14), Pair(7, 14), Pair(6, 14),
        Pair(6, 13), Pair(6, 12), Pair(6, 11), Pair(6, 10), Pair(6, 9), Pair(5, 8), Pair(4, 8), Pair(3, 8), Pair(2, 8), Pair(1, 8), Pair(0, 8), Pair(0, 7),
        Pair(1, 7), Pair(2, 7), Pair(3, 7), Pair(4, 7), Pair(5, 7), Pair(6, 7)
    )

    val PATH_GREEN = listOf(
        Pair(8, 1), Pair(8, 2), Pair(8, 3), Pair(8, 4), Pair(8, 5), Pair(9, 6), Pair(10, 6), Pair(11, 6), Pair(12, 6), Pair(13, 6), Pair(14, 6), Pair(14, 7), Pair(14, 8),
        Pair(13, 8), Pair(12, 8), Pair(11, 8), Pair(10, 8), Pair(9, 8), Pair(8, 9), Pair(8, 10), Pair(8, 11), Pair(8, 12), Pair(8, 13), Pair(8, 14), Pair(7, 14), Pair(6, 14),
        Pair(6, 13), Pair(6, 12), Pair(6, 11), Pair(6, 10), Pair(6, 9), Pair(5, 8), Pair(4, 8), Pair(3, 8), Pair(2, 8), Pair(1, 8), Pair(0, 8), Pair(0, 7), Pair(0, 6),
        Pair(1, 6), Pair(2, 6), Pair(3, 6), Pair(4, 6), Pair(5, 6), Pair(6, 5), Pair(6, 4), Pair(6, 3), Pair(6, 2), Pair(6, 1), Pair(6, 0), Pair(7, 0),
        Pair(7, 1), Pair(7, 2), Pair(7, 3), Pair(7, 4), Pair(7, 5), Pair(7, 6)
    )

    val PATH_BLUE = listOf(
        Pair(13, 8), Pair(12, 8), Pair(11, 8), Pair(10, 8), Pair(9, 8), Pair(8, 9), Pair(8, 10), Pair(8, 11), Pair(8, 12), Pair(8, 13), Pair(8, 14), Pair(7, 14), Pair(6, 14),
        Pair(6, 13), Pair(6, 12), Pair(6, 11), Pair(6, 10), Pair(6, 9), Pair(5, 8), Pair(4, 8), Pair(3, 8), Pair(2, 8), Pair(1, 8), Pair(0, 8), Pair(0, 7), Pair(0, 6),
        Pair(1, 6), Pair(2, 6), Pair(3, 6), Pair(4, 6), Pair(5, 6), Pair(6, 5), Pair(6, 4), Pair(6, 3), Pair(6, 2), Pair(6, 1), Pair(6, 0), Pair(7, 0), Pair(8, 0),
        Pair(8, 1), Pair(8, 2), Pair(8, 3), Pair(8, 4), Pair(8, 5), Pair(9, 6), Pair(10, 6), Pair(11, 6), Pair(12, 6), Pair(13, 6), Pair(14, 6), Pair(14, 7),
        Pair(13, 7), Pair(12, 7), Pair(11, 7), Pair(10, 7), Pair(9, 7), Pair(8, 7)
    )

    val PATH_YELLOW = listOf(
        Pair(6, 13), Pair(6, 12), Pair(6, 11), Pair(6, 10), Pair(6, 9), Pair(5, 8), Pair(4, 8), Pair(3, 8), Pair(2, 8), Pair(1, 8), Pair(0, 8), Pair(0, 7), Pair(0, 6),
        Pair(1, 6), Pair(2, 6), Pair(3, 6), Pair(4, 6), Pair(5, 6), Pair(6, 5), Pair(6, 4), Pair(6, 3), Pair(6, 2), Pair(6, 1), Pair(6, 0), Pair(7, 0), Pair(8, 0),
        Pair(8, 1), Pair(8, 2), Pair(8, 3), Pair(8, 4), Pair(8, 5), Pair(9, 6), Pair(10, 6), Pair(11, 6), Pair(12, 6), Pair(13, 6), Pair(14, 6), Pair(14, 7), Pair(14, 8),
        Pair(13, 8), Pair(12, 8), Pair(11, 8), Pair(10, 8), Pair(9, 8), Pair(8, 9), Pair(8, 10), Pair(8, 11), Pair(8, 12), Pair(8, 13), Pair(8, 14), Pair(7, 14),
        Pair(7, 13), Pair(7, 12), Pair(7, 11), Pair(7, 10), Pair(7, 9), Pair(7, 8)
    )

    fun getGlobalCoord(playerIndex: Int, stepIndex: Int): Pair<Int, Int>? {
        if (stepIndex < 0 || stepIndex > 56) return null
        val path = when(playerIndex) {
            0 -> PATH_RED
            1 -> PATH_GREEN
            2 -> PATH_BLUE
            else -> PATH_YELLOW
        }
        if (stepIndex >= path.size) return null
        return path.get(stepIndex)
    }
}