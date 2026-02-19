package com.bytemantis.snald.ludogame

data class LudoPlayer(
    val id: Int,
    val colorName: String,
    val tokenCount: Int = 4, // Dynamic count
    val tokenPositions: MutableList<Int> = MutableList(tokenCount) { -1 }
) {
    // FIX: 56 is the end of the path (Center)
    fun getFinishedCount(): Int = tokenPositions.count { it == 56 }
    fun isTokenInBase(index: Int): Boolean = tokenPositions.get(index) == -1
}