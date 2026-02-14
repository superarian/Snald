package com.bytemantis.snald

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bytemantis.snald.core.SoundManager // Assuming you moved SoundManager to the core package
import com.bytemantis.snald.ludogame.LudoActivity
import com.bytemantis.snald.snaldgame.SnaldActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MenuActivity : AppCompatActivity() {

    private lateinit var splashLayout: FrameLayout
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        // Initialize Sound Manager for Menu Music
        soundManager = SoundManager(this)

        // Find Layouts and Buttons
        splashLayout = findViewById(R.id.layout_splash)
        val btnSnald = findViewById<Button>(R.id.btn_play_snald)
        val btnLudo = findViewById<Button>(R.id.btn_play_ludo)

        // Set up click listeners for the games
        btnSnald.setOnClickListener {
            val intent = Intent(this, SnaldActivity::class.java)
            startActivity(intent)
        }

        btnLudo.setOnClickListener {
            val intent = Intent(this, LudoActivity::class.java)
            startActivity(intent)
        }

        // --- Splash Screen & Music Logic ---
        splashLayout.visibility = View.VISIBLE
        soundManager.startMenuMusic()

        lifecycleScope.launch {
            delay(3000) // 3 Seconds Splash
            splashLayout.visibility = View.GONE
        }
    }

    // --- Lifecycle Audio Management ---
    override fun onPause() {
        super.onPause()
        soundManager.pauseMusic()
    }

    override fun onResume() {
        super.onResume()
        // Resume music only if the splash screen is gone and we are on the menu
        if (splashLayout.visibility == View.GONE) {
            soundManager.resumeMusic()
        }
    }
}