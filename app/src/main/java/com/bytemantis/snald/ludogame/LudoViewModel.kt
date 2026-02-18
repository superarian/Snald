package com.bytemantis.snald.ludogame

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LudoViewModel : ViewModel() {

    enum class State { SETUP_PLAYERS, SETUP_TOKENS, WAITING_FOR_ROLL, WAITING_FOR_MOVE, ANIMATING, GAME_OVER }
    enum class AnnouncementType { TOKEN_GOAL, PLAYER_VICTORY }
    data class Announcement(val message: String, val type: AnnouncementType)

    data class TurnUpdate(val playerIdx: Int, val tokenIdx: Int, val visualSteps: Int, val isSpawn: Boolean, val soundToPlay: SoundType, val killInfo: KillInfo?)
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

    // OWNER FIX: Using a Nullable LiveData that we clear after every show
    private val _announcement = MutableLiveData<Announcement?>()
    val announcement: LiveData<Announcement?> = _announcement

    private val ruleEngine = LudoRuleEngine()
    private var rankCounter = 0
    private var tempPlayerCount = 2
    private var currentTokenCount = 1
    private var shouldGiveExtraTurn = false
    private val finishedPlayerIds = mutableSetOf<Int>()

    init {
        if (LudoGameStateHolder.hasActiveGame) restoreGame()
        else _gameState.value = State.SETUP_PLAYERS
    }

    fun selectPlayerCount(count: Int) {
        tempPlayerCount = count
        _gameState.value = State.SETUP_TOKENS
    }

    fun startGame(tokenCount: Int) {
        currentTokenCount = tokenCount
        val colors = listOf("RED", "GREEN", "BLUE", "YELLOW")
        val newPlayers = (0 until tempPlayerCount).map { LudoPlayer(it + 1, colors[it], currentTokenCount) }
        _players.value = newPlayers
        _activePlayerIndex.value = 0
        finishedPlayerIds.clear()
        _gameState.value = State.WAITING_FOR_ROLL
        _statusMessage.value = "${newPlayers[0].colorName}'s Turn"
        saveCurrentState()
    }

    private fun restoreGame() {
        _players.value = LudoGameStateHolder.players
        _activePlayerIndex.value = LudoGameStateHolder.activePlayerIndex
        _diceValue.value = LudoGameStateHolder.diceValue
        _gameState.value = LudoGameStateHolder.gameState
        _statusMessage.value = LudoGameStateHolder.statusMessage
        rankCounter = LudoGameStateHolder.rankCounter
        finishedPlayerIds.clear()
        finishedPlayerIds.addAll(LudoGameStateHolder.finishedPlayerIds)
        currentTokenCount = _players.value?.firstOrNull()?.tokenCount ?: 4
    }

    fun saveCurrentState() {
        val p = _players.value ?: return
        LudoGameStateHolder.saveState(p, _activePlayerIndex.value ?: 0, _diceValue.value ?: 0, _gameState.value ?: State.WAITING_FOR_ROLL, _statusMessage.value ?: "", rankCounter, finishedPlayerIds)
    }

    fun rollDice() {
        if (_gameState.value != State.WAITING_FOR_ROLL) return
        _gameState.value = State.ANIMATING
        shouldGiveExtraTurn = false

        val roll = (1..6).random()
        _diceValue.value = roll

        // Bonus for rolling a 6
        if (roll == 6) shouldGiveExtraTurn = true

        viewModelScope.launch {
            delay(600)
            val pIdx = _activePlayerIndex.value!!
            val p = _players.value!![pIdx]

            val valid = (0 until currentTokenCount).filter {
                ruleEngine.calculateMove(pIdx, it, p.tokenPositions[it], roll, _players.value!!) !is LudoRuleEngine.MoveResult.Invalid
            }

            if (valid.isNotEmpty()) {
                if (valid.size == 1) onTokenClicked(valid[0])
                else {
                    _gameState.value = State.WAITING_FOR_MOVE
                    _statusMessage.value = "Select Token"
                }
            } else {
                shouldGiveExtraTurn = false // Lose bonus if no moves possible
                delay(800)
                passTurn()
            }
        }
    }

    fun onTokenClicked(tIdx: Int) {
        if (_gameState.value != State.WAITING_FOR_MOVE && _gameState.value != State.ANIMATING) return
        val pIdx = _activePlayerIndex.value!!
        val p = _players.value!![pIdx]
        val roll = _diceValue.value!!
        val res = ruleEngine.calculateMove(pIdx, tIdx, p.tokenPositions[tIdx], roll, _players.value!!)

        if (res is LudoRuleEngine.MoveResult.Invalid) return
        _gameState.value = State.ANIMATING

        // Give extra turn for Kills or Wins (Home)
        if (res.givesExtraTurn) shouldGiveExtraTurn = true

        val isSpawn = p.tokenPositions[tIdx] == -1
        var sound = SoundType.NONE
        var kill: KillInfo? = null

        when(res) {
            is LudoRuleEngine.MoveResult.MoveOnly -> p.tokenPositions[tIdx] = res.newPosIndex
            is LudoRuleEngine.MoveResult.SafeZoneLanded -> { p.tokenPositions[tIdx] = res.newPosIndex; sound = SoundType.SAFE }
            is LudoRuleEngine.MoveResult.SafeStack -> { p.tokenPositions[tIdx] = res.newPosIndex; sound = SoundType.SAFE }
            is LudoRuleEngine.MoveResult.Kill -> {
                p.tokenPositions[tIdx] = res.newPosIndex
                _players.value!![res.victimPlayerIdx].tokenPositions[res.victimTokenIdx] = -1
                kill = KillInfo(res.victimPlayerIdx, res.victimTokenIdx, -1)
                sound = SoundType.KILL
            }
            is LudoRuleEngine.MoveResult.Win -> {
                p.tokenPositions[tIdx] = 57
                sound = SoundType.WIN
                if (p.getFinishedCount() == currentTokenCount) {
                    rankCounter++
                    finishedPlayerIds.add(p.id)
                    _announcement.value = Announcement("${p.colorName} TAKES SPOT #${rankCounter}!", AnnouncementType.PLAYER_VICTORY)
                } else {
                    _announcement.value = Announcement("${p.colorName} TOKEN HOME!", AnnouncementType.TOKEN_GOAL)
                }
            }
            else -> {}
        }
        _turnUpdate.value = TurnUpdate(pIdx, tIdx, if (isSpawn) 0 else roll, isSpawn, sound, kill)
    }

    fun clearAnnouncement() { _announcement.value = null }

    fun onTurnAnimationsFinished() {
        _turnUpdate.value = null
        val p = _players.value!![_activePlayerIndex.value!!]

        // If player just finished their last token, they don't get an extra turn, turn passes
        if (finishedPlayerIds.contains(p.id)) {
            passTurn()
        } else if (shouldGiveExtraTurn) {
            _gameState.value = State.WAITING_FOR_ROLL
            _statusMessage.value = "Extra Turn!"
        } else {
            passTurn()
        }
        saveCurrentState()
    }

    private fun passTurn() {
        val all = _players.value!!
        val activeCount = all.size - finishedPlayerIds.size

        if (activeCount <= 1) {
            _gameState.value = State.GAME_OVER
            _statusMessage.value = "GAME OVER"
            return
        }

        var next = _activePlayerIndex.value!!
        var safety = 0
        do {
            next = (next + 1) % all.size
            safety++
        } while (finishedPlayerIds.contains(all[next].id) && safety < 10)

        _activePlayerIndex.value = next
        _gameState.value = State.WAITING_FOR_ROLL
        _statusMessage.value = "${all[next].colorName}'s Turn"
        saveCurrentState()
    }

    fun quitGame() { LudoGameStateHolder.clear() }
}