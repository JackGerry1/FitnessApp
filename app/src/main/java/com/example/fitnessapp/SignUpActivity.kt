package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fitnessapp.databinding.ActivitySignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivitySignUpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignUpBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportActionBar?.hide()

        auth = Firebase.auth
        val db = FirebaseFirestore.getInstance()


        binding.textViewLogin.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
        }

        binding.btnSignUp.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (checkAllFields()) {
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this) { authResult ->
                    if (authResult.isSuccessful) {
                        // Successfully created user account
                        val user = auth.currentUser
                        user?.let { currentUser ->
                            // Create a new document in "users" collection with the user's UID as the document ID
                            val userData = hashMapOf(
                                "email" to email
                                // You can add more user data here as needed
                            )
                            db.collection("users")
                                .document(currentUser.uid)
                                .set(userData)
                                .addOnSuccessListener {
                                    // Document creation successful
                                    Toast.makeText(this, "Account Created Successfully", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this, SignInActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    // Document creation failed
                                    Log.e("Firestore", "Error adding document", e)
                                    Toast.makeText(this, "Failed to create user document", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        // Account creation failed
                        Log.e("Auth", "Error creating user", authResult.exception)
                        Toast.makeText(this, "Account creation failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    private fun checkAllFields(): Boolean {
        val email = binding.etEmail.text.toString()
        if(binding.etEmail.text.toString() == "") {
            binding.textInputLayoutEmail.error = "This is a required field"
            return false
        }

        if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputLayoutEmail.error = "Invalid Email Format"
            return false
        }

        if(binding.etPassword.text.toString() == "") {
            binding.textInputLayoutPassword.error = "This is a required field"
            binding.textInputLayoutPassword.errorIconDrawable = null
            return false
        }
        if(binding.etPassword.length() <= 6) {
            binding.textInputLayoutPassword.error = "Password needs to be greater than 6 characters"
            binding.textInputLayoutPassword.errorIconDrawable = null
            return false
        }

        if(binding.etConfirmPassword.text.toString() == "") {
            binding.textInputLayoutConfirmPassword.error = "This is a required field"
            binding.textInputLayoutConfirmPassword.errorIconDrawable = null
            return false
        }

        if(binding.etPassword.text.toString() != binding.etConfirmPassword.text.toString()) {
            binding.textInputLayoutPassword.error = "Passwords Must Match"
            return false
        }

        return true
    }
}