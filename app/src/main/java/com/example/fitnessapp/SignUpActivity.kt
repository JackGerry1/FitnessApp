package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.Spinner
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


/*
References:

    Firebase (2024a). Add Data to Cloud Firestore.
    [online] Firebase. Available at: https://firebase.google.com/docs/firestore/manage-data/add-data#kotlin+ktx_2 [Accessed 18 Apr. 2024].

*/

class SignUpActivity : AppCompatActivity() {

    // globals for firebase auth and binding
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

        // auth and firestore
        auth = Firebase.auth
        val db = FirebaseFirestore.getInstance()


        // Set up spinner for sex selection
        val sexOptions = arrayOf("Male", "Female")
        val spinner: Spinner = findViewById(R.id.spinnerSex)
        val adapter: ArrayAdapter<String> = ArrayAdapter(this, android.R.layout.simple_spinner_item, sexOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // link to sign in page
        binding.textViewLogin.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
        }

        // signup functionality
        binding.btnSignUp.setOnClickListener {

            // values for all fields
            val username = binding.etUsername.text.toString()
            val email = binding.etEmail.text.toString()
            val age = binding.etAge.text.toString()
            val sex = sexOptions[spinner.selectedItemPosition]
            val height = binding.etHeight.text.toString()
            val weight = binding.etWeight.text.toString()
            val daily_step_goal = binding.etSteps.text.toString()
            val password = binding.etPassword.text.toString()

            // if all fields are filled
            if (checkAllFields()) {

                // create a user with email and password entered in
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this) { authResult ->
                    if (authResult.isSuccessful) {
                        // Successfully created user account
                        val user = auth.currentUser
                        user?.let { currentUser ->
                            // Create a new document in "users" collection with the user's UID as the document ID
                            val userData = hashMapOf(
                                "uid" to currentUser.uid,
                                "username" to username,
                                "email" to email,
                                "age" to age,
                                "sex" to sex,
                                "height" to height,
                                "weight" to weight,
                                "daily_step_goal" to daily_step_goal
                            )

                            // create a users collection and fill in the user data from the hashmap
                            db.collection("users")
                                .document(currentUser.uid)
                                .set(userData)
                                .addOnSuccessListener {
                                    // Document creation successful and navigate to profile picture activity
                                    Toast.makeText(this, "Account Created Successfully", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this, ProfilePictureActivity::class.java)
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
    // function to check if all fields are filled
    private fun checkAllFields(): Boolean {
        val email = binding.etEmail.text.toString()

        if(binding.etUsername.text.toString() == "") {
            binding.textInputLayoutUsername.error = "This is a required field"
            return false
        }

        if(binding.etEmail.text.toString() == "") {
            binding.textInputLayoutEmail.error = "This is a required field"
            return false
        }

        if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputLayoutEmail.error = "Invalid Email Format"
            return false
        }

        if(binding.etAge.text.toString() == "") {
            binding.textInputLayoutAge.error = "This is a required field"
            return false
        }

        if(binding.etHeight.text.toString() == "") {
            binding.textInputLayoutHeight.error = "This is a required field"
            return false
        }

        if(binding.etWeight.text.toString() == "") {
            binding.textInputLayoutWeight.error = "This is a required field"
            return false
        }

        if(binding.etSteps.text.toString() == "") {
            binding.textInputLayoutWeight.error = "This is a required field"
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