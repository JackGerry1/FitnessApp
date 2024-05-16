package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitnessapp.adapters.ActivityAdapter
import com.example.fitnessapp.constants.ActivityData
import com.example.fitnessapp.databinding.ActivityRunningStatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/*
References:

   Firebase (2024). Get data with Cloud Firestore. [online] Firebase.
   Available at: https://firebase.google.com/docs/firestore/query-data/get-data [Accessed 29 April 2024].

   CodingTutorials (2022). Recycler View in Android Studio | Populate Recycler View with Firebase Database (with Source Code). [online] Youtube.
   Available at: https://www.youtube.com/watch?v=p2KmuAO8YsE [Accessed 29 April 2024].
*/

class RunningStatsActivity : AppCompatActivity() {

    // global variables for binding, firebase, currentUser, runDataList and runAdapter
    private lateinit var binding: ActivityRunningStatsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var runDataList: MutableList<ActivityData>
    private lateinit var runAdapter: ActivityAdapter
    private var currentUser = FirebaseAuth.getInstance().currentUser
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRunningStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // setup data for recycler view and setup the display for the recycler view
        runDataList = mutableListOf()
        runAdapter = ActivityAdapter(runDataList)
        binding.recyclerView.adapter = runAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)


        // initialise firebase auth and firestore
        auth = Firebase.auth
        firestore = Firebase.firestore

        // highlight stats icon
        binding.bottomNavigation.selectedItemId = R.id.bottom_run_stats

        // Bottom navigation view item selection listener
        // if click stats, do nothing you are already on that page
        // if click walk go back to walk activity
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_run_stats -> true
                R.id.bottom_run -> {
                    startActivity(Intent(applicationContext, RunningActivity::class.java))
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

        // Fetch run data from Firestore based on the current user account
        fetchRunData()
    }

    // obtain the current users walk data from firestore
    private fun fetchRunData() {

        // reference to where the run data is stored
        val runningRef = firestore.collection("running").document(currentUser!!.uid).collection("runs")

        // display the data with the most recent run being at the top
        val query = runningRef.orderBy("date_timestamp", Query.Direction.DESCENDING)

        // get all of the data from all of the documents
        query.get()
            .addOnSuccessListener { documents ->

                // clear data to make sure there is no duplicates
                runDataList.clear()
                for (document in documents) {
                    val dateTimestamp = document.getLong("date_timestamp")
                    val avgSpeed = document.getString("avg_speed")
                    val distanceInKM = document.getString("distance_in_KM")
                    val durationInMillis = document.getLong("duration_in_millis")
                    val caloriesBurned = document.getLong("calories_burned")
                    val imageUrl = document.getString("image_url")

                    // add the data to the ActivityData data class
                    val runData = ActivityData(
                        dateTimestamp = dateTimestamp,
                        avgSpeed = avgSpeed,
                        distanceInKM = distanceInKM,
                        durationInMillis = durationInMillis,
                        caloriesBurned = caloriesBurned,
                        imageUrl = imageUrl
                    )
                    // add this the data to the list
                    runDataList.add(runData)
                }
                // notify the run adapter has been changed so that it can update the data
                runAdapter.notifyDataSetChanged()
            }
            // error if failure to obtain walk data, this is unlikely to happen
            .addOnFailureListener { exception ->
                Log.d("RunningStatsActivity", "Failed to get running documents: $exception")
            }
    }

    // navigate users to the homepage
    private fun goToHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}