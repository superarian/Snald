package com.bytemantis.snald.ui

import android.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytemantis.snald.logic.GameEngine
import com.bytemantis.snald.model.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    // --- NEW: Game State Management ---
    enum class GameState {
        SPLASH, MENU, PLAYING, PAUSED, GAME_OVER
    }

    private val _gameState = MutableLiveData<GameState>(GameState.SPLASH)
    val gameState: LiveData<GameState> = _gameState

    // --- Existing LiveData ---
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
    private var currentTurnRoll = 0

    init {
        // Auto-transition from Splash to Menu
        viewModelScope.launch {
            delay(3000) // 3 Seconds Splash
            if (_gameState.value == GameState.SPLASH) {
                _gameState.value = GameState.MENU
            }
        }
    }

    // --- NEW: Back Key Logic ---
    fun handleBackPress() {
        when (_gameState.value) {
            GameState.PLAYING -> _gameState.value = GameState.PAUSED
            GameState.PAUSED -> _gameState.value = GameState.PLAYING // Toggle back
            GameState.GAME_OVER -> _gameState.value = GameState.MENU
            else -> { /* Handled by Activity (exit app) */ }
        }
    }

    fun resumeGame() {
        if (_gameState.value == GameState.PAUSED) {
            _gameState.value = GameState.PLAYING
        }
    }

    fun quitToMenu() {
        _gameState.value = GameState.MENU
    }

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

        // Transition to PLAYING
        _gameState.value = GameState.PLAYING
    }

    // --- Existing Game Logic Below ---

    fun assignPacmanTo(newOwnerId: Int) {
        val currentList = _players.value ?: return
        currentList.forEach { it.pacmanPosition = 0 }
        val owner = currentList.find { it.id == newOwnerId }
        owner?.pacmanPosition = 100
        _players.value = currentList
    }

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

        if (result is GameEngine.MoveResult.PacmanSpawned) {
            assignPacmanTo(activePlayer.id)
        }

        _lastMoveResult.value = result
    }

    fun onPlayerMoveFinished(finalPos: Int) {
        val currentList = _players.value ?: return
        val activePlayer = currentList[turnIndex]

        activePlayer.currentPosition = finalPos

        if (finalPos == 100) {
            activePlayer.isFinished = true
            if (activePlayer.pacmanPosition > 0) {
                activePlayer.pacmanPosition = 0
                transferPacmanToLowest(currentList)
            }
        }

        if (activePlayer.isPacmanActive) {
            val pacResult = engine.calculatePacmanMove(activePlayer, currentTurnRoll)
            _pacmanMoveResult.value = pacResult
        } else {
            finalizeTurn()
        }
    }

    private fun transferPacmanToLowest(allPlayers: List<Player>) {
        val heir = allPlayers.filter { !it.isFinished }.minByOrNull { it.currentPosition }
        if (heir != null) {
            assignPacmanTo(heir.id)
        }
    }

    fun onPacmanMoveFinished(newPacPos: Int) {
        val currentList = _players.value ?: return
        val activePlayer = currentList[turnIndex]
        activePlayer.pacmanPosition = newPacPos
        _players.value = currentList
        finalizeTurn()
    }

    private fun finalizeTurn() {
        val currentList = _players.value ?: return
        val activePlayer = currentList[turnIndex]
        var enemy = engine.checkCollisions(activePlayer, currentList)

        if (enemy != null) {
            if (enemy.hasShield) {
                enemy.starCount--
                _collisionEvent.value = null
                checkWinConditionOrNextTurn()
            } else {
                if (enemy.isPacmanActive) {
                    assignPacmanTo(activePlayer.id)
                }
                val fatalPos = enemy.currentPosition
                isAnimationLocked = true
                _collisionEvent.value = Pair(enemy, fatalPos)
            }
        } else {
            checkWinConditionOrNextTurn()
        }
    }

    private fun checkWinConditionOrNextTurn() {
        if (_gameOver.value == true) return
        val currentList = _players.value ?: return
        val activePlayersCount = currentList.count { !it.isFinished }
        if (activePlayersCount <= 1) {
            _gameOver.value = true
            _gameState.value = GameState.GAME_OVER // State Update
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