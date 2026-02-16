package com.bytemantis.snald.ludogame

class LudoRuleEngine {

    // Added "givesExtraTurn" to results
    sealed class MoveResult(val givesExtraTurn: Boolean = false) {
        object Invalid : MoveResult()
        data class MoveOnly(val newPosIndex: Int) : MoveResult()

        // Kill grants extra turn
        data class Kill(val newPosIndex: Int, val victimPlayerIdx: Int, val victimTokenIdx: Int) : MoveResult(true)

        data class SafeStack(val newPosIndex: Int) : MoveResult()
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

        // 1. Check Spawn
        if (currentPos == -1) {
            if (diceRoll == 6) {
                // Check if spawn lands on enemy (Kill at start)
                val startPos = 0
                val collision = checkCollision(activePlayerIdx, startPos, allPlayers)
                return if (collision != null) {
                    MoveResult.Kill(startPos, collision.first, collision.second)
                } else {
                    MoveResult.MoveOnly(startPos)
                }
            }
            else return MoveResult.Invalid
        }

        // 2. Check Target
        val targetPos = currentPos + diceRoll
        if (targetPos > 56) return MoveResult.Invalid
        if (targetPos == 57) return MoveResult.Win(57)

        // 3. Check Collision
        val collision = checkCollision(activePlayerIdx, targetPos, allPlayers)
        if (collision != null) {
            val (victimP, victimT) = collision
            val targetGlobal = LudoBoardConfig.getGlobalCoord(activePlayerIdx, targetPos)

            // If Safe Zone, stack instead of kill
            if (targetGlobal != null && LudoBoardConfig.SAFE_ZONES.contains(targetGlobal)) {
                return MoveResult.SafeStack(targetPos)
            }
            return MoveResult.Kill(targetPos, victimP, victimT)
        }

        // 4. Check Safe Zone Land (No collision)
        val targetGlobal = LudoBoardConfig.getGlobalCoord(activePlayerIdx, targetPos)
        if (targetGlobal != null && LudoBoardConfig.SAFE_ZONES.contains(targetGlobal)) {
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
}