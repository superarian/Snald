package com.bytemantis.snald.ludogame

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import com.bytemantis.snald.core.SoundManager
import kotlinx.coroutines.delay

class LudoAnimationManager(private val soundManager: SoundManager) {

    // OWNER FIX: Faster movement (150ms instead of 200ms)
    private val HOP_DURATION = 150L
    private val SLIDE_SPEED_PPS = 1200f // Faster slide

    suspend fun animateHop(
        tokenView: View,
        playerIdx: Int,
        startPathIndex: Int,
        steps: Int,
        cellW: Float,
        cellH: Float,
        boardOffsetX: Float,
        boardOffsetY: Float
    ) {
        var currentPathIndex = startPathIndex

        // 1. If starting from Base (-1), jump to Start (0)
        if (currentPathIndex == -1) {
            val startCoord = LudoBoardConfig.getGlobalCoord(playerIdx, 0) ?: return

            // OWNER FIX: We do NOT play hop sound here, checking Activity will play Aura sound via event
            // But we keep the visual move
            animateMoveTo(tokenView, startCoord, cellW, cellH, boardOffsetX, boardOffsetY, 300)
            delay(300)

            currentPathIndex = 0

            // OWNER FIX: If visual steps (from ViewModel) is 0, we STOP here.
            // This prevents the glitch where it moves to index 1 then snaps back.
            if (steps == 0) return
        }

        // 2. Loop through the steps
        for (i in 1..steps) {
            val nextPathIndex = currentPathIndex + 1
            if (nextPathIndex > 57) break

            val targetCoord = LudoBoardConfig.getGlobalCoord(playerIdx, nextPathIndex) ?: break

            animateMoveTo(tokenView, targetCoord, cellW, cellH, boardOffsetX, boardOffsetY, HOP_DURATION)
            soundManager.playHop()

            // Faster delay between hops
            delay(HOP_DURATION + 20)
            currentPathIndex = nextPathIndex
        }
    }

    suspend fun animateDeathSlide(
        tokenView: View,
        startCoord: Pair<Int, Int>,
        baseCoord: Pair<Float, Float>,
        cellW: Float,
        cellH: Float,
        boardOffsetX: Float,
        boardOffsetY: Float
    ) {
        val startX = boardOffsetX + (startCoord.first * cellW)
        val startY = boardOffsetY + (startCoord.second * cellH)
        val endX = boardOffsetX + (baseCoord.first * cellW)
        val endY = boardOffsetY + (baseCoord.second * cellH)

        val dx = endX - startX
        val dy = endY - startY
        val distance = kotlin.math.sqrt(dx*dx + dy*dy)

        val duration = (distance / SLIDE_SPEED_PPS * 1000).toLong().coerceAtLeast(200)

        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            tokenView.animate()
                .x(endX + (cellW - tokenView.width)/2)
                .y(endY + (cellH - tokenView.height)/2)
                .setDuration(duration)
                .setInterpolator(AccelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        continuation.resume(Unit, null)
                    }
                })
                .start()
        }
    }

    private fun animateMoveTo(
        view: View,
        gridPos: Pair<Int, Int>,
        cellW: Float,
        cellH: Float,
        offsetX: Float,
        offsetY: Float,
        duration: Long
    ) {
        val targetX = offsetX + (gridPos.first * cellW) + (cellW - view.width) / 2
        val targetY = offsetY + (gridPos.second * cellH) + (cellH - view.height) / 2

        view.animate()
            .x(targetX)
            .y(targetY)
            .setDuration(duration)
            .setInterpolator(LinearInterpolator())
            .start()
    }
}