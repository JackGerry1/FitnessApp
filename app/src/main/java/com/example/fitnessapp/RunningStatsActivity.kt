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

class RunningStatsActivity : AppCompatActivity() {
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

        binding.imgBack.setOnClickListener {
            goToHomeActivity()
        }


        // Fetch run data from Firestore
        fetchRunData()
    }

    private fun fetchRunData() {
        val runningRef = firestore.collection("running").document(currentUser!!.uid).collection("runs")
        val query = runningRef.orderBy("date_timestamp", Query.Direction.DESCENDING)

        query.get()
            .addOnSuccessListener { documents ->
                runDataList.clear()
                for (document in documents) {
                    val dateTimestamp = document.getLong("date_timestamp")
                    val avgSpeed = document.getString("avg_speed")
                    val distanceInKM = document.getString("distance_in_KM")
                    val durationInMillis = document.getLong("duration_in_millis")
                    val caloriesBurned = document.getLong("calories_burned")
                    val imageUrl = document.getString("image_url")

                    val runData = ActivityData(
                        dateTimestamp = dateTimestamp,
                        avgSpeed = avgSpeed,
                        distanceInKM = distanceInKM,
                        durationInMillis = durationInMillis,
                        caloriesBurned = caloriesBurned,
                        imageUrl = imageUrl
                    )
                    runDataList.add(runData)
                }
                runAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.d("RunningStatsActivity", "Failed to get running documents: $exception")
            }
    }

    private fun goToHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}