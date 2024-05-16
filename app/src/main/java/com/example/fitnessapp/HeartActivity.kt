package com.example.fitnessapp


import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
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
import java.nio.ByteBuffer
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executor

/*
References:

  Wetherell, J. (2013). android-heart-rate-monitor. [online]
  GitHub. Available at: https://github.com/phishman3579/android-heart-rate-monitor [Accessed 15 May 2024].

  Coding Reel (2022). Camera X Image Analysis Convert Realtime Preview to Grayscale in Java. [online]
  YouTube. Available at: https://www.youtube.com/watch?v=4vv2PtfdWRQ [Accessed 13 May 2024].

  Firebase (2024a). Add Data to Cloud Firestore.
  [online] Firebase. Available at: https://firebase.google.com/docs/firestore/manage-data/add-data#kotlin+ktx_2 [Accessed 19 Apr. 2024].
*/
class HeartActivity : AppCompatActivity(), ImageAnalysis.Analyzer {
    // global variables for binding and camerax
    private lateinit var binding: ActivityHeartBinding
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // global variables for timer, and heart rate tracking and calculation
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

    // global firebase current user
    private var currentUser = FirebaseAuth.getInstance().currentUser

    // enum to be able to switch between beat detected and not
    enum class TYPE {
        GREEN,
        RED
    }

    private var currentType = TYPE.GREEN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHeartBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // prepare camerax to get camera preview
        previewView = binding.previewView

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        binding.imgBack.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }

        // btn start heart
        binding.btnStartHeart.setBackgroundColor((Color.rgb(245, 20, 43)))
        binding.btnStartHeart.setOnClickListener {

            // if has permissions start timer and cameraX
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

    // function to start a 30s timer so that the heart rate measurment will end then
    private fun startTimer() {
        // Schedule a task to run after 30 seconds
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                // This code will run after 30 seconds
                runOnUiThread {
                    // save heart rate data and navigate to stats page
                    saveHeartRateData()
                    val intent = Intent(this@HeartActivity, HeartStatsActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }, 30 * 1000) // 30 seconds in milliseconds
    }

    // save heart rate to firestore
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
    // Cancel the timer when the activity is destroyed to prevent memory leaks
    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    // executor for the image analysis use case later
    private val executor: Executor
        get() = ContextCompat.getMainExecutor(this)

    // function to start the cameraX
    private fun startCameraX() {
        cameraProvider?.unbindAll()

        // setup the preview with the current view of the back lens
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

    // function to analyse the current preview image
    override fun analyze(image: ImageProxy) {

        // the below codes take the current image (format: YUV_420_888) and converts it into a byte array

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

        // Copy the U plane data
        val uBuffer = planes[1].buffer
        buffer.put(uBuffer)

        // Copy the V plane data
        val vBuffer = planes[2].buffer
        buffer.put(vBuffer)

        // Convert the byte buffer to a byte array
        val imageByteArray = buffer.array()

        // Process the imageByteArray to find average amount of red
        val imgAvg = ImageUtility.decodeYUV420SPtoRedAvg(imageByteArray, image.width, image.height)

        // Close the image proxy, this means it will move onto the next frame
        image.close()

        // pass every imgAvg frame To heartRateCounter for processing
        heartRateCounter(imgAvg)

    }

    // function to calculate heart rate
    private fun heartRateCounter(imgAvg: Int) {
        // display template message while heart rate being calculated
        binding.textHeartRate.visibility = View.VISIBLE

        // if image all white or black return because there is no point
        if (imgAvg == 0 || imgAvg == 255) {
            return
        }

        // store the amount of indices into an average array for further calculations
        var averageArrayAvg = 0
        var averageArrayCount = 0
        for (i in averageArray.indices) {
            if (averageArray[i] > 0) {
                averageArrayAvg += averageArray[i]
                averageArrayCount++
            }
        }

        // calculates the rolling average of all the previous frames
        val rollingAverage = if (averageArrayCount > 0) averageArrayAvg / averageArrayCount else 0

        // create newType variable so it can be switched when necessary
        var newType = currentType
        val heartImage = binding.imgHeartRateFull

        // add a heart beat
        if (imgAvg < rollingAverage) {
            newType = TYPE.RED
            if (newType != currentType) {
                beats++
                Log.d("BEAT!!", "BEAT!! beats=$beats")

                // change image to empty heart to symbol a heart beat
                heartImage.setImageResource(R.drawable.heart_empty)
            }
            // this will allow for cycling between the two images when a heart beat is detected
        } else if (imgAvg > rollingAverage) {
            newType = TYPE.GREEN
            heartImage.setImageResource(R.drawable.heart_white)
        }

        // calculate the average index of the imgAvg
        if (averageIndex == averageArraySize) averageIndex = 0
        averageArray[averageIndex] = imgAvg
        averageIndex++

        // Transitioned from one state to another to the same
        if (newType != currentType) {
            currentType = newType
        }

        // code to update the heart rate every 2 seconds
        val endTime = System.currentTimeMillis()
        val totalTimeInSecs = (endTime - startTime) / 1000.0
        if (totalTimeInSecs >= 2) {

            // calculate bps and dps
            val bps = beats / totalTimeInSecs
            val dpm = (bps * 60.0).toInt()

            // if dpm to low or high reset beats and startTime
            if (dpm < 30 || dpm > 180) {
                startTime = System.currentTimeMillis()
                beats = 0.0
                return
            }

            // else increment the beatsIndex and update beatsArray
            if (beatsIndex == beatsArraySize) beatsIndex = 0
            beatsArray[beatsIndex] = dpm
            beatsIndex++


            // store the beats array information so it can be used to calculate average beats per min
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
                // else display beat number
                binding.textHeartRate.text = "$beatsAvg BPM"
            }
            // reset everything so this process can start again
            startTime = System.currentTimeMillis()
            beats = 0.0
        }
    }

    // code to obtain permissions if they have not been granted already
    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle Permission granted/rejected
        var permissionGranted = true
        // check to make sure all permissions are granted
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
        }
        if (!permissionGranted) {
            Toast.makeText(
                baseContext, "Permission request denied", Toast.LENGTH_SHORT
            ).show()
        // if they have start the camera x function
        } else {
            startCameraX()
        }
    }

    // object to store needed permissions
    companion object {

        // try and find the camera permission and older devices try and find the write to external storage permission
        private val REQUIRED_PERMISSIONS = mutableListOf(
            android.Manifest.permission.CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        // check if the permissions have been granted true or false
        fun hasPermissions(context: Context) = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

}