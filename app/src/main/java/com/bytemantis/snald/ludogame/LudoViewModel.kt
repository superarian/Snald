package com.bytemantis.snald.ludogame

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LudoViewModel : ViewModel() {

    enum class State { WAITING_FOR_ROLL, WAITING_FOR_MOVE, ANIMATING, GAME_OVER }

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

    private val _victoryAnnouncement = MutableLiveData<String?>()
    val victoryAnnouncement: LiveData<String?> = _victoryAnnouncement

    // Rank counter to persist victory order
    private var rankCounter = 0

    private val _moveEvent = MutableLiveData<Triple<Int, Int, Int>?>()
    val moveEvent: LiveData<Triple<Int, Int, Int>?> = _moveEvent

    private val _killEvent = MutableLiveData<Triple<Int, Int, Int>?>()
    val killEvent: LiveData<Triple<Int, Int, Int>?> = _killEvent

    // Event: Spawn (Play Aura Sound)
    private val _spawnEvent = MutableLiveData<Boolean>()
    val spawnEvent: LiveData<Boolean> = _spawnEvent

    // Event: Safe Zone (Play Safe Sound)
    private val _safeZoneEvent = MutableLiveData<Boolean>()
    val safeZoneEvent: LiveData<Boolean> = _safeZoneEvent

    private val ruleEngine = LudoRuleEngine()

    init {
        // OWNER FIX: Persistence Check
        if (LudoGameStateHolder.hasActiveGame) {
            restoreGame()
        } else {
            initNewGame()
        }
    }

    private fun initNewGame() {
        // OWNER FIX: Changed Order to Red -> Green -> Blue -> Yellow
        _players.value = listOf(
            LudoPlayer(1, "RED"),
            LudoPlayer(2, "GREEN"),
            LudoPlayer(3, "BLUE"),   // P3 is now BLUE (Right Side)
            LudoPlayer(4, "YELLOW")  // P4 is now YELLOW (Bottom Side)
        )
        _statusMessage.value = "Red's Turn! Roll the dice."
        rankCounter = 0
    }

    private fun restoreGame() {
        _players.value = LudoGameStateHolder.players
        _activePlayerIndex.value = LudoGameStateHolder.activePlayerIndex
        _diceValue.value = LudoGameStateHolder.diceValue
        _gameState.value = LudoGameStateHolder.gameState
        _statusMessage.value = LudoGameStateHolder.statusMessage
        rankCounter = LudoGameStateHolder.rankCounter
    }

    fun quitGame() {
        LudoGameStateHolder.clear()
    }

    fun saveCurrentState() {
        LudoGameStateHolder.saveState(
            _players.value ?: emptyList(),
            _activePlayerIndex.value ?: 0,
            _diceValue.value ?: 0,
            _gameState.value ?: State.WAITING_FOR_ROLL,
            _statusMessage.value ?: "",
            rankCounter
        )
    }

    fun rollDice() {
        if (_gameState.value != State.WAITING_FOR_ROLL) return

        _gameState.value = State.ANIMATING
        val roll = (1..6).random()
        _diceValue.value = roll

        viewModelScope.launch {
            delay(500)

            val activeIdx = _activePlayerIndex.value!!
            val currentPlayers = _players.value!!
            val activePlayer = currentPlayers[activeIdx]

            // Get valid moves to check for auto-move
            val validTokenIndices = getValidTokenIndices(activeIdx, activePlayer, roll, currentPlayers)

            if (validTokenIndices.isNotEmpty()) {
                // OWNER FIX: Auto-move if only 1 option
                if (validTokenIndices.size == 1) {
                    _statusMessage.value = "Auto Moving..."
                    _gameState.value = State.WAITING_FOR_MOVE
                    delay(300) // Negligible pause
                    onTokenClicked(validTokenIndices[0])
                } else {
                    _statusMessage.value = "Select a token to move."
                    _gameState.value = State.WAITING_FOR_MOVE
                }
            } else {
                _statusMessage.value = "No moves possible!"
                delay(1000)
                nextTurn()
            }
        }
    }

    private fun getValidTokenIndices(
        playerIdx: Int,
        player: LudoPlayer,
        roll: Int,
        allPlayers: List<LudoPlayer>
    ): List<Int> {
        val validIndices = mutableListOf<Int>()
        for (i in 0 until 4) {
            val currentPos = player.tokenPositions[i]
            if (currentPos == 57) continue
            val result = ruleEngine.calculateMove(playerIdx, i, currentPos, roll, allPlayers)
            if (result !is LudoRuleEngine.MoveResult.Invalid) {
                validIndices.add(i)
            }
        }
        return validIndices
    }

    fun onTokenClicked(tokenIndex: Int) {
        if (_gameState.value != State.WAITING_FOR_MOVE) return

        val playerIndex = _activePlayerIndex.value!!
        val player = _players.value!![playerIndex]
        val roll = _diceValue.value!!
        val currentPos = player.tokenPositions[tokenIndex]

        val result = ruleEngine.calculateMove(playerIndex, tokenIndex, currentPos, roll, _players.value!!)

        if (result is LudoRuleEngine.MoveResult.Invalid) return

        _gameState.value = State.ANIMATING

        // OWNER FIX: Spawn Glitch Logic
        // If spawning (-1 to 0), visualSteps is 0 so AnimationManager stops immediately after the spawn hop.
        val isSpawn = currentPos == -1
        val visualSteps = if (isSpawn) 0 else roll

        if (isSpawn) {
            _spawnEvent.value = true
        }

        when(result) {
            is LudoRuleEngine.MoveResult.MoveOnly -> {
                player.tokenPositions[tokenIndex] = result.newPosIndex
            }
            is LudoRuleEngine.MoveResult.SafeZoneLanded -> {
                // OWNER FIX: Safe Zone Logic
                player.tokenPositions[tokenIndex] = result.newPosIndex
                _safeZoneEvent.value = true
            }
            is LudoRuleEngine.MoveResult.SafeStack -> {
                player.tokenPositions[tokenIndex] = result.newPosIndex
                _statusMessage.value = "Safe!"
                _safeZoneEvent.value = true
            }
            is LudoRuleEngine.MoveResult.Kill -> {
                player.tokenPositions[tokenIndex] = result.newPosIndex
                val victimPlayer = _players.value!![result.victimPlayerIdx]
                val victimCurrentPos = victimPlayer.tokenPositions[result.victimTokenIdx]
                victimPlayer.tokenPositions[result.victimTokenIdx] = -1
                _killEvent.value = Triple(result.victimPlayerIdx, result.victimTokenIdx, victimCurrentPos)
            }
            is LudoRuleEngine.MoveResult.Win -> {
                player.tokenPositions[tokenIndex] = 57
                if (player.getFinishedCount() == 4) {
                    rankCounter++
                    val suffix = when(rankCounter) { 1->"1ST"; 2->"2ND"; 3->"3RD"; else->"${rankCounter}TH" }
                    _victoryAnnouncement.value = "${player.colorName} TAKES $suffix SPOT!"
                } else {
                    _statusMessage.value = "One token home!"
                }
            }
            else -> {}
        }

        _moveEvent.value = Triple(playerIndex, tokenIndex, visualSteps)
        saveCurrentState()
    }

    fun onAnimationFinished() {
        _moveEvent.value = null
        _killEvent.value = null
        _spawnEvent.value = false
        _safeZoneEvent.value = false // Reset
        _victoryAnnouncement.value = null

        val currentPlayer = _players.value!![_activePlayerIndex.value!!]
        if (currentPlayer.getFinishedCount() == 4) {
            nextTurn()
            return
        }

        val roll = _diceValue.value!!
        if (roll == 6) {
            _statusMessage.value = "Rolled a 6! Roll again."
            _gameState.value = State.WAITING_FOR_ROLL
        } else {
            nextTurn()
        }
        saveCurrentState()
    }

    private fun nextTurn() {
        val currentPlayers = _players.value!!
        val activeCount = currentPlayers.count { it.getFinishedCount() < 4 }

        if (activeCount < 2) {
            _statusMessage.value = "GAME OVER!"
            _gameState.value = State.GAME_OVER
            LudoGameStateHolder.clear()
            return
        }

        var nextIndex = _activePlayerIndex.value!!
        for(i in 0 until 4) {
            nextIndex = (nextIndex + 1) % 4
            if (currentPlayers[nextIndex].getFinishedCount() < 4) break
        }

        _activePlayerIndex.value = nextIndex
        _gameState.value = State.WAITING_FOR_ROLL
        val color = currentPlayers[nextIndex].colorName
        _statusMessage.value = "$color's Turn!"
        saveCurrentState()
    }
}