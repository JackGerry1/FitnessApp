package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitnessapp.adapters.HeartAdapter
import com.example.fitnessapp.constants.HeartData
import com.example.fitnessapp.constants.StepsData
import com.example.fitnessapp.databinding.ActivityHeartStatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/*
References:

   Firebase (2024). Get data with Cloud Firestore. [online] Firebase.
   Available at: https://firebase.google.com/docs/firestore/query-data/get-data [Accessed 15 May 2024].

*/
class HeartStatsActivity : AppCompatActivity() {
    // global variables for heart rate, firebase and binding
    private lateinit var binding: ActivityHeartStatsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var heartDataList: MutableList<HeartData>
    private lateinit var heartAdapter: HeartAdapter
    private var currentUser = FirebaseAuth.getInstance().currentUser
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHeartStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // setup data for recycler view and setup the display for the recycler view
        heartDataList = mutableListOf()
        heartAdapter = HeartAdapter(heartDataList)
        binding.recyclerView.adapter = heartAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        // initialise firebase auth and firestore
        auth = Firebase.auth
        firestore = Firebase.firestore

        // highlight stats icon instead of the walk icon
        binding.bottomNavigation.selectedItemId = R.id.bottom_heart_stats


        // back button
        binding.imgBack.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Bottom navigation view item selection listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // if walk stay on the same page because you are already here
                R.id.bottom_heart_stats -> true

                // if clicking on the stats navigate to the stats page
                R.id.bottom_heart -> {
                    startActivity(Intent(applicationContext, HeartActivity::class.java))
                    finish()
                    true
                }

                else -> false
            }
        }
        fetchHeartRateData()
    }
    private fun fetchHeartRateData() {

        // reference to where the data is stored
        val stepRef = firestore.collection("heart").document(currentUser!!.uid).collection("heart_measurements")

        // display the data with the most recent walk being at the top
        val query = stepRef.orderBy("date_timestamp", Query.Direction.DESCENDING)

        // get all of the data from all of the documents
        query.get()
            .addOnSuccessListener { documents ->

                // clear data to make sure there is no duplicates
                heartDataList.clear()
                for (document in documents) {
                    val currentDate = document.getLong("date_timestamp")
                    val heartRate = document.getLong("heart_rate")

                    // add the data to the ActivityData data class
                    val heartData = HeartData(
                        dateTimestamp = currentDate,
                        heartRate = heartRate,
                    )
                    // add this the data to the list
                    heartDataList.add(heartData)
                }
                // notify the heart adapter has been changed so that it can update the data
                heartAdapter.notifyDataSetChanged()
            }
            // error if failure to obtain walk data, this is unlikely to happen
            .addOnFailureListener { exception ->
                Log.d("HeartStatsActivity", "Failed to get Heart documents: $exception")
            }
    }
}