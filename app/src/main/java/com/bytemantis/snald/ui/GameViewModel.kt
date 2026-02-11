package com.bytemantis.snald.ui

import android.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bytemantis.snald.logic.GameEngine
import com.bytemantis.snald.model.Player

class GameViewModel : ViewModel() {

    private val engine = GameEngine()

    private val _players = MutableLiveData<List<Player>>()
    val players: LiveData<List<Player>> = _players

    private val _activePlayerId = MutableLiveData<Int>()
    val activePlayerId: LiveData<Int> = _activePlayerId

    private val _lastMoveResult = MutableLiveData<GameEngine.MoveResult>()
    val lastMoveResult: LiveData<GameEngine.MoveResult> = _lastMoveResult

    private val _diceValue = MutableLiveData<Int>()
    val diceValue: LiveData<Int> = _diceValue

    private val _collisionEvent = MutableLiveData<Pair<Player, Int>?>()
    val collisionEvent: LiveData<Pair<Player, Int>?> = _collisionEvent

    private val _gameOver = MutableLiveData<Boolean>()
    val gameOver: LiveData<Boolean> = _gameOver

    private var totalPlayers = 2
    private var turnIndex = 0
    var isAnimationLocked = false

    fun startGame(playerCount: Int) {
        totalPlayers = playerCount
        val newPlayers = ArrayList<Player>()
        newPlayers.add(Player(1, "P1", Color.RED))
        if (playerCount >= 2) newPlayers.add(Player(2, "P2", Color.BLUE))
        if (playerCount >= 3) newPlayers.add(Player(3, "P3", Color.GREEN))
        if (playerCount >= 4) newPlayers.add(Player(4, "P4", Color.YELLOW))

        _players.value = newPlayers
        turnIndex = 0
        _activePlayerId.value = newPlayers[0].id
        _gameOver.value = false
        isAnimationLocked = false
    }

    fun rollDiceForActivePlayer() {
        if (isAnimationLocked) return // Block input during animation

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
    }

    fun updatePositionAndNextTurn(finalPos: Int) {
        val currentList = _players.value ?: return
        val activePlayer = currentList[turnIndex]

        activePlayer.currentPosition = finalPos

        if (finalPos == 100) {
            activePlayer.isFinished = true
            val activePlayersCount = currentList.count { !it.isFinished }
            if (activePlayersCount <= 1) {
                _gameOver.value = true
            }
        }

        val enemy = engine.checkCollisions(activePlayer, currentList)
        if (enemy != null) {
            if (enemy.hasStar) {
                enemy.hasStar = false
                _collisionEvent.value = null
                if (_gameOver.value != true) nextTurn()
            } else {
                // KILL LOGIC
                val fatalPos = enemy.currentPosition
                isAnimationLocked = true

                // CRITICAL: We do NOT reset enemy.currentPosition to 1 yet.
                // We keep them at the fatal position so the UI shows them there
                // while the video plays.

                _collisionEvent.value = Pair(enemy, fatalPos)
                return
            }
        } else {
            _players.value = currentList
            if (_gameOver.value != true) {
                nextTurn()
            }
        }
    }

    // Called by UI AFTER the slide animation is 100% done
    fun finalizeKill(killedPlayerId: Int) {
        val currentList = _players.value ?: return
        val enemy = currentList.find { it.id == killedPlayerId } ?: return

        enemy.currentPosition = 1 // Now strictly set to 1
        _players.value = currentList // Update LiveData to sync everyone

        isAnimationLocked = false
        _collisionEvent.value = null

        if (_gameOver.value != true) {
            nextTurn()
        }
    }

    private fun nextTurn() {
        val currentList = _players.value ?: return
        var nextIndex = (turnIndex + 1) % currentList.size

        while (currentList[nextIndex].isFinished) {
            nextIndex = (nextIndex + 1) % currentList.size
            if (currentList.all { it.isFinished }) break
        }

        turnIndex = nextIndex
        _activePlayerId.value = currentList[turnIndex].id
    }
}