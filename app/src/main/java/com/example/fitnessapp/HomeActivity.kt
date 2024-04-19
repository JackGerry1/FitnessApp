package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.fitnessapp.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/*
References:
    Ghinna, R. (2020). Android : How to make circle image with Glide.
    [online] Medium. Available at: https://rizkaghina29.medium.com/android-how-to-make-circle-image-with-glide-bb0b50fbbda [Accessed 18 Apr. 2024].

*/
class HomeActivity : AppCompatActivity() {

    // global variables for firebase and binding
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

        // initialise firebase auth and firestore
        auth = Firebase.auth
        firestore = Firebase.firestore

        // profile picture image
        val imgReceivedProfilePictureActivity: ImageView = binding.imgReceivedProfilePicture

        // buttons for navigation to other pages and sign out
        binding.imgSignOut.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnCalories.setOnClickListener {
            val intent = Intent(this, CalorieActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnHeart.setOnClickListener {
            val intent = Intent(this, HeartActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnRun.setOnClickListener {
            val intent = Intent(this, RunningActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnWalk.setOnClickListener {
            val intent = Intent(this, WalkingActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnSteps.setOnClickListener {
            val intent = Intent(this, StepsActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnWorkout.setOnClickListener {
            val intent = Intent(this, WorkoutActivity::class.java)
            startActivity(intent)
            finish()
        }

        val currentUser = auth.currentUser

        // Get data from firestore about profile picture and username for current user
        val userRef = firestore.collection("users").document(currentUser!!.uid)
        userRef.get()
            .addOnSuccessListener { document ->
                // Document exists, update UI with user data
                val username = document.getString("username")
                val url = document.getString("photoURL")

                // Update UI with username
                binding.textViewUsername.text = username

                // load profile picture using a circle crop
                if (url != null) {
                    Glide.with(this)
                        .load(url)
                        .transform(CircleCrop())
                        .into(imgReceivedProfilePictureActivity)
                }
            }
            .addOnFailureListener { exception ->
                Log.d("HomeActivity", "Failed to get user document: $exception")
            }
    }
}