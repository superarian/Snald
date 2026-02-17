package com.bytemantis.snald.ludogame

object LudoGameStateHolder {
    var hasActiveGame: Boolean = false
    var players: List<LudoPlayer> = emptyList()
    var activePlayerIndex: Int = 0
    var diceValue: Int = 0
    var gameState: LudoViewModel.State = LudoViewModel.State.SETUP_PLAYERS
    var statusMessage: String = "Select Players"
    var rankCounter: Int = 0

    fun saveState(
        _players: List<LudoPlayer>,
        _activeIdx: Int,
        _dice: Int,
        _state: LudoViewModel.State,
        _msg: String,
        _rank: Int
    ) {
        players = _players
        activePlayerIndex = _activeIdx
        diceValue = _dice

        // OWNER FIX: Never save in ANIMATING state.
        // If the user quits during a move, reset to WAITING_FOR_ROLL to prevent being stuck.
        gameState = if (_state == LudoViewModel.State.ANIMATING)
            LudoViewModel.State.WAITING_FOR_ROLL else _state

        statusMessage = _msg
        rankCounter = _rank
        hasActiveGame = true
    }

    fun clear() {
        hasActiveGame = false
        players = emptyList()
        activePlayerIndex = 0
        diceValue = 0
        gameState = LudoViewModel.State.SETUP_PLAYERS
        rankCounter = 0
        statusMessage = "Select Players"
    }
}