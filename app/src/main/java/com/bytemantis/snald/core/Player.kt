package com.bytemantis.snald.core

data class Player(
    val id: Int,
    val name: String,
    val color: Int, // Hex Color Code
    var currentPosition: Int = 1,

    // CHANGED: Star is now a counter (collect multiple)
    var starCount: Int = 0,

    // NEW: Pac-Man Entity State (0 = inactive)
    var pacmanPosition: Int = 0,

    var isFinished: Boolean = false
) {
    // Helper: Has at least 1 star for immunity
    val hasShield: Boolean
        get() = starCount >= 1

    // Helper: Is Pac-Man currently on the board?
    val isPacmanActive: Boolean
        get() = pacmanPosition > 0
}