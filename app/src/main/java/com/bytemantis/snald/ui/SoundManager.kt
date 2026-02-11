package com.bytemantis.snald.ui

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.SparseIntArray
import android.view.animation.LinearInterpolator
import com.bytemantis.snald.R

class SoundManager(context: Context) {

    private val soundPool: SoundPool
    // Correct initialization
    private val soundMap = SparseIntArray()

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load all sounds
        soundMap.put(R.raw.sfx_dice_roll, soundPool.load(context, R.raw.sfx_dice_roll, 1))
        soundMap.put(R.raw.sfx_hop, soundPool.load(context, R.raw.sfx_hop, 1))
        soundMap.put(R.raw.sfx_snake_bite, soundPool.load(context, R.raw.sfx_snake_bite, 1))
        soundMap.put(R.raw.sfx_ladder_climb, soundPool.load(context, R.raw.sfx_ladder_climb, 1))
        soundMap.put(R.raw.sfx_star_collect, soundPool.load(context, R.raw.sfx_star_collect, 1))
        soundMap.put(R.raw.sfx_star_use, soundPool.load(context, R.raw.sfx_star_use, 1))
        soundMap.put(R.raw.sfx_win, soundPool.load(context, R.raw.sfx_win, 1))

        // Ensure you have 'sfx_slide_back.mp3' in res/raw/
        soundMap.put(R.raw.sfx_slide_back, soundPool.load(context, R.raw.sfx_slide_back, 1))
    }

    // UPDATED: Now returns Int (Stream ID)
    fun playDiceRoll(): Int = play(R.raw.sfx_dice_roll)
    fun playHop(): Int = play(R.raw.sfx_hop)
    fun playSnakeBite(): Int = play(R.raw.sfx_snake_bite)
    fun playLadderClimb(): Int = play(R.raw.sfx_ladder_climb)
    fun playStarUsed(): Int = play(R.raw.sfx_star_use)
    fun playWin(): Int = play(R.raw.sfx_win)
    fun playSlideBack(): Int = play(R.raw.sfx_slide_back)

    // NEW: Stop a specific sound
    fun stop(streamId: Int) {
        if (streamId != 0) {
            soundPool.stop(streamId)
        }
    }

    fun playStarCollect() {
        val soundId = soundMap.get(R.raw.sfx_star_collect)
        if (soundId == 0) return

        val streamId = soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)

        // Fade out effect
        val fadeAnim = ValueAnimator.ofFloat(1.0f, 0.0f)
        fadeAnim.duration = 1500L
        fadeAnim.interpolator = LinearInterpolator()

        fadeAnim.addUpdateListener { animation ->
            val volume = animation.animatedValue as Float
            soundPool.setVolume(streamId, volume, volume)
        }

        fadeAnim.startDelay = 500
        fadeAnim.start()
    }

    // UPDATED: Returns Stream ID
    private fun play(resId: Int): Int {
        val soundId = soundMap.get(resId)
        if (soundId == 0) return 0

        // Returns the stream ID (non-zero if successful)
        return soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }
}