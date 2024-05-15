package com.example.fitnessapp

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fitnessapp.constants.Constants
import com.example.fitnessapp.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.jar.Manifest

/*
References:

  Steibel, A. (2021). Is Your Android App Using Dark Mode against Your will? [online]
  Medium. Available at: https://audric-steibel.medium.com/is-your-android-app-using-dark-mode-against-your-will-fa8be22aaf24 [Accessed 7 May 2024].

*/

class MainActivity : AppCompatActivity() {

    // global variables for firebase auth and binding
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)

        navigateToWalkingActivityIfNeeded(intent)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportActionBar?.hide()

        // override device theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // firebase auth
        auth = Firebase.auth

        // signup and sign in buttons
        binding.btnSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        binding.btnSignIn.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
        }

        binding.btnSignIn.setBackgroundColor(Color.rgb(245, 20, 43))
        binding.btnSignUp.setBackgroundColor(Color.rgb(245, 20, 43))
        // loop to check if the user has logged in previously and closed the app
        Handler(Looper.getMainLooper()).postDelayed({

            // if the user has logged in previously redirected to the home activity
            val user = auth.currentUser
            if(user != null) {
                val intent = Intent(this, HomeActivity::class.java)
                startActivity(intent)
                finish()
            }
            // If the user is not signed in, just stay on MainActivity
        }, 3000)
    }
    private fun navigateToWalkingActivityIfNeeded(intent: Intent?) {
        if(intent?.action == Constants.ACTION_SHOW_TRACKING_FRAGMENT) {
            val walkingActivityIntent = Intent(this, WalkingActivity::class.java)
            startActivity(walkingActivityIntent)
            finish() // Optionally finish the MainActivity if needed
        }
    }
}