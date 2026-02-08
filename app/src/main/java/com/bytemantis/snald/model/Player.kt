package com.bytemantis.snald.model

data class Player(
    val id: Int,
    val name: String,
    val color: Int, // Hex Color Code
    var currentPosition: Int = 1,
    var hasStar: Boolean = false,
    var isFinished: Boolean = false // True if they won and are waiting for others
)