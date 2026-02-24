package com.bytemantis.snald.ludogame

class LudoBotEngine(private val ruleEngine: LudoRuleEngine) {

    fun getBestMove(
        activePlayerIdx: Int,
        diceRoll: Int,
        allPlayers: List<LudoPlayer>,
        dynamicSafeZone: Pair<Int, Int>?
    ): Int? {
        val player = allPlayers[activePlayerIdx]
        var bestTokenIdx: Int? = null
        var highestScore = -9999

        for (tIdx in 0 until player.tokenCount) {
            val currentPos = player.tokenPositions[tIdx]
            val res = ruleEngine.calculateMove(activePlayerIdx, tIdx, currentPos, diceRoll, allPlayers, dynamicSafeZone)

            if (res is LudoRuleEngine.MoveResult.Invalid) continue

            var score = 0

            // 1. Evaluate the Tactical Outcome
            when (res) {
                is LudoRuleEngine.MoveResult.Win -> score += 5000
                is LudoRuleEngine.MoveResult.Kill -> score += 2000
                is LudoRuleEngine.MoveResult.StarCollected -> score += 1000
                is LudoRuleEngine.MoveResult.ShieldBreak -> score += 800
                is LudoRuleEngine.MoveResult.SafeZoneLanded -> score += 500
                is LudoRuleEngine.MoveResult.SafeStack -> score += 400
                is LudoRuleEngine.MoveResult.MoveOnly -> {
                    if (currentPos == -1) {
                        score += 600 // Priority to get tokens on the board
                    } else {
                        score += (currentPos + diceRoll) * 2 // Push advanced tokens further
                    }
                }
                else -> {}
            }

            // 2. Evaluate the Risk (Defensive play)
            if (currentPos in 0..55) {
                val currentGlobal = LudoBoardConfig.getGlobalCoord(activePlayerIdx, currentPos)
                if (currentGlobal != null && LudoBoardConfig.SAFE_ZONES.contains(currentGlobal)) {
                    // Penalize leaving a safe zone if it's just a normal move
                    if (res is LudoRuleEngine.MoveResult.MoveOnly) {
                        score -= 300
                    }
                }
            }

            // Keep track of the highest scoring move
            if (score > highestScore) {
                highestScore = score
                bestTokenIdx = tIdx
            }
        }
        return bestTokenIdx
    }
}