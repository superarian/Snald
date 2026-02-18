package com.bytemantis.snald.ludogame

class LudoRuleEngine {

    /**
     * OWNER APPROACH: Scalable Move Result system.
     * givesExtraTurn is true for Kills, Wins, and 6s (handled in VM).
     */
    sealed class MoveResult(val givesExtraTurn: Boolean = false) {
        object Invalid : MoveResult()
        data class MoveOnly(val newPosIndex: Int) : MoveResult()

        // Kills and Wins grant an extra turn
        data class Kill(val newPosIndex: Int, val victimPlayerIdx: Int, val victimTokenIdx: Int) : MoveResult(true)
        data class Win(val newPosIndex: Int) : MoveResult(true)

        // Safe zones
        data class SafeStack(val newPosIndex: Int) : MoveResult()
        data class SafeZoneLanded(val newPosIndex: Int) : MoveResult()
    }

    fun calculateMove(
        activePlayerIdx: Int,
        tokenIndex: Int,
        currentPos: Int,
        diceRoll: Int,
        allPlayers: List<LudoPlayer>
    ): MoveResult {
        val player = allPlayers[activePlayerIdx]

        // Count tokens currently on the active board (0 to 56)
        val tokensActiveOnBoard = player.tokenPositions.count { it in 0..56 }

        // --- 1. SPAWN LOGIC (BASE -> START) ---
        if (currentPos == -1) {
            val canSpawnOnSix = (diceRoll == 6)
            // Desperation Start: Can spawn on 1 ONLY if 0 tokens are on the board
            val canSpawnOnOne = (diceRoll == 1 && tokensActiveOnBoard == 0)

            if (canSpawnOnSix || canSpawnOnOne) {
                val startPos = 0
                val collision = checkCollision(activePlayerIdx, startPos, allPlayers)

                // If someone is on our start square, we stack (Start squares are safe)
                if (collision != null) return MoveResult.SafeStack(startPos)
                return MoveResult.MoveOnly(startPos)
            } else {
                return MoveResult.Invalid
            }
        }

        // --- 2. MOVEMENT LOGIC ---
        val targetPos = currentPos + diceRoll

        // Overshot the home center
        if (targetPos > 57) return MoveResult.Invalid

        // Exact 57 is a Win (Home)
        if (targetPos == 57) return MoveResult.Win(57)

        val targetGlobal = LudoBoardConfig.getGlobalCoord(activePlayerIdx, targetPos)
            ?: return MoveResult.MoveOnly(targetPos)

        // --- 3. COLLISION & SAFE ZONES ---
        val isStaticSafe = LudoBoardConfig.SAFE_ZONES.contains(targetGlobal)
        val collision = checkCollision(activePlayerIdx, targetPos, allPlayers)

        if (collision != null) {
            // A. Dynamic Safe Zone (Stacked Enemies) or B. Static Safe Zone (Star/Start Box)
            if (isEnemyBlock(activePlayerIdx, targetPos, allPlayers) || isStaticSafe) {
                return MoveResult.SafeStack(targetPos)
            }

            // C. Otherwise -> KILL
            val (victimP, victimT) = collision
            return MoveResult.Kill(targetPos, victimP, victimT)
        }

        // D. Landed on Empty Safe Zone
        if (isStaticSafe) {
            return MoveResult.SafeZoneLanded(targetPos)
        }

        return MoveResult.MoveOnly(targetPos)
    }

    private fun checkCollision(activePIdx: Int, targetPos: Int, allPlayers: List<LudoPlayer>): Pair<Int, Int>? {
        val targetGlobal = LudoBoardConfig.getGlobalCoord(activePIdx, targetPos) ?: return null

        for (enemy in allPlayers) {
            if (enemy.id == (activePIdx + 1)) continue

            for (i in enemy.tokenPositions.indices) {
                val enemyPos = enemy.tokenPositions[i]
                if (enemyPos !in 0..50) continue // Only tokens on common path can be killed

                val enemyGlobal = LudoBoardConfig.getGlobalCoord(enemy.id - 1, enemyPos)
                if (targetGlobal == enemyGlobal) {
                    return Pair(enemy.id - 1, i)
                }
            }
        }
        return null
    }

    private fun isEnemyBlock(activePIdx: Int, targetPos: Int, allPlayers: List<LudoPlayer>): Boolean {
        val targetGlobal = LudoBoardConfig.getGlobalCoord(activePIdx, targetPos) ?: return false
        var enemyCount = 0

        for (enemy in allPlayers) {
            if (enemy.id == (activePIdx + 1)) continue
            for (pos in enemy.tokenPositions) {
                if (pos !in 0..50) continue
                val enemyGlobal = LudoBoardConfig.getGlobalCoord(enemy.id - 1, pos)
                if (targetGlobal == enemyGlobal) enemyCount++
            }
        }
        return enemyCount > 1
    }
}