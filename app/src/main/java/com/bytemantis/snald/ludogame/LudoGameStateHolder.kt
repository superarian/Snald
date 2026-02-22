package com.bytemantis.snald.ludogame

object LudoGameStateHolder {
    var hasActiveGame: Boolean = false
    var players: List<LudoPlayer> = emptyList()
    var activePlayerIndex: Int = 0
    var diceValue: Int = 0
    var gameState: LudoViewModel.State = LudoViewModel.State.SETUP_THEME
    var statusMessage: String = "Select Board"
    var rankCounter: Int = 0
    var finishedPlayerIds: MutableSet<Int> = mutableSetOf()

    var timerSeconds: Int = 30
    var dynamicSafeZone: Pair<Int, Int>? = null // OWNER FIX: Only one star allowed

    fun saveState(
        _players: List<LudoPlayer>,
        _activeIdx: Int,
        _dice: Int,
        _state: LudoViewModel.State,
        _msg: String,
        _rank: Int,
        _finished: Set<Int>,
        _timer: Int,
        _dynamicSafeZone: Pair<Int, Int>?
    ) {
        players = _players
        activePlayerIndex = _activeIdx
        diceValue = _dice
        gameState = if (_state == LudoViewModel.State.ANIMATING) LudoViewModel.State.WAITING_FOR_ROLL else _state
        statusMessage = _msg
        rankCounter = _rank
        finishedPlayerIds = _finished.toMutableSet()
        timerSeconds = _timer
        dynamicSafeZone = _dynamicSafeZone
        hasActiveGame = true
    }

    fun clear() {
        hasActiveGame = false
        players = emptyList()
        activePlayerIndex = 0
        diceValue = 0
        gameState = LudoViewModel.State.SETUP_THEME
        rankCounter = 0
        finishedPlayerIds.clear()
        statusMessage = "Select Board"
        timerSeconds = 30
        dynamicSafeZone = null
    }
}