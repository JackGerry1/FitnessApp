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
import com.example.fitnessapp.databinding.ActivityWalkingStatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class WalkingStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalkingStatsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalkingStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // initialise firebase auth and firestore
        auth = Firebase.auth
        firestore = Firebase.firestore

        val currentUser = auth.currentUser
        val imgWalk: ImageView = binding.imgWalk

        // highlight stats icon
        binding.bottomNavigation.selectedItemId = R.id.bottom_stats

        // Bottom navigation view item selection listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_stats -> true
                R.id.bottom_walk -> {
                    startActivity(Intent(applicationContext, WalkingActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }

        // Get data from firestore about walking stats for current user
        val walkingRef = firestore.collection("walking").document(currentUser!!.uid).collection("walks")
        walkingRef.get()
            .addOnSuccessListener { documents ->
                // Iterate through each document in the subcollection
                for (document in documents) {
                    // Access the data of each walk document
                    val dateTimestamp = document.getLong("date_timestamp")
                    val avgSpeed = document.getDouble("avg_speed")
                    val distanceInKM = document.getDouble("distance_in_KM")
                    val durationInMillis = document.getLong("duration_in_millis")
                    val caloriesBurned = document.getLong("calories_burned")
                    val imageUrl = document.getString("image_url")

                    // Log the fetched data for debugging
                    Log.d("WalkingStatsActivity", "Date Timestamp: ${dateTimestamp.toString()}")
                    Log.d("WalkingStatsActivity", "Average Speed: ${avgSpeed.toString()}")
                    Log.d("WalkingStatsActivity", "Distance in Meters: ${distanceInKM.toString()}")
                    Log.d("WalkingStatsActivity", "Duration in Millis: ${durationInMillis.toString()}")
                    Log.d("WalkingStatsActivity", "Calories Burned: ${caloriesBurned.toString()}")
                    Log.d("WalkingStatsActivity", "Image URL: ${imageUrl.toString()}")

                    // Handle the data as needed
                    // For example, you can aggregate the data, display it, or perform further processing
                    binding.textDate.text = "Date: ${dateTimestamp ?: "N/A"}"
                    binding.textAvgSpeed.text = "Average Speed: ${avgSpeed ?: "N/A"} km/h"
                    binding.textDistance.text = "Distance: ${distanceInKM ?: "N/A"} kilometers"
                    binding.textDuration.text = "Duration: ${durationInMillis ?: "N/A"} milliseconds"
                    binding.textCaloriesBurned.text = "Calories Burned: ${caloriesBurned ?: "N/A"}"

                    // load profile picture using a circle crop
                    if (imageUrl != null) {
                        Glide.with(this)
                            .load(imageUrl)
                            .into(imgWalk)
                    }

                }
            }
            .addOnFailureListener { exception ->
                Log.d("WalkingStatsActivity", "Failed to get walking documents: $exception")
            }
    }
}