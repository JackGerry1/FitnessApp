package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.fitnessapp.adapters.WalkAdapter
import com.example.fitnessapp.constants.WalkData
import com.example.fitnessapp.databinding.ActivityWalkingStatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class WalkingStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalkingStatsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var walkDataList: MutableList<WalkData>
    private lateinit var walkAdapter: WalkAdapter
    private var currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalkingStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        walkDataList = mutableListOf()
        walkAdapter = WalkAdapter(walkDataList)
        binding.recyclerView.adapter = walkAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)



        // initialise firebase auth and firestore
        auth = Firebase.auth
        firestore = Firebase.firestore



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

        binding.imgBack.setOnClickListener {
            goToHomeActivity()
        }

        // Fetch walk data from Firestore
        fetchWalkData()
    }

    private fun goToHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun fetchWalkData() {
        val walkingRef = firestore.collection("walking").document(currentUser!!.uid).collection("walks")
        val query = walkingRef.orderBy("date_timestamp", Query.Direction.DESCENDING)

        query.get()
            .addOnSuccessListener { documents ->
                walkDataList.clear()
                for (document in documents) {
                    val dateTimestamp = document.getLong("date_timestamp")
                    val avgSpeed = document.getString("avg_speed")
                    val distanceInKM = document.getString("distance_in_KM")
                    val durationInMillis = document.getLong("duration_in_millis")
                    val caloriesBurned = document.getLong("calories_burned")
                    val imageUrl = document.getString("image_url")

                    val walkData = WalkData(
                        dateTimestamp = dateTimestamp,
                        avgSpeed = avgSpeed,
                        distanceInKM = distanceInKM,
                        durationInMillis = durationInMillis,
                        caloriesBurned = caloriesBurned,
                        imageUrl = imageUrl
                    )
                    walkDataList.add(walkData)
                }
                walkAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.d("WalkingStatsActivity", "Failed to get walking documents: $exception")
            }
    }

}