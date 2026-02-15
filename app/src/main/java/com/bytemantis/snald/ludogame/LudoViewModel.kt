package com.bytemantis.snald.ludogame

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LudoViewModel : ViewModel() {

    enum class State { WAITING_FOR_ROLL, WAITING_FOR_MOVE, ANIMATING }

    private val _gameState = MutableLiveData(State.WAITING_FOR_ROLL)
    val gameState: LiveData<State> = _gameState

    private val _players = MutableLiveData<List<LudoPlayer>>()
    val players: LiveData<List<LudoPlayer>> = _players

    private val _activePlayerIndex = MutableLiveData(0)
    val activePlayerIndex: LiveData<Int> = _activePlayerIndex

    private val _diceValue = MutableLiveData<Int>()
    val diceValue: LiveData<Int> = _diceValue

    private val _statusMessage = MutableLiveData("Welcome to Ludo!")
    val statusMessage: LiveData<String> = _statusMessage

    // NEW: Event to tell Activity to animate a token
    // Triple: (PlayerIndex, TokenIndex, StepsToMove)
    private val _moveEvent = MutableLiveData<Triple<Int, Int, Int>?>()
    val moveEvent: LiveData<Triple<Int, Int, Int>?> = _moveEvent

    init {
        _players.value = listOf(
            LudoPlayer(1, "RED"),
            LudoPlayer(2, "GREEN"),
            LudoPlayer(3, "YELLOW"),
            LudoPlayer(4, "BLUE")
        )
        _statusMessage.value = "Red's Turn! Roll the dice."
    }

    fun rollDice() {
        if (_gameState.value != State.WAITING_FOR_ROLL) return

        _gameState.value = State.ANIMATING
        val roll = (1..6).random()
        _diceValue.value = roll

        viewModelScope.launch {
            delay(500)
            val currentPlayer = _players.value!![_activePlayerIndex.value!!]

            if (canPlayerMove(currentPlayer, roll)) {
                _statusMessage.value = "Select a token to move."
                _gameState.value = State.WAITING_FOR_MOVE

                // AUTO-MOVE RULE: If only 1 legal move exists, do it automatically?
                // For now, let's force the user to click to keep it simple.
            } else {
                _statusMessage.value = "No moves possible."
                delay(1000)
                nextTurn()
            }
        }
    }

    fun onTokenClicked(tokenIndex: Int) {
        if (_gameState.value != State.WAITING_FOR_MOVE) return

        val playerIndex = _activePlayerIndex.value!!
        val player = _players.value!![playerIndex]
        val roll = _diceValue.value!!
        val currentPos = player.tokenPositions[tokenIndex]

        // 1. VALIDATE MOVE
        var isValid = false
        var newPos = currentPos

        if (currentPos == -1) {
            // Base to Start
            if (roll == 6) {
                isValid = true
                newPos = 0 // Start of path
            }
        } else {
            // Normal Movement
            if (currentPos + roll <= 56) { // 56 is the last square (Victory)
                isValid = true
                newPos = currentPos + roll
            }
        }

        if (!isValid) {
            _statusMessage.value = "Invalid Move!"
            return
        }

        // 2. EXECUTE MOVE
        _gameState.value = State.ANIMATING

        // Update data model
        val updatedPositions = player.tokenPositions.toMutableList()
        updatedPositions[tokenIndex] = newPos

        // We need to update the _players list to trigger LiveData?
        // Actually, we just modify the list inside the object for now,
        // but to be safe/clean in MVVM we should refresh the list.
        player.tokenPositions[tokenIndex] = newPos

        // Trigger Animation in Activity
        // Note: If coming from base (-1 -> 0), we treat it as "1 step" visually for now
        val visualSteps = if (currentPos == -1) 1 else roll
        _moveEvent.value = Triple(playerIndex, tokenIndex, visualSteps)
    }

    fun onAnimationFinished() {
        _moveEvent.value = null // Reset event
        val roll = _diceValue.value!!

        // Rule: If you rolled a 6, you get another turn!
        if (roll == 6) {
            _statusMessage.value = "Rolled a 6! Roll again."
            _gameState.value = State.WAITING_FOR_ROLL
        } else {
            nextTurn()
        }
    }

    private fun canPlayerMove(player: LudoPlayer, roll: Int): Boolean {
        var hasMove = false
        player.tokenPositions.forEach { pos ->
            if (pos == -1) {
                if (roll == 6) hasMove = true
            } else if (pos + roll <= 56) {
                hasMove = true
            }
        }
        return hasMove
    }

    private fun nextTurn() {
        val nextIndex = (_activePlayerIndex.value!! + 1) % 4
        _activePlayerIndex.value = nextIndex
        _gameState.value = State.WAITING_FOR_ROLL
        val color = _players.value!![nextIndex].colorName
        _statusMessage.value = "$color's Turn!"
    }
}