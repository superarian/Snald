package com.bytemantis.snald.ludogame

class LudoRuleEngine {

    // Possible outcomes of a move
    // Added "givesExtraTurn" to results
    sealed class MoveResult(val givesExtraTurn: Boolean = false) {
        object Invalid : MoveResult()
        data class MoveOnly(val newPosIndex: Int) : MoveResult()

        // Kill grants extra turn
        data class Kill(val newPosIndex: Int, val victimPlayerIdx: Int, val victimTokenIdx: Int) : MoveResult(true)

        // Landed on safe zone OR Stacked enemies
        data class SafeStack(val newPosIndex: Int) : MoveResult()

        // Landed on empty safe zone
        data class SafeZoneLanded(val newPosIndex: Int) : MoveResult()

        // Win (Home) grants extra turn
        data class Win(val newPosIndex: Int) : MoveResult(true)
    }

    fun calculateMove(
        activePlayerIdx: Int,
        tokenIndex: Int,
        currentPos: Int,
        diceRoll: Int,
        allPlayers: List<LudoPlayer>
    ): MoveResult {

        // --- 1. HANDLE SPAWN (Base -> Start) ---
        if (currentPos == -1) {
            if (diceRoll == 6) {
                val startPos = 0
                val collision = checkCollision(activePlayerIdx, startPos, allPlayers)

                // If there is someone on our Start Square...
                if (collision != null) {
                    // CRITICAL FIX: The Start Square is ALWAYS a Safe Zone.
                    // So we NEVER kill here. We always stack.
                    return MoveResult.SafeStack(startPos)
                }
                return MoveResult.MoveOnly(startPos)
            } else {
                return MoveResult.Invalid
            }
        }

        // --- 2. HANDLE NORMAL MOVE ---
        val targetPos = currentPos + diceRoll
        if (targetPos > 56) return MoveResult.Invalid
        if (targetPos == 57) return MoveResult.Win(57)

        val targetGlobal = LudoBoardConfig.getGlobalCoord(activePlayerIdx, targetPos)
            ?: return MoveResult.MoveOnly(targetPos)

        // --- 3. CHECK COLLISIONS & SAFE ZONES ---
        val isStaticSafe = LudoBoardConfig.SAFE_ZONES.contains(targetGlobal)
        val collision = checkCollision(activePlayerIdx, targetPos, allPlayers)

        if (collision != null) {
            // A. Dynamic Safe Zone (Team Block)
            // If >1 enemy is here, it's a block/safe zone.
            if (isEnemyBlock(activePlayerIdx, targetPos, allPlayers)) {
                return MoveResult.SafeStack(targetPos)
            }

            // B. Static Safe Zone (Star/Globe/Start Box)
            // If it's a Star or Start Box, we cannot kill.
            if (isStaticSafe) {
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

    // Helper to find collisions
    private fun checkCollision(activePIdx: Int, targetPos: Int, allPlayers: List<LudoPlayer>): Pair<Int, Int>? {
        val targetGlobal = LudoBoardConfig.getGlobalCoord(activePIdx, targetPos) ?: return null

        for (enemy in allPlayers) {
            if (enemy.id == (activePIdx + 1)) continue // Skip self

            for (i in 0 until 4) {
                val enemyPos = enemy.tokenPositions[i]
                if (enemyPos < 0 || enemyPos > 50) continue // Ignore base or home stretch

                val enemyGlobal = LudoBoardConfig.getGlobalCoord(enemy.id - 1, enemyPos)
                if (targetGlobal == enemyGlobal) {
                    return Pair(enemy.id - 1, i)
                }
            }
        }
        return null
    }

    // NEW: Helper to check if multiple enemies are on the target (Dynamic Safe Zone)
    private fun isEnemyBlock(activePIdx: Int, targetPos: Int, allPlayers: List<LudoPlayer>): Boolean {
        val targetGlobal = LudoBoardConfig.getGlobalCoord(activePIdx, targetPos) ?: return false
        var enemyCount = 0

        for (enemy in allPlayers) {
            if (enemy.id == (activePIdx + 1)) continue // Skip self

            for (pos in enemy.tokenPositions) {
                if (pos < 0 || pos > 50) continue
                val enemyGlobal = LudoBoardConfig.getGlobalCoord(enemy.id - 1, pos)
                if (targetGlobal == enemyGlobal) {
                    enemyCount++
                }
            }
        }
        return enemyCount > 1
    }
}