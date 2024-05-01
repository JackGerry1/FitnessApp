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
import kotlin.math.roundToInt

class StepsActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var binding: ActivityStepsBinding
    private var sensorManager: SensorManager? = null
    private var running = false
    private var totalSteps = 0f
    private var previousTotalSteps = 0f

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
        loadData()
        resetSteps()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
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
            val currentSteps = totalSteps.toInt() - previousTotalSteps.toInt()
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
        previousTotalSteps = if (sharedPreferences.contains("key1")) {
            val savedNumber = sharedPreferences.getFloat("key1", 0f)
            savedNumber
        } else {
            0f
        }
        Log.d("Steps Activity", "Previous total steps: $previousTotalSteps")
    }

}