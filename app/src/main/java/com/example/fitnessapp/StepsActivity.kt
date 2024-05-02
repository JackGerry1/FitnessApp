package com.example.fitnessapp

import android.content.Context
import android.content.Intent
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class StepsActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var binding: ActivityStepsBinding
    private var sensorManager: SensorManager? = null
    private var running = false
    private var isCounterRunning = true
    private var totalSteps = 0f
    private var currentSteps = 0
    private var previousTotalSteps = 0f
    private var lastResetDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStepsBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.imgBack.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnToggleSteps.setOnClickListener {
            toggleStepCounter()
        }

        binding.btnUploadSteps.setOnClickListener {
            if (currentSteps != 0) {
                val distanceInKm = (currentSteps * STEPS_TO_KM)
                val caloriesBurned = (currentSteps * CALORIES_PER_STEP).roundToInt()
                uploadStepsToFirestore(currentSteps, caloriesBurned, distanceInKm)
            } else {
                Toast.makeText(this, "Steps count is zero.", Toast.LENGTH_SHORT).show()
            }
        }

        loadData()
        resetSteps()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private fun uploadStepsToFirestore(currentSteps: Int, caloriesBurned: Int, distanceInKm: Double) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val db = FirebaseFirestore.getInstance()
            val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

            val stepsData = hashMapOf(
                "steps" to currentSteps,
                "calories_burned" to caloriesBurned,
                "distance_in_km" to distanceInKm
            )

            val dailyStepsRef = db.collection("steps")
                .document(currentUser.uid)
                .collection("daily_steps")
                .document(currentDate)

            dailyStepsRef.set(stepsData)
                .addOnSuccessListener {
                    Log.d("StepsActivity", "Steps data uploaded successfully.")
                    Toast.makeText(this, "Steps data uploaded successfully.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.w("StepsActivity", "Error uploading steps data", e)
                    Toast.makeText(this, "Error uploading steps data.", Toast.LENGTH_SHORT).show()
                }
        }
    }

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

    override fun onSensorChanged(event: SensorEvent?) {
        if (running) {
            totalSteps = event!!.values[0]
            currentSteps = totalSteps.toInt() - previousTotalSteps.toInt()
            binding.textStepsTaken.text = "$currentSteps"

            // Calculate and display distance
            val distanceInKm = currentSteps * STEPS_TO_KM
            val distanceTextView = findViewById<TextView>(R.id.textDistance)
            distanceTextView.text = String.format("%.2f KM", distanceInKm)

            // Calculate and display calories burned
            val caloriesBurned = (currentSteps * CALORIES_PER_STEP).roundToInt()
            val caloriesTextView = findViewById<TextView>(R.id.textCalories)
            caloriesTextView.text = "$caloriesBurned Calories Burned"

            binding.stepsProgressBar.apply {
                setProgressWithAnimation(currentSteps.toFloat())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No implementation needed for step counter
    }

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

    private fun saveData() {
        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat("key1", previousTotalSteps)
        editor.apply()
    }

    private fun loadData() {
        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        previousTotalSteps = sharedPreferences.getFloat("key1", 0f)
        lastResetDate = sharedPreferences.getString("lastResetDate", null)

        if (!isSameDay(lastResetDate)) {
            // Reset steps if it's a new day
            previousTotalSteps = 0f
            saveData()
        }
    }
    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun isSameDay(dateString: String?): Boolean {
        if (dateString.isNullOrEmpty()) return false
        val currentDate = getCurrentDate()
        return currentDate == dateString
    }
}