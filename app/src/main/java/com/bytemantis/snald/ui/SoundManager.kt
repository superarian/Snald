package com.bytemantis.snald.ui

import android.content.Context
import android.media.MediaPlayer
import com.bytemantis.snald.R

class SoundManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    // NEW: Dice Roll Sound
    fun playDiceRoll() {
        // MAKE SURE YOU HAVE 'sfx_dice_roll' in res/raw!
        playSound(R.raw.sfx_dice_roll)
    }

    fun playSnakeBite() {
        playSound(R.raw.sfx_snake_bite)
    }

    fun playLadderClimb() {
        playSound(R.raw.sfx_ladder_climb)
    }

    fun playStarCollect() {
        playSound(R.raw.sfx_star_collect)
    }

    fun playStarUsed() {
        playSound(R.raw.sfx_star_use)
    }

    fun playWin() {
        playSound(R.raw.sfx_win)
    }

    private fun playSound(resId: Int) {
        // 1. Release previous sound to avoid overlapping chaos
        mediaPlayer?.release()

        // 2. Create and Start new sound
        try {
            mediaPlayer = MediaPlayer.create(context, resId)
            mediaPlayer?.start()

            // 3. Clean up when finished
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}