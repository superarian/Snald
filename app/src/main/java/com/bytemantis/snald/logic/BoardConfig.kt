package com.bytemantis.snald.logic

object BoardConfig {
    const val BOARD_SIZE = 100

    // 🐍 Snakes (Head -> Tail)
    val SNAKES = mapOf(
        45 to 4,   // NEW: Giant Snake shifted
        96 to 93,  // NEW: Small Toxic Snake
        91 to 73,
        88 to 85,
        83 to 57,
        81 to 61,
        59 to 40,
        52 to 32,
        29 to 10
    )

    // 🪜 Ladders (Bottom -> Top)
    val LADDERS = mapOf(
        62 to 98,
        51 to 71,
        48 to 75,
        27 to 55,
        19 to 58,
        7 to 28
    )

    // ⭐ Stars (Immunity Pickup)
    val STARS = setOf(20, 37, 54, 79, 94)
}