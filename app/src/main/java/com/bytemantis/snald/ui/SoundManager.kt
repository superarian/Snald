package com.bytemantis.snald.ui

import android.content.Context
import android.media.MediaPlayer
import com.bytemantis.snald.R

class SoundManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    fun playDiceRoll() {
        playSound(R.raw.sfx_dice_roll)
    }

    // NEW: Hop Sound
    fun playHop() {
        // MAKE SURE YOU HAVE 'sfx_hop' in res/raw!
        playSound(R.raw.sfx_hop)
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
        try {
            // Release previous to allow fast repetitive sounds (like hopping)
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer.create(context, resId)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}