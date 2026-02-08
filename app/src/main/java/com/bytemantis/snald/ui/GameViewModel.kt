package com.bytemantis.snald.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bytemantis.snald.logic.GameEngine
import com.bytemantis.snald.model.Player

class GameViewModel : ViewModel() {

    // The Engine Contractor (The Brain)
    private val engine = GameEngine()

    // The State (What the UI sees)
    // We use "LiveData" so the UI updates automatically when these change.

    private val _playerState = MutableLiveData<Player>()
    val playerState: LiveData<Player> = _playerState

    private val _lastMoveResult = MutableLiveData<GameEngine.MoveResult>()
    val lastMoveResult: LiveData<GameEngine.MoveResult> = _lastMoveResult

    private val _diceValue = MutableLiveData<Int>()
    val diceValue: LiveData<Int> = _diceValue

    init {
        // Start the game with Player 1
        _playerState.value = Player(id = 1, name = "Player 1")
    }

    // This is the ONLY function the UI is allowed to call.
    fun rollDice() {
        val currentPlayer = _playerState.value ?: return
        if (currentPlayer.isWinner) return // Game Over, no more rolling

        // 1. Roll the dice (Random 1-6)
        val rolledNumber = (1..6).random()
        _diceValue.value = rolledNumber

        // 2. Ask the Contractor to calculate the result
        val result = engine.calculateMove(currentPlayer, rolledNumber)

        // 3. Update the LiveData (This triggers the UI to animate)
        _lastMoveResult.value = result

        // Force the UI to refresh the player data
        _playerState.value = currentPlayer
    }

    // Helper to reset game if needed
    fun resetGame() {
        _playerState.value = Player(id = 1, name = "Player 1")
        _diceValue.value = 1
    }
}