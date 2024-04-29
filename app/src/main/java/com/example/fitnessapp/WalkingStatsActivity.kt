package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitnessapp.adapters.ActivityAdapter
import com.example.fitnessapp.constants.ActivityData
import com.example.fitnessapp.databinding.ActivityWalkingStatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class WalkingStatsActivity : AppCompatActivity() {

    // global variables for binding, firebase, currentUser.uid and walk Recycler View
    private lateinit var binding: ActivityWalkingStatsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var walkDataList: MutableList<ActivityData>
    private lateinit var walkAdapter: ActivityAdapter
    private var currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalkingStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // setup data for recycler view and setup the display for the recycler view
        walkDataList = mutableListOf()
        walkAdapter = ActivityAdapter(walkDataList)
        binding.recyclerView.adapter = walkAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        // initialise firebase auth and firestore
        auth = Firebase.auth
        firestore = Firebase.firestore

        // highlight stats icon instead of the walk icon
        binding.bottomNavigation.selectedItemId = R.id.bottom_walk_stats

        // Bottom navigation view item selection listener
        // if click stats, do nothing you are already on that page
        // if click walk go back to walk activity
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_walk_stats -> true
                R.id.bottom_walk -> {
                    startActivity(Intent(applicationContext, WalkingActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }

        // button to take you back to homeactivity
        binding.imgBack.setOnClickListener {
            goToHomeActivity()
        }

        // Fetch walk data from Firestore based on the current user account
        fetchWalkData()
    }

    // navigate users to the homepage
    private fun goToHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    // obtain the current users walk data from firestore
    private fun fetchWalkData() {

        // reference to where the data is stored
        val walkingRef = firestore.collection("walking").document(currentUser!!.uid).collection("walks")

        // display the data with the most recent walk being at the top
        val query = walkingRef.orderBy("date_timestamp", Query.Direction.DESCENDING)

        // get all of the data from all of the documents
        query.get()
            .addOnSuccessListener { documents ->

                // clear data to make sure there is no duplicates
                walkDataList.clear()
                for (document in documents) {
                    val dateTimestamp = document.getLong("date_timestamp")
                    val avgSpeed = document.getString("avg_speed")
                    val distanceInKM = document.getString("distance_in_KM")
                    val durationInMillis = document.getLong("duration_in_millis")
                    val caloriesBurned = document.getLong("calories_burned")
                    val imageUrl = document.getString("image_url")

                    // add the data to the ActivityData data class
                    val walkData = ActivityData(
                        dateTimestamp = dateTimestamp,
                        avgSpeed = avgSpeed,
                        distanceInKM = distanceInKM,
                        durationInMillis = durationInMillis,
                        caloriesBurned = caloriesBurned,
                        imageUrl = imageUrl
                    )
                    // add this the data to the list
                    walkDataList.add(walkData)
                }
                // notify the walk adapter has been changed so that it can update the data
                walkAdapter.notifyDataSetChanged()
            }
            // error if failure to obtain walk data, this is unlikely to happen
            .addOnFailureListener { exception ->
                Log.d("WalkingStatsActivity", "Failed to get walking documents: $exception")
            }
    }

}