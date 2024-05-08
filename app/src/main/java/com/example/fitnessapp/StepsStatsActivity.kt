package com.example.fitnessapp

import StepAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitnessapp.adapters.ActivityAdapter
import com.example.fitnessapp.constants.ActivityData
import com.example.fitnessapp.constants.StepsData
import com.example.fitnessapp.databinding.ActivityStepsStatsBinding
import com.example.fitnessapp.databinding.ActivityWalkingStatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class StepsStatsActivity : AppCompatActivity() {
    // global variables for binding, firebase, currentUser.uid and walk Recycler View
    private lateinit var binding: ActivityStepsStatsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var stepDataList: MutableList<StepsData>
    private lateinit var stepAdapter: StepAdapter
    private var currentUser = FirebaseAuth.getInstance().currentUser
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStepsStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // setup data for recycler view and setup the display for the recycler view
        stepDataList = mutableListOf()
        stepAdapter = StepAdapter(stepDataList)
        binding.recyclerView.adapter = stepAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        // initialise firebase auth and firestore
        auth = Firebase.auth
        firestore = Firebase.firestore

        // highlight stats icon instead of the walk icon
        binding.bottomNavigation.selectedItemId = R.id.bottom_steps_stats

        // Bottom navigation view item selection listener
        // if click stats, do nothing you are already on that page
        // if click steps go back to steps activity
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_steps_stats -> true
                R.id.bottom_steps -> {
                    startActivity(Intent(applicationContext, StepsActivity::class.java))
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

        fetchStepData()

    }
    private fun fetchStepData() {

        // reference to where the data is stored
        val stepRef = firestore.collection("steps").document(currentUser!!.uid).collection("daily_steps")

        // display the data with the most recent walk being at the top
        val query = stepRef.orderBy("current_date", Query.Direction.DESCENDING)

        // get all of the data from all of the documents
        query.get()
            .addOnSuccessListener { documents ->

                // clear data to make sure there is no duplicates
                stepDataList.clear()
                for (document in documents) {
                    val currentDate = document.getString("current_date")
                    val steps = document.getLong("steps")
                    val distanceInKM = document.getString("distance_in_km")
                    val caloriesBurned = document.getLong("calories_burned")
                    val goalReached = document.getBoolean("step_goal_reached")

                    // add the data to the ActivityData data class
                    val stepData = StepsData(
                        currentDate = currentDate,
                        steps = steps,
                        distanceInKM = distanceInKM,
                        caloriesBurned = caloriesBurned,
                        goalReached = goalReached
                    )
                    // add this the data to the list
                    stepDataList.add(stepData)
                }
                // notify the walk adapter has been changed so that it can update the data
                stepAdapter.notifyDataSetChanged()
            }
            // error if failure to obtain walk data, this is unlikely to happen
            .addOnFailureListener { exception ->
                Log.d("StepsStatsActivity", "Failed to get Step documents: $exception")
            }
    }
    // navigate users to the homepage
    private fun goToHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}