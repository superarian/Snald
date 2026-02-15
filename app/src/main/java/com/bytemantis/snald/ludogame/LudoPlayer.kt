package com.bytemantis.snald.ludogame

data class LudoPlayer(
    val id: Int,             // 1=Red, 2=Green, 3=Yellow, 4=Blue
    val colorName: String,   // "RED", "GREEN", "YELLOW", "BLUE"

    // Tracks the position of all 4 tokens.
    // -1  = Inside Base (Safe)
    // 0   = Start Point (First square outside base)
    // 1-50 = Normal Path
    // 51-56 = Home Stretch
    // 57  = WON (Home)
    val tokenPositions: MutableList<Int> = mutableListOf(-1, -1, -1, -1)
) {
    // Helper: Count how many tokens are fully home
    fun getFinishedCount(): Int = tokenPositions.count { it == 57 }

    // Helper: Check if a token is locked in base
    fun isTokenInBase(index: Int): Boolean = tokenPositions[index] == -1
}