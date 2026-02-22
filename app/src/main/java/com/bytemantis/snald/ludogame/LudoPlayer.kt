package com.bytemantis.snald.ludogame

data class LudoPlayer(
    val id: Int,
    val colorName: String,
    val tokenCount: Int = 4,
    val tokenPositions: MutableList<Int> = MutableList(tokenCount) { -1 },
    // NEW: RPG Stats tracking
    var kills: Int = 0,
    var deaths: Int = 0,
    var sixesRolled: Int = 0
) {
    fun getFinishedCount(): Int = tokenPositions.count { it == 56 }
    fun isTokenInBase(index: Int): Boolean = tokenPositions.get(index) == -1
}