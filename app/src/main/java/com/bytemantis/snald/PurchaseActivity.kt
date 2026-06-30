package com.bytemantis.snald

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot

class PurchaseActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var userEmail: String? = null
    private var requestListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null

    private lateinit var editUtr: EditText
    private lateinit var btnSubmitUtr: Button
    private lateinit var panelStatus: LinearLayout
    private lateinit var textStatusTitle: TextView
    private lateinit var textStatusDesc: TextView
    private lateinit var btnCopyUpi: Button
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        userEmail = auth.currentUser?.email

        if (userEmail == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        editUtr = findViewById(R.id.edit_utr_number)
        btnSubmitUtr = findViewById(R.id.btn_submit_utr)
        panelStatus = findViewById(R.id.panel_status)
        textStatusTitle = findViewById(R.id.text_status_title)
        textStatusDesc = findViewById(R.id.text_status_desc)
        btnCopyUpi = findViewById(R.id.btn_copy_upi)
        btnBack = findViewById(R.id.btn_back_to_menu)

        btnCopyUpi.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("UPI ID", "paytmqr2810050501011e876976d7ua@paytm")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "UPI ID copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnSubmitUtr.setOnClickListener {
            val utr = editUtr.text.toString().trim()
            if (utr.length != 12 || !utr.all { it.isDigit() }) {
                Toast.makeText(this, "UTR must be exactly 12 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            submitUtrRequest(utr)
        }

        btnBack.setOnClickListener {
            finish()
        }

        listenToPurchaseRequests()
        listenToProStatus()
    }

    private fun submitUtrRequest(utr: String) {
        btnSubmitUtr.isEnabled = false
        val email = userEmail ?: return

        val requestData = hashMapOf(
            "utrNumber" to utr,
            "email" to email,
            "status" to "PENDING",
            "requestedAt" to FieldValue.serverTimestamp()
        )

        // Write to Firestore under the UTR number as key
        db.collection("pro_requests").document(utr).set(requestData)
            .addOnSuccessListener {
                Toast.makeText(this, "Transaction submitted successfully!", Toast.LENGTH_SHORT).show()
                editUtr.text.clear()
            }
            .addOnFailureListener { e ->
                btnSubmitUtr.isEnabled = true
                Toast.makeText(this, "Failed to submit: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun listenToPurchaseRequests() {
        val email = userEmail ?: return
        // Fetch requests for this email address and listen to updates in real-time
        requestListener = db.collection("pro_requests")
            .whereEqualTo("email", email)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                updateStatusUi(snapshot)
            }
    }

    private fun listenToProStatus() {
        val email = userEmail ?: return
        userListener = db.collection("users").document(email)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val isPro = snapshot.getBoolean("isPro") ?: false
                if (isPro) {
                    Toast.makeText(this, "🎉 PRO Mode Unlocked!", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun updateStatusUi(snapshot: QuerySnapshot) {
        if (snapshot.isEmpty) {
            panelStatus.visibility = View.GONE
            btnSubmitUtr.isEnabled = true
            editUtr.isEnabled = true
            return
        }

        // Find the latest request in memory to avoid index requirements
        val latestDoc = snapshot.documents
            .filter { it.getTimestamp("requestedAt") != null }
            .maxByOrNull { it.getTimestamp("requestedAt")!! }
            ?: snapshot.documents.firstOrNull()

        if (latestDoc == null) {
            panelStatus.visibility = View.GONE
            btnSubmitUtr.isEnabled = true
            editUtr.isEnabled = true
            return
        }

        val status = latestDoc.getString("status") ?: "PENDING"
        val utr = latestDoc.getString("utrNumber") ?: ""

        panelStatus.visibility = View.VISIBLE

        when (status) {
            "PENDING" -> {
                textStatusTitle.text = "VERIFICATION PENDING"
                textStatusTitle.setTextColor(Color.parseColor("#FFD700")) // Yellow
                textStatusDesc.text = "Checking UTR $utr. Your PRO status will activate automatically once verified by the admin."
                btnSubmitUtr.isEnabled = false
                editUtr.isEnabled = false
            }
            "APPROVED" -> {
                textStatusTitle.text = "PRO MODE ACTIVE"
                textStatusTitle.setTextColor(Color.parseColor("#4CAF50")) // Green
                textStatusDesc.text = "Verified! Thank you for purchasing the Pro version."
                btnSubmitUtr.isEnabled = false
                editUtr.isEnabled = false
            }
            "REJECTED" -> {
                textStatusTitle.text = "TRANSACTION REJECTED"
                textStatusTitle.setTextColor(Color.parseColor("#F44336")) // Red
                textStatusDesc.text = "The UTR $utr could not be verified. Please double-check your receipt and submit the correct UTR."
                btnSubmitUtr.isEnabled = true
                editUtr.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        requestListener?.remove()
        userListener?.remove()
    }
}
