package com.bytemantis.snald.ludogame

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LudoViewModel : ViewModel() {

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

    // --- NETWORK VARIABLES ---
    val networkManager = LudoNetworkManager()
    var isMultiplayer = false
    var localPlayerId = 0 // 0 for Host, 1 for Joiner
    private var connectedHumans = 1

    private val _roomCode = MutableLiveData<String>("")
    val roomCode: LiveData<String> = _roomCode

    private val ruleEngine = LudoRuleEngine()
    private val botEngine = LudoBotEngine(ruleEngine)

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
        // Only the host generates random safe zones in multiplayer to prevent desync
        if (isMultiplayer && localPlayerId != 0) return

        val outerPathCoords = (LudoBoardConfig.PATH_RED.take(51) + LudoBoardConfig.PATH_GREEN.take(51) + LudoBoardConfig.PATH_BLUE.take(51) + LudoBoardConfig.PATH_YELLOW.take(51)).toSet()
        val available = outerPathCoords - LudoBoardConfig.SAFE_ZONES
        if (available.isNotEmpty()) {
            val zone = available.random()
            if (isMultiplayer) {
                // Encode coordinate (x, y) as x * 15 + y
                networkManager.pushAction("SPAWN_STAR", localPlayerId, zone.first * 15 + zone.second)
            } else {
                _dynamicSafeZone.value = zone
            }
        }
    }

    // --- NETWORK LOBBY LOGIC ---

    fun hostMultiplayerGame(playerCount: Int, tokenCount: Int) {
        isMultiplayer = true
        localPlayerId = 0
        currentTokenCount = tokenCount
        connectedHumans = 1
        val targetHumans = tempPlayerCount - tempBotCount

        _statusMessage.value = "Creating Room..."
        networkManager.createPrivateRoom(playerCount, tokenCount) { roomId ->
            if (roomId != null) {
                _roomCode.value = roomId
                setupNetworkListener()

                if (connectedHumans >= targetHumans) {
                    networkManager.pushAction("START_MATCH", 0, currentTokenCount)
                } else {
                    _statusMessage.value = "Room: $roomId - Waiting for players..."
                }
            } else {
                _statusMessage.value = "Failed to create room."
            }
        }
    }

    fun joinMultiplayerGame(roomId: String) {
        isMultiplayer = true
        _statusMessage.value = "Joining..."
        networkManager.joinPrivateRoom(roomId) { success, roomInfo ->
            if (success && roomInfo != null) {
                _roomCode.value = roomId
                tempPlayerCount = roomInfo.playerCount
                currentTokenCount = roomInfo.tokenCount
                
                // Fetch existing joiners to determine localPlayerId
                networkManager.getJoinedPlayersCount { count ->
                    localPlayerId = count + 1 // Host is 0, first joiner is 1, etc.
                    _statusMessage.value = "Joined as Player ${localPlayerId + 1}! Waiting for Host..."
                    setupNetworkListener()
                    networkManager.pushAction("PLAYER_JOINED", localPlayerId, 0)
                }
            } else {
                _statusMessage.value = "Invalid Room Code."
            }
        }
    }

    private fun setupNetworkListener() {
        networkManager.startListeningForActions { action ->
            processNetworkAction(action)
        }
    }

    private fun processNetworkAction(action: LudoAction) {
        when (action.type) {
            "PLAYER_JOINED" -> {
                if (localPlayerId == 0) {
                    connectedHumans++
                    val targetHumans = tempPlayerCount - tempBotCount
                    if (connectedHumans >= targetHumans) {
                        networkManager.pushAction("START_MATCH", 0, currentTokenCount)
                    } else {
                        _statusMessage.value = "Players Joined: $connectedHumans / $targetHumans"
                    }
                }
            }
            "START_MATCH" -> executeStartGame(action.value)
            "ROLL_DICE" -> executeRollDice(action.value, action.playerId)
            "MOVE_TOKEN" -> executeTokenMove(action.value, action.playerId)
            "PASS_TURN" -> executePassTurn(action.playerId)
            "SPAWN_STAR" -> {
                val x = action.value / 15
                val y = action.value % 15
                _dynamicSafeZone.value = Pair(x, y)
            }
        }
    }

    private fun isLocalControl(pIdx: Int): Boolean {
        if (!isMultiplayer) return true
        val p = _players.value?.get(pIdx) ?: return false
        if (p.isBot && localPlayerId == 0) return true
        return pIdx == localPlayerId
    }

    // Navigation
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

    // --- GAME START LOGIC ---

    fun startGame(tokenCount: Int) {
        if (isMultiplayer) {
            if (localPlayerId == 0) {
                networkManager.pushAction("START_MATCH", 0, tokenCount)
            }
        } else {
            executeStartGame(tokenCount)
        }
    }

    private fun executeStartGame(tokenCount: Int) {
        currentTokenCount = tokenCount
        val colors = listOf("RED", "GREEN", "BLUE", "YELLOW")
        val humanCount = tempPlayerCount - tempBotCount

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
        triggerBotIfActive()
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

    private fun triggerBotIfActive() {
        val p = _players.value?.get(_activePlayerIndex.value ?: 0) ?: return
        if (p.isBot && _gameState.value == State.WAITING_FOR_ROLL) {
            if (isLocalControl(p.id - 1)) {
                viewModelScope.launch {
                    delay(1200)
                    rollDice()
                }
            }
        }
    }

    // --- DICE ROLL LOGIC ---

    fun rollDice() {
        if (_gameState.value != State.WAITING_FOR_ROLL) return
        val pIdx = _activePlayerIndex.value!!

        if (!isLocalControl(pIdx)) return

        _gameState.value = State.ANIMATING
        val roll = (1..6).random()

        if (isMultiplayer) {
            networkManager.pushAction("ROLL_DICE", pIdx, roll)
        } else {
            executeRollDice(roll, pIdx)
        }
    }

    private fun executeRollDice(roll: Int, pIdx: Int) {
        _gameState.value = State.ANIMATING
        _diceValue.value = roll
        shouldGiveExtraTurn = false

        val pList = _players.value!!
        val p = pList[pIdx]

        if (roll == 6) {
            p.sixesRolled++
            shouldGiveExtraTurn = true
            _statsUpdate.value = Unit
        }

        if (isLocalControl(pIdx)) {
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
                            delay(800)
                            if (bestMove != null) onTokenClicked(bestMove)
                        } else {
                            _gameState.value = State.WAITING_FOR_MOVE
                            _statusMessage.value = "Select Token"
                        }
                    }
                } else {
                    shouldGiveExtraTurn = false
                    delay(800)
                    if (isMultiplayer) {
                        networkManager.pushAction("PASS_TURN", pIdx, 0)
                    } else {
                        executePassTurn(pIdx)
                    }
                }
            }
        } else {
            // Remote watcher ignores local delays and awaits network explicit commands
            _gameState.value = State.ANIMATING
            _statusMessage.value = "Waiting for ${p.colorName}..."
        }
    }

    // --- TOKEN MOVE & PASS LOGIC ---

    fun onTokenClicked(tIdx: Int) {
        if (_gameState.value != State.WAITING_FOR_MOVE && _gameState.value != State.ANIMATING) return
        val pIdx = _activePlayerIndex.value!!

        if (!isLocalControl(pIdx)) return

        if (isMultiplayer) {
            networkManager.pushAction("MOVE_TOKEN", pIdx, tIdx)
        } else {
            executeTokenMove(tIdx, pIdx)
        }
    }

    private fun executeTokenMove(tIdx: Int, pIdx: Int) {
        _gameState.value = State.ANIMATING
        val pList = _players.value!!
        val p = pList[pIdx]
        val roll = _diceValue.value!!
        val res = ruleEngine.calculateMove(pIdx, tIdx, p.tokenPositions.get(tIdx), roll, pList, _dynamicSafeZone.value)

        if (res is LudoRuleEngine.MoveResult.Invalid) return
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

    private fun executePassTurn(pIdx: Int) {
        shouldGiveExtraTurn = false
        passTurn()
    }

    // --- TURN FINISHING & CLEANUP ---

    fun clearAnnouncement() { _announcement.value = null }

    fun onTurnAnimationsFinished() {
        _turnUpdate.value = null
        pendingAnimationEndAction?.invoke()
        pendingAnimationEndAction = null

        val p = _players.value!!.get(_activePlayerIndex.value!!)
        val all = _players.value!!
        val activeCount = all.size - finishedPlayerIds.size

        if (activeCount <= 1) {
            viewModelScope.launch { 
                delay(1000)
                _gameState.value = State.GAME_OVER
                _statusMessage.value = "GAME OVER"
                if (isMultiplayer && localPlayerId == 0) {
                    networkManager.deleteRoom { success ->
                        if (success) Log.d("LudoViewModel", "Room deleted successfully")
                    }
                }
            }
            return
        }

        if (finishedPlayerIds.contains(p.id)) {
            passTurn()
        } else if (shouldGiveExtraTurn) {
            _gameState.value = State.WAITING_FOR_ROLL
            _statusMessage.value = "Extra Turn!"
            triggerBotIfActive()
        } else {
            passTurn()
        }
        saveCurrentState()
    }

    private fun passTurn() {
        val all = _players.value!!
        val activeCount = all.size - finishedPlayerIds.size

        if (activeCount <= 1) {
            viewModelScope.launch { 
                delay(1000)
                _gameState.value = State.GAME_OVER
                _statusMessage.value = "GAME OVER"
                if (isMultiplayer && localPlayerId == 0) {
                    networkManager.deleteRoom { success ->
                        if (success) Log.d("LudoViewModel", "Room deleted successfully")
                    }
                }
            }
            return
        }

        var next = _activePlayerIndex.value!!
        var safety = 0
        do { next = (next + 1) % all.size; safety++ } while (finishedPlayerIds.contains(all.get(next).id) && safety < 10)

        _activePlayerIndex.value = next
        _gameState.value = State.WAITING_FOR_ROLL
        _statusMessage.value = "${all.get(next).colorName}'s Turn"
        saveCurrentState()
        triggerBotIfActive()
    }

    fun quitGame() {
        isGameAbandoned = true
        if (isMultiplayer) {
            if (localPlayerId == 0) {
                networkManager.deleteRoom { success ->
                    if (success) Log.d("LudoViewModel", "Host deleted room on quit")
                }
            }
            networkManager.stopListening()
        }
        LudoGameStateHolder.clear()
    }

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