package com.bytemantis.snald.logic

object BoardConfig {
    const val BOARD_SIZE = 100

    // 🐍 Snakes (Head -> Tail) - CORRECTED
    val SNAKES = mapOf(
        95 to 3,   // Giant Red Cobra
        91 to 73,  // Top Right Green Snake
        88 to 85,  // Horizontal Gold Snake
        83 to 57,  // Pink/Purple Snake
        81 to 61,  // Top Left Green Snake
        59 to 40,  // Mid-Left Red Snake
        52 to 32,  // Mid-Right Blue Snake
        29 to 10   // Bottom Right Green Snake
    )

    // 🪜 Ladders (Bottom -> Top) - CORRECTED
    val LADDERS = mapOf(
        62 to 98,  // Top Left Green Ladder
        51 to 71,  // Top Right Pink Ladder
        48 to 75,  // Mid-Right Blue Ladder
        27 to 55,  // Center Green Ladder
        19 to 58,  // Long Blue Ladder
        7 to 28    // Bottom Right Ladder
    )

    // ⭐ Stars (Immunity Pickup) - CORRECTED
    val STARS = setOf(20, 37, 54, 79, 94)
}