package com.example.fitnessapp


import android.R.attr.text
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.fitnessapp.databinding.ActivityHeartBinding
import com.example.fitnessapp.utilties.ImageUtility
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executor


class HeartActivity : AppCompatActivity(), ImageAnalysis.Analyzer {
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var binding: ActivityHeartBinding
    private var timer: Timer? = null
    private var beatsIndex = 0
    private val beatsArraySize = 3
    private val beatsArray = IntArray(beatsArraySize)
    private var beats = 0.0
    private var startTime: Long = 0
    private var averageIndex = 0
    private val averageArraySize = 4
    private val averageArray = IntArray(averageArraySize)
    private var beatsAvg = 0
    private var currentUser = FirebaseAuth.getInstance().currentUser

    enum class TYPE {
        GREEN,
        RED
    }

    private var currentType = TYPE.GREEN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHeartBinding.inflate(layoutInflater)

        setContentView(binding.root)

        previewView = binding.previewView

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        binding.imgBack.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
        binding.btnStartHeart.setBackgroundColor((Color.rgb(245, 20, 43)))
        binding.btnStartHeart.setOnClickListener {
            if (hasPermissions(baseContext)) {
                cameraProviderFuture!!.addListener({
                    cameraProvider = cameraProviderFuture!!.get()
                    startCameraX()
                    startTimer()
                }, executor)
            } else {
                // Request permissions if not granted
                activityResultLauncher.launch(REQUIRED_PERMISSIONS)
            }
        }

        // Bottom navigation view item selection listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // if heart stay on the same page because you are already here
                R.id.bottom_heart -> true

                // if clicking on the stats navigate to the stats page
                R.id.bottom_heart_stats -> {
                    startActivity(Intent(applicationContext, HeartStatsActivity::class.java))
                    finish()
                    true
                }

