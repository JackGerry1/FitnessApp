package com.example.fitnessapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fitnessapp.constants.Constants.Companion.CALORIES_PER_STEP
import com.example.fitnessapp.constants.Constants.Companion.STEPS_TO_KM
import com.example.fitnessapp.databinding.ActivityStepsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class StepsActivity : AppCompatActivity(), SensorEventListener {

    // declare global variables for step counter, firebase and user
    private lateinit var binding: ActivityStepsBinding
    private var sensorManager: SensorManager? = null
    private var running = false
    private var isCounterRunning = true
    private var totalSteps = 0f
    private var currentSteps = 0
    private var previousTotalSteps = 0f
    private var daily_step_goal = ""
    private lateinit var stepsProgressBar: CircularProgressBar
    private lateinit var stepsGoalText: TextView
    private var db = FirebaseFirestore.getInstance()
    private var currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStepsBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // back button
        binding.imgBack.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }

        // start/stop button
        binding.btnToggleSteps.setBackgroundColor((Color.rgb(245, 20, 43)))
        binding.btnToggleSteps.setOnClickListener {
            toggleStepCounter()
        }
        // progress bar setup for goal steps
        stepsProgressBar = binding.stepsProgressBar
        stepsGoalText = binding.textTotalMax

        // upload steps to firebase code
        binding.btnUploadSteps.setBackgroundColor((Color.rgb(245, 20, 43)))
        binding.btnUploadSteps.setOnClickListener {
            if (currentSteps != 0) {
                val distanceInKm = (currentSteps * STEPS_TO_KM)
                val formattedDistanceInKM = "%.2f".format(distanceInKm)
                val caloriesBurned = (currentSteps * CALORIES_PER_STEP).roundToInt()
                uploadStepsToFirestore(currentSteps, caloriesBurned, formattedDistanceInKM)
            } else {
                Toast.makeText(this, "Steps count is zero.", Toast.LENGTH_SHORT).show()
            }
        }

        // Bottom navigation view item selection listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // if walk stay on the same page because you are already here
                R.id.bottom_steps -> true

                // if clicking on the stats navigate to the stats page
                R.id.bottom_steps_stats -> {
                    startActivity(Intent(applicationContext, StepsStatsActivity::class.java))
                    finish()
                    true
                }

                else -> false
            }
        }

        // functions for step counter
        fetchUserStepGoal()
        loadData()
        resetSteps()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // get users step count goal from db
    private fun fetchUserStepGoal() {

        // get the current user document
        val stepRef = db.collection("users").document(currentUser!!.uid)
        stepRef.get()
            .addOnSuccessListener { documentSnapshot ->
                // obtain step goal and reassign to the global variable
                val stepGoal = documentSnapshot.getString("daily_step_goal")
                daily_step_goal = stepGoal!!
                Log.d("daily step", daily_step_goal)

                // Set the progress_max value of the CircularProgressBar
                stepsProgressBar.progressMax = daily_step_goal.toFloat()
                stepsGoalText.text = "/$daily_step_goal"
            }
            // error if step goal cannot be obtained, this is unlikely
            .addOnFailureListener { e ->
                Log.e("Users Weight ERROR", e.toString())
            }
    }

    // upload step data to firebase
    private fun uploadStepsToFirestore(currentSteps: Int, caloriesBurned: Int, distanceInKm: String) {

        // get current user
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {

            // get the formatted date and other step stats
            val db = FirebaseFirestore.getInstance()
            val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val intStepGoal = daily_step_goal.toInt()
            val stepGoalReached = currentSteps >= intStepGoal
            val stepsData = hashMapOf(
                "steps" to currentSteps,
                "calories_burned" to caloriesBurned,
                "distance_in_km" to distanceInKm,
                "current_date" to currentDate,
                "step_goal_reached" to stepGoalReached
            )

            // upload data into steps > currentUser.uid > daily_steps > documentID (daily)
            // this update the same document if it is the same day or create a new one if it is a different day
            val dailyStepsRef = db.collection("steps")
                .document(currentUser.uid)
                .collection("daily_steps")
                .document(currentDate)

            // if upload successful
            dailyStepsRef.set(stepsData)
                .addOnSuccessListener {
                    Log.d("StepsActivity", "Steps data uploaded successfully.")
                    Toast.makeText(this, "Steps data uploaded successfully.", Toast.LENGTH_SHORT).show()
                }
                // if fails
                .addOnFailureListener { e ->
                    Log.w("StepsActivity", "Error uploading steps data", e)
                    Toast.makeText(this, "Error uploading steps data.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // check if the users has a step counter on their phone
    override fun onResume() {
        super.onResume()
        running = true
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if(stepSensor == null) {
            Toast.makeText(this, "No Step Sensor Found", Toast.LENGTH_SHORT).show()
        }
        else {
            Log.d("Steps Activity", "Sensor Detected")
            sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // update the step counter values when user takes steps
    override fun onSensorChanged(event: SensorEvent?) {
        if (running) {
            // stored total number of steps
            totalSteps = event!!.values[0]
            currentSteps = totalSteps.toInt() - previousTotalSteps.toInt()

            // assign steps to text displayed on screen
            binding.textStepsTaken.text = "$currentSteps"

            // Calculate and display distance
            val distanceInKm = currentSteps * STEPS_TO_KM
            val distanceTextView = findViewById<TextView>(R.id.textDistance)
            distanceTextView.text = String.format("%.2f KM", distanceInKm)

            // Calculate and display calories burned
            val caloriesBurned = (currentSteps * CALORIES_PER_STEP).roundToInt()
            val caloriesTextView = findViewById<TextView>(R.id.textCalories)
            caloriesTextView.text = "$caloriesBurned Calories Burned"

            // update the progress bar
            binding.stepsProgressBar.apply {
                setProgressWithAnimation(currentSteps.toFloat())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No implementation needed for step counter
    }

    // check if the user is running the step counter and allow them to toggle it
    private fun toggleStepCounter() {
        if (isCounterRunning) {
            running = false
            binding.btnToggleSteps.text = "Start"
        } else {
            running = true
            binding.btnToggleSteps.text = "Stop"
        }
        isCounterRunning = !isCounterRunning
    }

    // allow the user to reset their step counter on a long hold of the text
    private fun resetSteps() {
        binding.textStepsTaken.setOnClickListener {
            Toast.makeText(this, "Long Tap To Reset Steps", Toast.LENGTH_SHORT).show()
        }
        binding.textStepsTaken.setOnLongClickListener {

            Toast.makeText(this, "Succesfully Reset Steps", Toast.LENGTH_SHORT).show()
            previousTotalSteps = totalSteps
            binding.textStepsTaken.text = "0"
            binding.textCalories.text = "0 Calories Burned"
            binding.textDistance.text = "0 KM"
            saveData()

            true
        }
    }

    // save the data to local storage so it can be displayed quickly
    private fun saveData() {
        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat("key1", previousTotalSteps)
        editor.apply()
    }

    // load the data from local storage if there is any.
    private fun loadData() {
        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        previousTotalSteps = if (sharedPreferences.contains("key1")) {
            val savedNumber = sharedPreferences.getFloat("key1", 0f)
            savedNumber
        } else {
            0f
        }
    }
}