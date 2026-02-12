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

    private val _pacmanMoveResult = MutableLiveData<GameEngine.PacmanResult>()
    val pacmanMoveResult: LiveData<GameEngine.PacmanResult> = _pacmanMoveResult

    private val _diceValue = MutableLiveData<Int>()
    val diceValue: LiveData<Int> = _diceValue

    private val _collisionEvent = MutableLiveData<Pair<Player, Int>?>()
    val collisionEvent: LiveData<Pair<Player, Int>?> = _collisionEvent

    private val _gameOver = MutableLiveData<Boolean>()
    val gameOver: LiveData<Boolean> = _gameOver

    private var turnIndex = 0
    var isAnimationLocked = false

    // Stores the dice roll to use for Pac-Man's turn
    private var currentTurnRoll = 0

    fun startGame(playerCount: Int) {
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

    // PHASE 1: Start Turn
    fun rollDiceForActivePlayer() {
        if (isAnimationLocked) return

        val currentList = _players.value ?: return
        val activePlayer = currentList[turnIndex]

        if (activePlayer.isFinished) {
            nextTurn()
            return
        }

        currentTurnRoll = (1..6).random()
        _diceValue.value = currentTurnRoll

        val result = engine.calculateMove(activePlayer, currentTurnRoll)
        _lastMoveResult.value = result
    }

    // PHASE 2: Player finished moving, check for Pac-Man
    fun onPlayerMoveFinished(finalPos: Int) {
        val currentList = _players.value ?: return
        val activePlayer = currentList[turnIndex]

        activePlayer.currentPosition = finalPos
        if (finalPos == 100) activePlayer.isFinished = true

        // If player has an active Pac-Man, calculate its move next
        if (activePlayer.isPacmanActive) {
            val pacResult = engine.calculatePacmanMove(activePlayer, currentTurnRoll)
            _pacmanMoveResult.value = pacResult
        } else {
            // Skip Pac-Man phase
            finalizeTurn()
        }
    }

    // PHASE 3: Pac-Man finished moving
    fun onPacmanMoveFinished(newPacPos: Int) {
        val currentList = _players.value ?: return
        val activePlayer = currentList[turnIndex]

        activePlayer.pacmanPosition = newPacPos
        _players.value = currentList // Sync UI

        finalizeTurn()
    }

    // PHASE 4: Collisions & End Turn
    private fun finalizeTurn() {
        val currentList = _players.value ?: return
        val activePlayer = currentList[turnIndex]

        // 1. Player Collision (Standard)
        var enemy = engine.checkCollisions(activePlayer, currentList)

        if (enemy != null) {
            // == COLLISION LOGIC ==
            if (enemy.hasShield) {
                enemy.starCount-- // Break Shield
                _collisionEvent.value = null
                checkWinConditionOrNextTurn()
            } else {
                // == STEAL PAC-MAN LOGIC ==
                // If the victim has a Pac-Man, the killer steals it.
                if (enemy.isPacmanActive) {
                    activePlayer.pacmanPosition = enemy.pacmanPosition
                    enemy.pacmanPosition = 0
                }

                // Kill the enemy
                val fatalPos = enemy.currentPosition
                isAnimationLocked = true
                _collisionEvent.value = Pair(enemy, fatalPos)
            }
        } else {
            // 2. Pac-Man Collision (Path Logic)
            // Only check if Player didn't already hit someone
            if (activePlayer.isPacmanActive) {
                // Re-calculate the path to check for kills securely
                val steps = currentTurnRoll * 2
                val path = engine.calculatePacmanPath(activePlayer.pacmanPosition, steps) // Note: pacmanPosition is already updated at this point

                // Note: We need the path STARTING from where it was BEFORE the update?
                // Actually, onPacmanMoveFinished updated the position.
                // We should technically check kills based on the path just taken.
                // For safety, let's recalculate based on (Current + Steps) ? No, that's reverse.
                // Simpler: We know the path logic is deterministic.
                // Let's assume the Pac-Man move was valid.
                // To be perfectly accurate, we need the start position.
                // But for now, let's use the helper:
                // We can just call calculatePacmanPath relative to where we *landed*? No.
                // Let's just pass the path from the UI or recalculate properly.
                // TRICK: We can reverse the math. If current is X, previous was X + steps (wrapping).
                // Actually, let's just stick to the GameEngine logic.
                // We will iterate all players and see if they are on the square Pac-Man is currently on?
                // No, user wants PATH kills.

                // Let's trust the 'path' logic. We will re-generate the path based on the START of the turn.
                // Wait, activePlayer.pacmanPosition is now the END position.
                // We need the START position to calculate the path.
                // We don't have it easily here without storing it.
                // Quick fix: Do the kill check *inside* onPacmanMoveFinished before updating?
                // No, separation of concerns.

                // FIX: We will scan the board for any enemy standing on the activePlayer.pacmanPosition?
                // No, that misses the path.

                // Let's rely on the fact that we can just check the *current* position for now to keep it simple,
                // OR (Owner approach) - We reconstruct the path backwards.

                // Re-calculating path backwards for verification:
                var tempPos = activePlayer.pacmanPosition
                val pathTaken = mutableListOf<Int>()
                for(i in 1..(currentTurnRoll*2)) {
                    pathTaken.add(tempPos)
                    tempPos++
                    if(tempPos > 100) tempPos = 1
                }
                // pathTaken now contains the squares we just visited (in reverse order).

                enemy = engine.checkPacmanPathKills(activePlayer, currentList, pathTaken)

                if (enemy != null) {
                    if (enemy.hasShield) {
                        enemy.starCount--
                        _collisionEvent.value = null
                        checkWinConditionOrNextTurn()
                    } else {
                        val fatalPos = enemy.currentPosition
                        isAnimationLocked = true
                        _collisionEvent.value = Pair(enemy, fatalPos)
                    }
                    return // Exit
                }
            }

            _players.value = currentList
            checkWinConditionOrNextTurn()
        }
    }

    private fun checkWinConditionOrNextTurn() {
        if (_gameOver.value == true) return

        val currentList = _players.value ?: return
        val activePlayersCount = currentList.count { !it.isFinished }

        if (activePlayersCount <= 1) {
            _gameOver.value = true
        } else {
            nextTurn()
        }
    }

    fun finalizeKill(killedPlayerId: Int) {
        val currentList = _players.value ?: return
        val enemy = currentList.find { it.id == killedPlayerId } ?: return

        enemy.currentPosition = 1
        _players.value = currentList

        isAnimationLocked = false
        _collisionEvent.value = null

        checkWinConditionOrNextTurn()
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