package com.bytemantis.snald.model

data class Player(
    val id: Int,
    val name: String,
    var currentPosition: Int = 1, // Everyone starts at square 1
    var hasStar: Boolean = false, // The Immunity State
    var isWinner: Boolean = false
)