                else -> false
            }
        }
    }

    private fun startTimer() {
        // Schedule a task to run after 30 seconds
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                // This code will run after 30 seconds
                runOnUiThread {
                    // Display toast indicating successful completion

                    saveHeartRateData()
                    val intent = Intent(this@HeartActivity, HeartStatsActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }, 30 * 1000) // 30 seconds in milliseconds
    }

    private fun saveHeartRateData() {
        val dateTimestamp = Calendar.getInstance().timeInMillis
        // set hashmap of stored data
        val heartRateData = hashMapOf(
            "date_timestamp" to dateTimestamp,
            "heart_rate" to beatsAvg
        )

        // the below code stores the data like this:
        /*
        * structure data like this
        * heart
        *   userUID
        *       heart_measurements
        *       documentID
        *               date_timestamp
        *               heart_rate
        * */
        currentUser?.uid?.let { userId ->

            // create initial collections and documentID
            val db = FirebaseFirestore.getInstance()
            val userDocument = db.collection("heart").document(userId)
            val documentId = dateTimestamp.toString()

            // create heart_measurements subcollection and various documentIDs
            userDocument
                .collection("heart_measurements")
                .document(documentId)
                .set(heartRateData)

                // succesfully uploaded data
                .addOnSuccessListener {
                    Toast.makeText(
                        applicationContext,
                        "Heart rate detection completed successfully and uploaded to database",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // catch any error uploading data
                .addOnFailureListener { e ->
                    Log.w("Failed Uploading heart rate Data", "Error adding document", e)
                    Toast.makeText(
                        applicationContext,
                        "Failed to save heart rate",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    override fun onDestroy() {
        // Cancel the timer when the activity is destroyed to prevent memory leaks
        timer?.cancel()
        super.onDestroy()
    }

    private val executor: Executor
        get() = ContextCompat.getMainExecutor(this)

    private fun startCameraX() {
        cameraProvider?.unbindAll()

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView!!.getSurfaceProvider())

        // Image analysis use case
        val imageAnalysis =
            ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        imageAnalysis.setAnalyzer(executor, this)

        // Bind to lifecycle
        val camera = cameraProvider?.bindToLifecycle(
            (this as LifecycleOwner), cameraSelector, preview, imageAnalysis
        )

        // Get an instance of CameraControl and enable the torch
        camera?.cameraControl?.enableTorch(true)
    }

    override fun analyze(image: ImageProxy) {

        // Get the planes
        val planes = image.planes

        // Calculate the buffer size
        val ySize = planes[0].buffer.remaining()
        val uSize = planes[1].buffer.remaining()
        val vSize = planes[2].buffer.remaining()
        val bufferSize = ySize + uSize + vSize

        // Initialize a byte buffer to hold the image data
        val buffer = ByteBuffer.allocate(bufferSize)

        // Copy the Y plane data
        val yBuffer = planes[0].buffer
        buffer.put(yBuffer)

        // Copy the U and V plane data
        val uBuffer = planes[1].buffer
        buffer.put(uBuffer)

        val vBuffer = planes[2].buffer
        buffer.put(vBuffer)

        // Convert the byte buffer to a byte array
        val imageByteArray = buffer.array()

        // Process the imageByteArray as needed
        val imgAvg = ImageUtility.decodeYUV420SPtoRedAvg(imageByteArray, image.width, image.height)

        // Close the image proxy
        image.close()

        heartRateCounter(imgAvg)

    }

    private fun heartRateCounter(imgAvg: Int) {
        binding.textHeartRate.visibility = View.VISIBLE
        if (imgAvg == 0 || imgAvg == 255) {
            return
        }

        var averageArrayAvg = 0
        var averageArrayCount = 0
        for (i in averageArray.indices) {
            if (averageArray[i] > 0) {
                averageArrayAvg += averageArray[i]
                averageArrayCount++
            }
        }

        val rollingAverage = if (averageArrayCount > 0) averageArrayAvg / averageArrayCount else 0


        var newType = currentType
        val heartImage = binding.imgHeartRateFull
        if (imgAvg < rollingAverage) {
            newType = TYPE.RED
            if (newType != currentType) {
                beats++
                Log.d("BEAT!!", "BEAT!! beats=$beats")
                heartImage.setImageResource(R.drawable.heart_empty)
            }
        } else if (imgAvg > rollingAverage) {
            newType = TYPE.GREEN
            heartImage.setImageResource(R.drawable.heart_white)
        }

        if (averageIndex == averageArraySize) averageIndex = 0
        averageArray[averageIndex] = imgAvg
        averageIndex++

        // Transitioned from one state to another to the same
        if (newType != currentType) {
            currentType = newType
        }

        val endTime = System.currentTimeMillis()
        val totalTimeInSecs = (endTime - startTime) / 1000.0
        if (totalTimeInSecs >= 2) {
            val bps = beats / totalTimeInSecs
            val dpm = (bps * 60.0).toInt()
            if (dpm < 30 || dpm > 180) {
                startTime = System.currentTimeMillis()
                beats = 0.0
                return
            }

            if (beatsIndex == beatsArraySize) beatsIndex = 0
            beatsArray[beatsIndex] = dpm
            beatsIndex++

            var beatsArrayAvg = 0
            var beatsArrayCount = 0
            for (i in beatsArray.indices) {
                if (beatsArray[i] > 0) {
                    beatsArrayAvg += beatsArray[i]
                    beatsArrayCount++
                }
            }
            beatsAvg = beatsArrayAvg / beatsArrayCount
            // Display message if beatsAvg is zero
            if (beatsAvg == 0) {
                binding.textHeartRate.text = "Please wait for heart rate measurement"
            } else {
                binding.textHeartRate.text = "$beatsAvg BPM"
            }
            startTime = System.currentTimeMillis()
            beats = 0.0
        }
    }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle Permission granted/rejected
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
        }
        if (!permissionGranted) {
            Toast.makeText(
                baseContext, "Permission request denied", Toast.LENGTH_SHORT
            ).show()
        } else {
            startCameraX()
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = mutableListOf(
            android.Manifest.permission.CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()


        fun hasPermissions(context: Context) = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

}