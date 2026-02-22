package com.bytemantis.snald.ludogame

class LudoRuleEngine {

    sealed class MoveResult(val givesExtraTurn: Boolean = false) {
        object Invalid : MoveResult()
        data class MoveOnly(val newPosIndex: Int) : MoveResult()
        data class Kill(val newPosIndex: Int, val victimPlayerIdx: Int, val victimTokenIdx: Int) : MoveResult(true)
        data class Win(val newPosIndex: Int) : MoveResult(true)
        data class SafeStack(val newPosIndex: Int) : MoveResult()
        data class SafeZoneLanded(val newPosIndex: Int) : MoveResult()
        data class StarCollected(val newPosIndex: Int) : MoveResult()
        data class ShieldBreak(val newPosIndex: Int, val victimPlayerIdx: Int, val victimTokenIdx: Int) : MoveResult()
    }

    fun calculateMove(
        activePlayerIdx: Int,
        tokenIndex: Int,
        currentPos: Int,
        diceRoll: Int,
        allPlayers: List<LudoPlayer>,
        dynamicSafeZone: Pair<Int, Int>?
    ): MoveResult {
        val player = allPlayers.get(activePlayerIdx)
        val tokensActiveOnBoard = player.tokenPositions.count { it in 0..55 }

        if (currentPos == -1) {
            val canSpawnOnSix = (diceRoll == 6)
            val canSpawnOnOne = (diceRoll == 1 && tokensActiveOnBoard == 0)

            if (canSpawnOnSix || canSpawnOnOne) {
                val startPos = 0
                val collision = checkCollision(activePlayerIdx, startPos, allPlayers)
                if (collision != null) return MoveResult.SafeStack(startPos)
                return MoveResult.MoveOnly(startPos)
            } else {
                return MoveResult.Invalid
            }
        }

        val targetPos = currentPos + diceRoll
        if (targetPos > 56) return MoveResult.Invalid
        if (targetPos == 56) return MoveResult.Win(56)

        val targetGlobal = LudoBoardConfig.getGlobalCoord(activePlayerIdx, targetPos)
            ?: return MoveResult.MoveOnly(targetPos)

        val isStaticSafe = LudoBoardConfig.SAFE_ZONES.contains(targetGlobal)
        val isDynamicSafe = (dynamicSafeZone == targetGlobal)
        val collision = checkCollision(activePlayerIdx, targetPos, allPlayers)

        if (collision != null) {
            val (victimP, victimT) = collision

            if (isEnemyBlock(activePlayerIdx, targetPos, allPlayers) || isStaticSafe) {
                return MoveResult.SafeStack(targetPos)
            }

            if (allPlayers.get(victimP).tokenShields.get(victimT)) {
                return MoveResult.ShieldBreak(targetPos, victimP, victimT)
            }

            return MoveResult.Kill(targetPos, victimP, victimT)
        }

        if (isDynamicSafe) {
            return MoveResult.StarCollected(targetPos)
        }

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
                val enemyPos = enemy.tokenPositions.get(i)
                if (enemyPos !in 0..50) continue
                val enemyGlobal = LudoBoardConfig.getGlobalCoord(enemy.id - 1, enemyPos)
                if (targetGlobal == enemyGlobal) return Pair(enemy.id - 1, i)
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