package com.bytemantis.snald.ludogame

class LudoRuleEngine {

    sealed class MoveResult(val givesExtraTurn: Boolean = false) {
        object Invalid : MoveResult()
        data class MoveOnly(val newPosIndex: Int) : MoveResult()
        data class Kill(val newPosIndex: Int, val victimPlayerIdx: Int, val victimTokenIdx: Int) : MoveResult(true)
        data class SafeStack(val newPosIndex: Int) : MoveResult()
        data class SafeZoneLanded(val newPosIndex: Int) : MoveResult()
        data class Win(val newPosIndex: Int) : MoveResult(true)
    }

    fun calculateMove(
        activePlayerIdx: Int,
        tokenIndex: Int,
        currentPos: Int,
        diceRoll: Int,
        allPlayers: List<LudoPlayer>
    ): MoveResult {

        // 1. HANDLE SPAWN (Base -> Start)
        if (currentPos == -1) {
            if (diceRoll == 6) {
                val startPos = 0
                val collision = checkCollision(activePlayerIdx, startPos, allPlayers)
                if (collision != null) return MoveResult.SafeStack(startPos)
                return MoveResult.MoveOnly(startPos)
            } else return MoveResult.Invalid
        }

        // 2. HANDLE NORMAL MOVE
        val targetPos = currentPos + diceRoll
        if (targetPos > 56) return MoveResult.Invalid
        if (targetPos == 57) return MoveResult.Win(57)

        val targetGlobal = LudoBoardConfig.getGlobalCoord(activePlayerIdx, targetPos)
            ?: return MoveResult.MoveOnly(targetPos)

        val isStaticSafe = LudoBoardConfig.SAFE_ZONES.contains(targetGlobal)
        val collision = checkCollision(activePlayerIdx, targetPos, allPlayers)

        if (collision != null) {
            // Dynamic Safe Zone (Team Block)
            if (isEnemyBlock(activePlayerIdx, targetPos, allPlayers)) return MoveResult.SafeStack(targetPos)
            // Static Safe Zone
            if (isStaticSafe) return MoveResult.SafeStack(targetPos)
            // Otherwise -> KILL
            return MoveResult.Kill(targetPos, collision.first, collision.second)
        }

        if (isStaticSafe) return MoveResult.SafeZoneLanded(targetPos)

        return MoveResult.MoveOnly(targetPos)
    }

    private fun checkCollision(activePIdx: Int, targetPos: Int, allPlayers: List<LudoPlayer>): Pair<Int, Int>? {
        val targetGlobal = LudoBoardConfig.getGlobalCoord(activePIdx, targetPos) ?: return null

        for (enemy in allPlayers) {
            if (enemy.id == (activePIdx + 1)) continue

            // OWNER FIX: Iterate through actual token positions size (Prevents Crash)
            for (i in enemy.tokenPositions.indices) {
                val enemyPos = enemy.tokenPositions[i]
                if (enemyPos < 0 || enemyPos > 50) continue

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
                if (pos < 0 || pos > 50) continue
                val enemyGlobal = LudoBoardConfig.getGlobalCoord(enemy.id - 1, pos)
                if (targetGlobal == enemyGlobal) enemyCount++
            }
        }
        return enemyCount > 1
    }
}