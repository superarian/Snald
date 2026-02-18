package com.bytemantis.snald.ludogame

object LudoBoardConfig {
    const val GRID_SIZE = 15

    // --- BASE COORDINATES (Safe Havens) ---
    val RED_BASE_PRECISE = listOf(Pair(1.5f, 1.5f), Pair(3.5f, 1.5f), Pair(1.5f, 3.5f), Pair(3.5f, 3.5f))
    val GREEN_BASE_PRECISE = listOf(Pair(10.5f, 1.5f), Pair(12.5f, 1.5f), Pair(10.5f, 3.5f), Pair(12.5f, 3.5f))
    val YELLOW_BASE_PRECISE = listOf(Pair(1.5f, 10.5f), Pair(3.5f, 10.5f), Pair(1.5f, 12.5f), Pair(3.5f, 12.5f))
    val BLUE_BASE_PRECISE = listOf(Pair(10.5f, 10.5f), Pair(12.5f, 10.5f), Pair(10.5f, 12.5f), Pair(12.5f, 12.5f))

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
        // OWNER FIX: Allow index 57 (Center/Home)
        if (stepIndex == 57) return Pair(7, 7)

        if (stepIndex < 0 || stepIndex > 56) return null
        val path = when(playerIndex) {
            0 -> PATH_RED
            1 -> PATH_GREEN
            2 -> PATH_BLUE
            else -> PATH_YELLOW
        }
        if (stepIndex >= path.size) return null
        return path[stepIndex]
    }
}