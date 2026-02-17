package com.bytemantis.snald.ludogame

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LudoViewModel : ViewModel() {

    // ADDED: SETUP state
    enum class State { SETUP, WAITING_FOR_ROLL, WAITING_FOR_MOVE, ANIMATING, GAME_OVER }

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

    private val _gameState = MutableLiveData<State>()
    val gameState: LiveData<State> = _gameState

    private val _players = MutableLiveData<List<LudoPlayer>>()
    val players: LiveData<List<LudoPlayer>> = _players

    private val _activePlayerIndex = MutableLiveData(0)
    val activePlayerIndex: LiveData<Int> = _activePlayerIndex

    private val _diceValue = MutableLiveData<Int>()
    val diceValue: LiveData<Int> = _diceValue

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _turnUpdate = MutableLiveData<TurnUpdate?>()
    val turnUpdate: LiveData<TurnUpdate?> = _turnUpdate

    private val _victoryAnnouncement = MutableLiveData<String?>()
    val victoryAnnouncement: LiveData<String?> = _victoryAnnouncement

    private val ruleEngine = LudoRuleEngine()
    private var rankCounter = 0
    private var shouldContinueTurn = false

    init {
        // MODIFIED: Handle Setup vs Restore
        if (LudoGameStateHolder.hasActiveGame) {
            restoreGame()
        } else {
            _gameState.value = State.SETUP
            _statusMessage.value = "Select Player Count"
        }
    }

    // NEW: Start game with dynamic player count
    fun startGame(count: Int) {
        val allPossible = listOf(
            LudoPlayer(1, "RED"),
            LudoPlayer(2, "GREEN"),
            LudoPlayer(3, "BLUE"),
            LudoPlayer(4, "YELLOW")
        )
        _players.value = allPossible.take(count)
        _activePlayerIndex.value = 0
        _statusMessage.value = "${_players.value!![0].colorName}'s Turn!"
        _gameState.value = State.WAITING_FOR_ROLL
        saveCurrentState()
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
        val count = _players.value?.size ?: 4
        LudoGameStateHolder.saveState(
            _players.value ?: emptyList(),
            _activePlayerIndex.value ?: 0,
            _diceValue.value ?: 0,
            _gameState.value ?: State.WAITING_FOR_ROLL,
            _statusMessage.value ?: "",
            rankCounter,
            count // Persisting the count
        )
    }

    fun rollDice() {
        if (_gameState.value != State.WAITING_FOR_ROLL) return
        _gameState.value = State.ANIMATING
        shouldContinueTurn = false

        val roll = (1..6).random()
        _diceValue.value = roll
        if (roll == 6) shouldContinueTurn = true

        viewModelScope.launch {
            delay(500)
            val activeIdx = _activePlayerIndex.value!!
            val player = _players.value!![activeIdx]
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
        if (result.givesExtraTurn) shouldContinueTurn = true

        val isSpawn = currentPos == -1
        val visualSteps = if (isSpawn) 0 else roll
        var soundToPlay = SoundType.NONE
        var killInfo: KillInfo? = null

        when(result) {
            is LudoRuleEngine.MoveResult.MoveOnly -> { player.tokenPositions[tokenIndex] = result.newPosIndex }
            is LudoRuleEngine.MoveResult.SafeZoneLanded -> { player.tokenPositions[tokenIndex] = result.newPosIndex; soundToPlay = SoundType.SAFE }
            is LudoRuleEngine.MoveResult.SafeStack -> { player.tokenPositions[tokenIndex] = result.newPosIndex; soundToPlay = SoundType.SAFE }
            is LudoRuleEngine.MoveResult.Kill -> {
                player.tokenPositions[tokenIndex] = result.newPosIndex
                val victimP = _players.value!![result.victimPlayerIdx]
                val victimPos = victimP.tokenPositions[result.victimTokenIdx]
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

        _turnUpdate.value = TurnUpdate(pIdx, tokenIndex, visualSteps, isSpawn, soundToPlay, killInfo)
        saveCurrentState()
    }

    fun onTurnAnimationsFinished() {
        _turnUpdate.value = null
        endTurnLogic()
    }

    private fun endTurnLogic() {
        val currentPlayer = _players.value!![_activePlayerIndex.value!!]
        if (currentPlayer.getFinishedCount() == 4) {
            passTurn()
            return
        }
        if (shouldContinueTurn) {
            _statusMessage.value = "Extra Turn!"
            _gameState.value = State.WAITING_FOR_ROLL
        } else {
            passTurn()
        }
        saveCurrentState()
    }

    // MODIFIED: Pass turn logic now uses the dynamic size of the player list
    private fun passTurn() {
        val currentPlayers = _players.value!!
        val totalInGame = currentPlayers.size
        val activeCount = currentPlayers.count { it.getFinishedCount() < 4 }

        if (activeCount < 2 && totalInGame > 1) {
            _statusMessage.value = "GAME OVER"
            _gameState.value = State.GAME_OVER
            return
        }

        var nextIndex = _activePlayerIndex.value!!
        for(i in 0 until totalInGame) {
            nextIndex = (nextIndex + 1) % totalInGame
            if (currentPlayers[nextIndex].getFinishedCount() < 4) break
        }

        _activePlayerIndex.value = nextIndex
        _gameState.value = State.WAITING_FOR_ROLL
        _statusMessage.value = "${currentPlayers[nextIndex].colorName}'s Turn!"
    }
}