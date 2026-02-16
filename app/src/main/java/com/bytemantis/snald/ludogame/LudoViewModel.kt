package com.bytemantis.snald.ludogame

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LudoViewModel : ViewModel() {

    enum class State { WAITING_FOR_ROLL, WAITING_FOR_MOVE, ANIMATING, GAME_OVER }

    // --- Data to send to View ---
    data class TurnUpdate(
        val playerIdx: Int,
        val tokenIdx: Int,
        val visualSteps: Int,
        val isSpawn: Boolean,
        val soundToPlay: SoundType,
        val killInfo: KillInfo?
    )

    enum class SoundType { NONE, SAFE, KILL, WIN }
    data class KillInfo(val victimPlayerIdx: Int, val victimTokenIdx: Int, val fromPos: Int)

    // --- LiveData ---
    private val _gameState = MutableLiveData(State.WAITING_FOR_ROLL)
    val gameState: LiveData<State> = _gameState

    private val _players = MutableLiveData<List<LudoPlayer>>()
    val players: LiveData<List<LudoPlayer>> = _players

    private val _activePlayerIndex = MutableLiveData(0)
    val activePlayerIndex: LiveData<Int> = _activePlayerIndex

    private val _diceValue = MutableLiveData<Int>()
    val diceValue: LiveData<Int> = _diceValue

    private val _statusMessage = MutableLiveData("Welcome Back!")
    val statusMessage: LiveData<String> = _statusMessage

    // SINGLE Event Channel for Moves (Fixes Crash)
    private val _turnUpdate = MutableLiveData<TurnUpdate?>()
    val turnUpdate: LiveData<TurnUpdate?> = _turnUpdate

    private val _victoryAnnouncement = MutableLiveData<String?>()
    val victoryAnnouncement: LiveData<String?> = _victoryAnnouncement

    private val ruleEngine = LudoRuleEngine()
    private var rankCounter = 0

    // Track if the current turn should continue (Roll 6, Kill, Win)
    private var shouldContinueTurn = false

    init {
        if (LudoGameStateHolder.hasActiveGame) restoreGame() else initNewGame()
    }

    private fun initNewGame() {
        _players.value = listOf(
            LudoPlayer(1, "RED"),
            LudoPlayer(2, "GREEN"),
            LudoPlayer(3, "BLUE"),
            LudoPlayer(4, "YELLOW")
        )
        _statusMessage.value = "Red's Turn!"
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

    fun quitGame() { LudoGameStateHolder.clear() }

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

        // Reset turn continuation flag
        shouldContinueTurn = false

        val roll = (1..6).random()
        _diceValue.value = roll

        // Mark flag if rolled a 6
        if (roll == 6) shouldContinueTurn = true

        viewModelScope.launch {
            delay(500)
            val activeIdx = _activePlayerIndex.value!!
            val player = _players.value!![activeIdx]

            // Auto-move logic
            val validIndices = getValidTokenIndices(activeIdx, player, roll, _players.value!!)

            if (validIndices.isNotEmpty()) {
                if (validIndices.size == 1) {
                    _statusMessage.value = "Auto Moving..."
                    _gameState.value = State.WAITING_FOR_MOVE
                    delay(300)
                    onTokenClicked(validIndices[0])
                } else {
                    _statusMessage.value = "Select token."
                    _gameState.value = State.WAITING_FOR_MOVE
                }
            } else {
                _statusMessage.value = "No moves!"
                delay(1000)
                // If no moves, we lose the turn (even if we rolled 6)
                shouldContinueTurn = false
                endTurnLogic()
            }
        }
    }

    private fun getValidTokenIndices(pIdx: Int, p: LudoPlayer, roll: Int, all: List<LudoPlayer>): List<Int> {
        val list = mutableListOf<Int>()
        for (i in 0 until 4) {
            val res = ruleEngine.calculateMove(pIdx, i, p.tokenPositions[i], roll, all)
            if (res !is LudoRuleEngine.MoveResult.Invalid) list.add(i)
        }
        return list
    }

    fun onTokenClicked(tokenIndex: Int) {
        if (_gameState.value != State.WAITING_FOR_MOVE) return

        val pIdx = _activePlayerIndex.value!!
        val player = _players.value!![pIdx]
        val roll = _diceValue.value!!
        val currentPos = player.tokenPositions[tokenIndex]

        val result = ruleEngine.calculateMove(pIdx, tokenIndex, currentPos, roll, _players.value!!)
        if (result is LudoRuleEngine.MoveResult.Invalid) return

        _gameState.value = State.ANIMATING

        // Update Extra Turn Logic based on Result
        if (result.givesExtraTurn) shouldContinueTurn = true

        val isSpawn = currentPos == -1
        val visualSteps = if (isSpawn) 0 else roll

        var soundToPlay = SoundType.NONE
        var killInfo: KillInfo? = null

        // APPLY STATE CHANGES
        when(result) {
            is LudoRuleEngine.MoveResult.MoveOnly -> {
                player.tokenPositions[tokenIndex] = result.newPosIndex
                // If spawn, we might want a special sound, handled by visualSteps=0 check in Activity usually
            }
            is LudoRuleEngine.MoveResult.SafeZoneLanded -> {
                player.tokenPositions[tokenIndex] = result.newPosIndex
                soundToPlay = SoundType.SAFE
            }
            is LudoRuleEngine.MoveResult.SafeStack -> {
                player.tokenPositions[tokenIndex] = result.newPosIndex
                soundToPlay = SoundType.SAFE
            }
            is LudoRuleEngine.MoveResult.Kill -> {
                player.tokenPositions[tokenIndex] = result.newPosIndex
                val victimP = _players.value!![result.victimPlayerIdx]
                val victimPos = victimP.tokenPositions[result.victimTokenIdx]

                // Kill logic: Reset victim to -1
                victimP.tokenPositions[result.victimTokenIdx] = -1

                killInfo = KillInfo(result.victimPlayerIdx, result.victimTokenIdx, victimPos)
                soundToPlay = SoundType.KILL
            }
            is LudoRuleEngine.MoveResult.Win -> {
                player.tokenPositions[tokenIndex] = 57
                soundToPlay = SoundType.WIN
                if (player.getFinishedCount() == 4) {
                    rankCounter++
                    _victoryAnnouncement.value = "${player.colorName} FINISHED!"
                }
            }
            else -> {}
        }

        // Send ONE update packet
        _turnUpdate.value = TurnUpdate(
            playerIdx = pIdx,
            tokenIdx = tokenIndex,
            visualSteps = visualSteps,
            isSpawn = isSpawn,
            soundToPlay = soundToPlay,
            killInfo = killInfo
        )

        saveCurrentState()
    }

    // Called by Activity after ALL animations (hop + kill + sound) are done
    fun onTurnAnimationsFinished() {
        _turnUpdate.value = null // Clear event
        endTurnLogic()
    }

    private fun endTurnLogic() {
        // Victory Check
        val currentPlayer = _players.value!![_activePlayerIndex.value!!]
        if (currentPlayer.getFinishedCount() == 4) {
            // Even if finished, let's see if we pass turn.
            // Actually if finished, we skip them next time.
            passTurn()
            return
        }

        if (shouldContinueTurn) {
            _statusMessage.value = "Extra Turn! Roll again."
            _gameState.value = State.WAITING_FOR_ROLL
        } else {
            passTurn()
        }
        saveCurrentState()
    }

    private fun passTurn() {
        val currentPlayers = _players.value!!
        val activeCount = currentPlayers.count { it.getFinishedCount() < 4 }
        if (activeCount < 2) {
            _statusMessage.value = "GAME OVER"
            _gameState.value = State.GAME_OVER
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
    }
}