package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fitnessapp.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var binding: ActivityHomeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportActionBar?.hide()

        auth = Firebase.auth
        firestore = Firebase.firestore

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)

            finish()
        }

        val currentUser = auth.currentUser

        // Get user document from Firestore
        val userRef = firestore.collection("users").document(currentUser!!.uid)
        userRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Document exists, update UI with user data
                    val username = document.getString("username")
                    val email = document.getString("email")
                    val age = document.getString("age")
                    val sex = document.getString("sex")
                    val height = document.getString("height")
                    val weight = document.getString("weight")

                    // Update UI with user data
                    binding.textViewUsername.text = "Username: $username"
                    binding.textViewEmail.text = "Email: $email"
                    binding.textViewAge.text = "Age: $age"
                    binding.textViewSex.text = "Sex: $sex"
                    binding.textViewHeight.text = "Height: $height"
                    binding.textViewWeight.text = "Weight: $weight"

                } else {
                    Log.d("HomeActivity", "User document not found")
                }
            }
            .addOnFailureListener { exception ->
                Log.d("HomeActivity", "Failed to get user document: $exception")
            }
    }
}