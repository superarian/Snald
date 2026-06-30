package com.bytemantis.snald

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bytemantis.snald.core.SoundManager
import com.bytemantis.snald.ludogame.LudoActivity
import com.bytemantis.snald.snaldgame.SnaldActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MenuActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var proListener: ListenerRegistration? = null
    private var isUserPro = false

    private lateinit var splashLayout: FrameLayout
    private lateinit var soundManager: SoundManager
    private lateinit var btnGoPro: Button
    private lateinit var btnLogout: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Auth Validation ---
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_menu)

        // Initialize Sound Manager for Menu Music
        soundManager = SoundManager(this)

        // Find Layouts and Buttons
        splashLayout = findViewById(R.id.layout_splash)
        val btnSnald = findViewById<Button>(R.id.btn_play_snald)
        val btnLudo = findViewById<Button>(R.id.btn_play_ludo)
        btnGoPro = findViewById(R.id.btn_go_pro)
        btnLogout = findViewById(R.id.btn_logout)

        // Set up click listeners for the games
        btnSnald.setOnClickListener {
            val intent = Intent(this, SnaldActivity::class.java)
            intent.putExtra("IS_PRO", isUserPro)
            startActivity(intent)
        }

        btnLudo.setOnClickListener {
            val intent = Intent(this, LudoActivity::class.java)
            intent.putExtra("IS_PRO", isUserPro)
            startActivity(intent)
        }

        btnGoPro.setOnClickListener {
            val intent = Intent(this, PurchaseActivity::class.java)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            goToLogin()
        }

        // --- Splash Screen & Music Logic ---
        splashLayout.visibility = View.VISIBLE
        soundManager.startMenuMusic()

        lifecycleScope.launch {
            delay(3000) // 3 Seconds Splash
            splashLayout.visibility = View.GONE
            // Start listening to pro status after splash is gone
            listenToProStatus()
        }
    }

    private fun listenToProStatus() {
        val email = auth.currentUser?.email ?: return
        val docRef = db.collection("users").document(email)

        proListener = docRef.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists()) {
                val isPro = snapshot.getBoolean("isPro") ?: false
                updateProButton(isPro)
            } else {
                // If user document doesn't exist, create it
                val userData = hashMapOf(
                    "email" to email,
                    "isPro" to false,
                    "registeredAt" to FieldValue.serverTimestamp()
                )
                docRef.set(userData)
                updateProButton(false)
            }
        }
    }

    private fun updateProButton(isPro: Boolean) {
        isUserPro = isPro
        if (isPro) {
            btnGoPro.text = "PRO ACTIVE 👑"
            btnGoPro.setTextColor(0xFF4CAF50.toInt()) // Green
        } else {
            btnGoPro.text = "GO PRO 👑"
            btnGoPro.setTextColor(0xFFFFD700.toInt()) // Gold
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        proListener?.remove()
    }

    // --- Lifecycle Audio Management ---
    override fun onPause() {
        super.onPause()
        soundManager.pauseMusic()
    }

    override fun onResume() {
        super.onResume()
        // Resume music only if the splash screen is gone and we are on the menu
        if (::splashLayout.isInitialized && splashLayout.visibility == View.GONE) {
            soundManager.resumeMusic()
        }
    }
}