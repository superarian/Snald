package com.bytemantis.snald.ludogame

data class LudoPlayer(
    val id: Int,
    val colorName: String,
    val tokenCount: Int = 4, // Dynamic count
    val tokenPositions: MutableList<Int> = MutableList(tokenCount) { -1 }
) {
    fun getFinishedCount(): Int = tokenPositions.count { it == 57 }
    fun isTokenInBase(index: Int): Boolean = tokenPositions[index] == -1
}