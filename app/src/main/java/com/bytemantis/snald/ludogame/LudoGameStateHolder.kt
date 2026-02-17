package com.bytemantis.snald.ludogame

import androidx.lifecycle.MutableLiveData

/**
 * OWNER APPROACH:
 * Singleton to hold Ludo state even if the Activity is destroyed.
 * This ensures the game "Pauses" when hitting Back and "Resumes" when re-opening.
 */
object LudoGameStateHolder {
    var hasActiveGame: Boolean = false
    var playerCount: Int = 4

    // Data to persist
    var players: List<LudoPlayer> = emptyList()
    var activePlayerIndex: Int = 0
    var diceValue: Int = 0
    var gameState: LudoViewModel.State = LudoViewModel.State.WAITING_FOR_ROLL
    var statusMessage: String = "Welcome Back!"

    // Track rank for victory persistence
    var rankCounter: Int = 0

    fun saveState(
        _players: List<LudoPlayer>,
        _activeIdx: Int,
        _dice: Int,
        _state: LudoViewModel.State,
        _msg: String,
        _rank: Int,
        _count: Int
    ) {
        players = _players
        activePlayerIndex = _activeIdx
        diceValue = _dice
        gameState = _state
        statusMessage = _msg
        rankCounter = _rank
        playerCount = _count
        hasActiveGame = true
    }

    fun clear() {
        hasActiveGame = false
        players = emptyList()
        activePlayerIndex = 0
        diceValue = 0
        gameState = LudoViewModel.State.WAITING_FOR_ROLL
        rankCounter = 0
    }
}