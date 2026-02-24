package com.bytemantis.snald.ludogame

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LudoViewModel : ViewModel() {

    // ADDED SETUP_BOTS
    enum class State { SETUP_THEME, SETUP_PLAYERS, SETUP_BOTS, SETUP_TOKENS, WAITING_FOR_ROLL, WAITING_FOR_MOVE, ANIMATING, GAME_OVER }
    enum class AnnouncementType { TOKEN_GOAL, PLAYER_VICTORY }
    data class Announcement(val message: String, val type: AnnouncementType, val playerId: Int = -1)

    data class TurnUpdate(val playerIdx: Int, val tokenIdx: Int, val visualSteps: Int, val isSpawn: Boolean, val soundToPlay: SoundType, val killInfo: KillInfo?)
    enum class SoundType { NONE, SAFE, KILL, WIN, STAR_COLLECT, SHIELD_BREAK }
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

    private val _announcement = MutableLiveData<Announcement?>()
    val announcement: LiveData<Announcement?> = _announcement

    private val _timerSeconds = MutableLiveData<Int>(30)
    val timerSeconds: LiveData<Int> = _timerSeconds

    private val _dynamicSafeZone = MutableLiveData<Pair<Int, Int>?>(null)
    val dynamicSafeZone: LiveData<Pair<Int, Int>?> = _dynamicSafeZone

    private val _statsUpdate = MutableLiveData<Unit>()
    val statsUpdate: LiveData<Unit> = _statsUpdate

    private val ruleEngine = LudoRuleEngine()
    private val botEngine = LudoBotEngine(ruleEngine) // INSTANTIATE BOT ENGINE

    var tempPlayerCount = 2
        private set
    private var tempBotCount = 0
    private var currentTokenCount = 1
    private var rankCounter = 0
    private var shouldGiveExtraTurn = false
    private val finishedPlayerIds = mutableSetOf<Int>()
    private var isGameAbandoned = false
    private var timerJob: Job? = null
    private var pendingAnimationEndAction: (() -> Unit)? = null

    init {
        if (LudoGameStateHolder.hasActiveGame) restoreGame()
        else _gameState.value = State.SETUP_THEME
        startTimerLoop()
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val state = _gameState.value
                if (state == State.WAITING_FOR_ROLL || state == State.WAITING_FOR_MOVE || state == State.ANIMATING) {
                    val current = _timerSeconds.value ?: 30
                    if (current <= 1) {
                        spawnDynamicSafeZone()
                        _timerSeconds.value = 30
                    } else {
                        _timerSeconds.value = current - 1
                    }
                }
            }
        }
    }

    private fun spawnDynamicSafeZone() {
        val outerPathCoords = (LudoBoardConfig.PATH_RED.take(51) + LudoBoardConfig.PATH_GREEN.take(51) + LudoBoardConfig.PATH_BLUE.take(51) + LudoBoardConfig.PATH_YELLOW.take(51)).toSet()
        val available = outerPathCoords - LudoBoardConfig.SAFE_ZONES
        if (available.isNotEmpty()) {
            _dynamicSafeZone.value = available.random()
        }
    }

    // Updated Navigation
    fun navigateBackInSetup(): Boolean {
        return when (_gameState.value) {
            State.SETUP_TOKENS -> { _gameState.value = State.SETUP_BOTS; true }
            State.SETUP_BOTS -> { _gameState.value = State.SETUP_PLAYERS; true }
            State.SETUP_PLAYERS -> { _gameState.value = State.SETUP_THEME; true }
            State.SETUP_THEME -> false
            else -> false
        }
    }

    fun selectTheme() { _gameState.value = State.SETUP_PLAYERS }
    fun selectPlayerCount(count: Int) { tempPlayerCount = count; _gameState.value = State.SETUP_BOTS }
    fun selectBotCount(count: Int) { tempBotCount = count; _gameState.value = State.SETUP_TOKENS }

    fun startGame(tokenCount: Int) {
        currentTokenCount = tokenCount
        val colors = listOf("RED", "GREEN", "BLUE", "YELLOW")
        val humanCount = tempPlayerCount - tempBotCount

        // Brand the bots with a robot emoji
        val newPlayers = (0 until tempPlayerCount).map { i ->
            val isBot = i >= humanCount
            val prefix = if (isBot) "🤖 " else ""
            LudoPlayer(i + 1, "$prefix${colors[i]}", currentTokenCount, isBot = isBot)
        }

        _players.value = newPlayers
        _activePlayerIndex.value = 0
        finishedPlayerIds.clear()
        rankCounter = 0
        isGameAbandoned = false
        _timerSeconds.value = 30
        _dynamicSafeZone.value = null

        _gameState.value = State.WAITING_FOR_ROLL
        _statusMessage.value = "${newPlayers[0].colorName}'s Turn"
        saveCurrentState()
        triggerBotIfActive() // CHECK IF BOT GOES FIRST
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
        isGameAbandoned = false
        _timerSeconds.value = LudoGameStateHolder.timerSeconds
        _dynamicSafeZone.value = LudoGameStateHolder.dynamicSafeZone
        triggerBotIfActive()
    }

    fun saveCurrentState() {
        if (isGameAbandoned) return
        val p = _players.value ?: return
        LudoGameStateHolder.saveState(p, _activePlayerIndex.value ?: 0, _diceValue.value ?: 0, _gameState.value ?: State.WAITING_FOR_ROLL, _statusMessage.value ?: "", rankCounter, finishedPlayerIds, _timerSeconds.value ?: 30, _dynamicSafeZone.value)
    }

    // The Master Bot Trigger
    private fun triggerBotIfActive() {
        val p = _players.value?.get(_activePlayerIndex.value ?: 0) ?: return
        if (p.isBot && _gameState.value == State.WAITING_FOR_ROLL) {
            viewModelScope.launch {
                delay(1200) // "Thinking" delay
                rollDice()
            }
        }
    }

    fun rollDice() {
        if (_gameState.value != State.WAITING_FOR_ROLL) return
        _gameState.value = State.ANIMATING
        shouldGiveExtraTurn = false

        val roll = (1..6).random()
        _diceValue.value = roll

        val pIdx = _activePlayerIndex.value!!
        val pList = _players.value!!
        val p = pList[pIdx]

        if (roll == 6) {
            p.sixesRolled++
            shouldGiveExtraTurn = true
            _statsUpdate.value = Unit
        }

        viewModelScope.launch {
            delay(600)
            val valid = (0 until currentTokenCount).filter {
                ruleEngine.calculateMove(pIdx, it, p.tokenPositions.get(it), roll, _players.value!!, _dynamicSafeZone.value) !is LudoRuleEngine.MoveResult.Invalid
            }

            if (valid.isNotEmpty()) {
                if (valid.size == 1) {
                    onTokenClicked(valid.get(0))
                } else {
                    if (p.isBot) {
                        val bestMove = botEngine.getBestMove(pIdx, roll, _players.value!!, _dynamicSafeZone.value)
                        delay(800) // Delay to simulate "deciding" which token to move
                        if (bestMove != null) onTokenClicked(bestMove)
                    } else {
                        _gameState.value = State.WAITING_FOR_MOVE
                        _statusMessage.value = "Select Token"
                    }
                }
            } else {
                shouldGiveExtraTurn = false
                delay(800)
                passTurn()
            }
        }
    }

    fun onTokenClicked(tIdx: Int) {
        if (_gameState.value != State.WAITING_FOR_MOVE && _gameState.value != State.ANIMATING) return
        val pIdx = _activePlayerIndex.value!!
        val pList = _players.value!!
        val p = pList[pIdx]
        val roll = _diceValue.value!!
        val res = ruleEngine.calculateMove(pIdx, tIdx, p.tokenPositions.get(tIdx), roll, pList, _dynamicSafeZone.value)

        if (res is LudoRuleEngine.MoveResult.Invalid) return
        _gameState.value = State.ANIMATING
        if (res.givesExtraTurn) shouldGiveExtraTurn = true

        val isSpawn = p.tokenPositions.get(tIdx) == -1
        var sound = SoundType.NONE
        var kill: KillInfo? = null

        when(res) {
            is LudoRuleEngine.MoveResult.MoveOnly -> p.tokenPositions.set(tIdx, res.newPosIndex)
            is LudoRuleEngine.MoveResult.SafeZoneLanded -> { p.tokenPositions.set(tIdx, res.newPosIndex); sound = SoundType.SAFE }
            is LudoRuleEngine.MoveResult.SafeStack -> { p.tokenPositions.set(tIdx, res.newPosIndex); sound = SoundType.SAFE }
            is LudoRuleEngine.MoveResult.StarCollected -> {
                p.tokenPositions.set(tIdx, res.newPosIndex); sound = SoundType.STAR_COLLECT
                pendingAnimationEndAction = { p.tokenShields.set(tIdx, true); _dynamicSafeZone.value = null; _announcement.value = Announcement("SHIELD ACQUIRED!", AnnouncementType.TOKEN_GOAL, p.id) }
            }
            is LudoRuleEngine.MoveResult.ShieldBreak -> {
                p.tokenPositions.set(tIdx, res.newPosIndex); sound = SoundType.SHIELD_BREAK
                pendingAnimationEndAction = { pList[res.victimPlayerIdx].tokenShields.set(res.victimTokenIdx, false); _announcement.value = Announcement("SHIELD BROKEN!", AnnouncementType.TOKEN_GOAL, p.id) }
            }
            is LudoRuleEngine.MoveResult.Kill -> {
                p.tokenPositions.set(tIdx, res.newPosIndex)
                pList[res.victimPlayerIdx].tokenPositions.set(res.victimTokenIdx, -1)
                pList[res.victimPlayerIdx].tokenShields.set(res.victimTokenIdx, false)
                kill = KillInfo(res.victimPlayerIdx, res.victimTokenIdx, -1)
                sound = SoundType.KILL; p.kills++; pList[res.victimPlayerIdx].deaths++; _statsUpdate.value = Unit
            }
            is LudoRuleEngine.MoveResult.Win -> {
                p.tokenPositions.set(tIdx, 56); sound = SoundType.WIN
                pendingAnimationEndAction = {
                    p.tokenShields.set(tIdx, false)
                    if (p.getFinishedCount() == currentTokenCount) {
                        rankCounter++; finishedPlayerIds.add(p.id)
                        _announcement.value = Announcement("${p.colorName} RANK $rankCounter", AnnouncementType.PLAYER_VICTORY, p.id)
                    } else {
                        _announcement.value = Announcement("${p.colorName} TOKEN HOME!", AnnouncementType.TOKEN_GOAL, p.id)
                    }
                }
            }
            else -> {}
        }
        _turnUpdate.value = TurnUpdate(pIdx, tIdx, if (isSpawn) 0 else roll, isSpawn, sound, kill)
    }

    fun clearAnnouncement() { _announcement.value = null }

    fun onTurnAnimationsFinished() {
        _turnUpdate.value = null
        pendingAnimationEndAction?.invoke()
        pendingAnimationEndAction = null

        val p = _players.value!!.get(_activePlayerIndex.value!!)
        val all = _players.value!!
        val activeCount = all.size - finishedPlayerIds.size

        if (activeCount <= 1) {
            viewModelScope.launch { delay(1000); _gameState.value = State.GAME_OVER; _statusMessage.value = "GAME OVER" }
            return
        }

        if (finishedPlayerIds.contains(p.id)) {
            passTurn()
        } else if (shouldGiveExtraTurn) {
            _gameState.value = State.WAITING_FOR_ROLL
            _statusMessage.value = "Extra Turn!"
            triggerBotIfActive() // BOT COMBO ROLLS
        } else {
            passTurn()
        }
        saveCurrentState()
    }

    private fun passTurn() {
        val all = _players.value!!
        val activeCount = all.size - finishedPlayerIds.size

        if (activeCount <= 1) {
            viewModelScope.launch { delay(1000); _gameState.value = State.GAME_OVER; _statusMessage.value = "GAME OVER" }
            return
        }

        var next = _activePlayerIndex.value!!
        var safety = 0
        do { next = (next + 1) % all.size; safety++ } while (finishedPlayerIds.contains(all.get(next).id) && safety < 10)

        _activePlayerIndex.value = next
        _gameState.value = State.WAITING_FOR_ROLL
        _statusMessage.value = "${all.get(next).colorName}'s Turn"
        saveCurrentState()
        triggerBotIfActive() // HAND OFF TO NEXT BOT
    }

    fun quitGame() { isGameAbandoned = true; LudoGameStateHolder.clear() }

    fun getFinalRankings(): List<Pair<String, LudoPlayer>> {
        val pList = _players.value ?: return emptyList()
        val rankings = mutableListOf<Pair<String, LudoPlayer>>()
        var rank = 1
        for (id in finishedPlayerIds) { val p = pList.find { it.id == id }; if (p != null) rankings.add(Pair("Rank $rank", p)); rank++ }
        val lastPlayer = pList.find { !finishedPlayerIds.contains(it.id) }
        if (lastPlayer != null) rankings.add(Pair("Last", lastPlayer))
        return rankings
    }
}