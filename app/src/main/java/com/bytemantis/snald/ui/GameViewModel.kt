package com.bytemantis.snald.ui

import android.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bytemantis.snald.logic.GameEngine
import com.bytemantis.snald.model.Player

class GameViewModel : ViewModel() {

    private val engine = GameEngine()

    // List of all players
    private val _players = MutableLiveData<List<Player>>()
    val players: LiveData<List<Player>> = _players

    // ID of the player whose turn it is
    private val _activePlayerId = MutableLiveData<Int>()
    val activePlayerId: LiveData<Int> = _activePlayerId

    private val _lastMoveResult = MutableLiveData<GameEngine.MoveResult>()
    val lastMoveResult: LiveData<GameEngine.MoveResult> = _lastMoveResult

    private val _diceValue = MutableLiveData<Int>()
    val diceValue: LiveData<Int> = _diceValue

    // Logic: Collided player needs to go back
    private val _collisionEvent = MutableLiveData<Player?>() // Player who got killed
    val collisionEvent: LiveData<Player?> = _collisionEvent

    private val _gameOver = MutableLiveData<Boolean>()
    val gameOver: LiveData<Boolean> = _gameOver

    private var totalPlayers = 2
    private var turnIndex = 0

    fun startGame(playerCount: Int) {
        totalPlayers = playerCount
        val newPlayers = ArrayList<Player>()

        // Setup Players with Colors
        // P1: Red, P2: Blue, P3: Green, P4: Yellow
        newPlayers.add(Player(1, "P1", Color.RED))
        if (playerCount >= 2) newPlayers.add(Player(2, "P2", Color.BLUE))
        if (playerCount >= 3) newPlayers.add(Player(3, "P3", Color.GREEN))
        if (playerCount >= 4) newPlayers.add(Player(4, "P4", Color.YELLOW))

        _players.value = newPlayers
        turnIndex = 0
        _activePlayerId.value = newPlayers[0].id
        _gameOver.value = false
    }

    fun rollDiceForActivePlayer() {
        val currentList = _players.value ?: return
        val activePlayer = currentList[turnIndex]

        if (activePlayer.isFinished) {
            nextTurn()
            return
        }

        val rolledNumber = (1..6).random()
        _diceValue.value = rolledNumber

        val result = engine.calculateMove(activePlayer, rolledNumber)
        _lastMoveResult.value = result

        // Logic will be handled in UI (Animation), then updatePosition is called
    }

    fun updatePositionAndNextTurn(finalPos: Int) {
        val currentList = _players.value ?: return
        val activePlayer = currentList[turnIndex]

        // 1. Update Position
        activePlayer.currentPosition = finalPos

        // 2. Check Win
        if (finalPos == 100) {
            activePlayer.isFinished = true
            // Check Game Over Condition (All finished except 1)
            val activePlayersCount = currentList.count { !it.isFinished }
            if (activePlayersCount <= 1) {
                _gameOver.value = true
            }
        }

        // 3. Check Collision (KILLING)
        // If active player landed on enemy, and enemy has NO star
        val enemy = engine.checkCollisions(activePlayer, currentList)
        if (enemy != null) {
            if (enemy.hasStar) {
                // Enemy survives, loses star
                enemy.hasStar = false
                _collisionEvent.value = null // No kill, just update state
            } else {
                // KILL! Enemy goes to 1
                enemy.currentPosition = 1
                _collisionEvent.value = enemy
            }
        }

        _players.value = currentList // Refresh UI

        if (_gameOver.value != true) {
            nextTurn()
        }
    }

    private fun nextTurn() {
        val currentList = _players.value ?: return
        var nextIndex = (turnIndex + 1) % currentList.size

        // Skip finished players
        while (currentList[nextIndex].isFinished) {
            nextIndex = (nextIndex + 1) % currentList.size
            // Safety break if game is actually over
            if (currentList.all { it.isFinished }) break
        }

        turnIndex = nextIndex
        _activePlayerId.value = currentList[turnIndex].id
    }
}