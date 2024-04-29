package com.example.fitnessapp

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fitnessapp.constants.Constants
import com.example.fitnessapp.databinding.ActivityCalorieBinding
import com.example.fitnessapp.databinding.ActivityRunningBinding
import com.example.fitnessapp.databinding.ActivityWalkingBinding
import com.google.android.gms.maps.GoogleMap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.example.fitnessapp.services.Polyline
import com.example.fitnessapp.services.Polylines
import com.example.fitnessapp.services.TrackingService
import com.example.fitnessapp.utilties.TrackingUtility
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

class RunningActivity : AppCompatActivity(), OnMapReadyCallback {

    // global variables for binding, google maps, firebase
    private lateinit var binding: ActivityRunningBinding
    private lateinit var mMap: GoogleMap
    private lateinit var auth: FirebaseAuth
    private var storageRef = Firebase.storage
    private var db = FirebaseFirestore.getInstance()
    private var currentUser = FirebaseAuth.getInstance().currentUser

    // global variables for tracking state, pathPoints, time, user weight, and bottom navigation menu
    private var isTracking = false
    private var pathPoints = mutableListOf<Polyline>()
    private var currentTimeMillis = 0L
    private var weight = 0f
    private var menu: Menu? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // if your device has the a recent api version request the POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                0
            )
        }

        binding = ActivityRunningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


        // initiliase firebase auth and cloud storage
        auth = Firebase.auth
        storageRef = FirebaseStorage.getInstance()

        fetchUserWeight()

        // button to get back to homepage
        binding.imgBack.setOnClickListener {
            goToHomeActivity()
        }

        // button to start/stop a run
        binding.btnToggleRun.setOnClickListener {
            toggleRun()
        }

        // button to finish the run
        binding.btnFinishRun.setOnClickListener {
            zoomToSeeWholeTrack()
            endRunAndSaveToDb()
        }

        // Bottom navigation view item selection listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // if run stay on the same page because you are already here
                R.id.bottom_run -> true
                // if clicking on the stats navigate to the stats page for the runs
                R.id.bottom_run_stats -> {
                    startActivity(Intent(applicationContext, RunningStatsActivity::class.java))
                    finish()
                    true
                }

                else -> false
            }
        }
    }

    // obtain the current users weight from firestore
    private fun fetchUserWeight() {

        // get the current user document
        val weightRef = db.collection("users").document(currentUser!!.uid)
        weightRef.get()
            .addOnSuccessListener { documentSnapshot ->
                // obtain weight and reassign to the global variable
                val userWeight = documentSnapshot.getString("weight")
                weight = userWeight!!.toFloat()

            }
            // error if weight cannot be obtained, this is unlikely
            .addOnFailureListener { e ->
                Log.e("Users Weight ERROR", e.toString())
            }
    }

    private fun endRunAndSaveToDb() {
        mMap.snapshot { bmp ->
            // Save the snapshot to a temporary file
            val imageFile = createImageFile()
            val outputStream = FileOutputStream(imageFile)
            bmp?.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()

            // Upload the image file to Firebase Cloud Storage
            val storageRef = FirebaseStorage.getInstance().reference
            val imageRef =
                storageRef.child("running_images/${currentUser!!.uid}/${System.currentTimeMillis()}.png")
            val uploadTask = imageRef.putFile(Uri.fromFile(imageFile))

            // Retrieve the download URL of the uploaded image
            uploadTask.continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let {
                        throw it
                    }
                }
                imageRef.downloadUrl
            }.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val downloadUri = task.result

                    // Save run data along with image URL to Firestore
                    saveRunDataToFirestore(downloadUri.toString())
                } else {
                    // Handle failure
                    Log.e("endRunAndSaveToDb ERROR", "Upload failed: ${task.exception}")
                }
            }


        }
    }

    // save the run data to the database
    private fun saveRunDataToFirestore(imageUrl: String) {
        // get run stats using helper functions
        val distanceInMeters = calculateTotalDistance()
        val avgSpeed = calculateAverageSpeed(distanceInMeters)
        val dateTimestamp = Calendar.getInstance().timeInMillis
        val caloriesBurned = calculateCaloriesBurnedRunning(distanceInMeters, weight, avgSpeed)


        // Format the data to two decimal places
        val formattedAvgSpeed = "%.2f".format(avgSpeed)
        val formattedDistanceInKM =
            "%.2f".format(distanceInMeters / 1000.0) // Assuming distanceInMeters is in meters

        // set hashmap of stored data
        val runData = hashMapOf(
            "date_timestamp" to dateTimestamp,
            "avg_speed" to formattedAvgSpeed,
            "distance_in_KM" to formattedDistanceInKM,
            "duration_in_millis" to currentTimeMillis,
            "calories_burned" to caloriesBurned,
            "image_url" to imageUrl // Add the image URL to the walk data
        )


        // the below code stores the data like this:
        /*
        * structure data like this
        * running
        *   userUID
        *       runs
        *       documentID
        *               date_timestamp
        *               avg_speed
        *               distance_in_KM
        *               duration_in_millis
        *               calories_burned
        *               image_url
        * */
        currentUser?.uid?.let { userId ->

            // create initial collections and documentID
            val db = FirebaseFirestore.getInstance()
            val userDocument = db.collection("running").document(userId)
            val documentId = dateTimestamp.toString() // Use date timestamp as document ID

            // create runs subcollection and various documentIDs
            userDocument
                .collection("runs")
                .document(documentId) // Set document ID
                .set(runData)

                // succesfully uploaded data
                .addOnSuccessListener {
                    Toast.makeText(
                        applicationContext,
                        "Run saved successfully",
                        Toast.LENGTH_LONG
                    ).show()
                    finishRun()
                }
                // catch any error uploading data
                .addOnFailureListener { e ->
                    Log.w("Failed Uploading Run Data", "Error adding document", e)
                    Toast.makeText(
                        applicationContext,
                        "Failed to save Run",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    // helper function to calculate total distance
    private fun calculateTotalDistance(): Float {
        var distanceInMeters = 0f

        // loop through each polyline and adds it length using the tracking utility and return the result
        for (polyline in pathPoints) {
            distanceInMeters += TrackingUtility.calculatePolylineLength(polyline)
        }
        return distanceInMeters
    }

    // calculate average speed in KM/H
    private fun calculateAverageSpeed(distanceInMeters: Float): Float {
        val distanceInKilometers = distanceInMeters / 1000f

        // convert milliseconds to hours
        val timeInHours = currentTimeMillis / 1000f / 60 / 60
        return distanceInKilometers / timeInHours
    }

    // estimate calories burned running
    private fun calculateCaloriesBurnedRunning(
        distanceInMeters: Float,
        weight: Float,
        avgSpeed: Float
    ): Int {

        // formula obtained from here: https://captaincalculator.com/health/calorie/calories-burned-running-calculator/
        // Average MET (Metabolic Equivalent of Task) for running is approximately 8 METs
        val metValueRunning = 8f

        // figure out the time spent running
        val timeInHours = distanceInMeters / avgSpeed

        // return calories estimated result
        return (metValueRunning * weight * timeInHours).toInt()
    }

    // create an image file for the map screenshot
    private fun createImageFile(): File {
        val imageFileName = System.currentTimeMillis().toString()
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".png", storageDir)
    }

    // when the run has finished zoom so that you can only see the route taken
    private fun zoomToSeeWholeTrack() {

        // take the bounds of the google maps
        val bounds = LatLngBounds.Builder()

        // go through all of the polylines drawn and get their lat and lng
        for (polyline in pathPoints) {
            for (pos in polyline) {
                bounds.include(pos)
            }
        }

        // get the mapFragment and mapView
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        val mapView = mapFragment.view as View

        // move the camera to the bounds to prepare to get that image that will be stored in firestore
        mMap.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                mapView.width,
                mapView.height,
                (mapView.height * 0.05f).toInt() // Add padding around the edges to ensure the entire walk path is visible on the map
            )
        )
    }

    // function to toggle the run and its coresponding service
    private fun toggleRun() {
        // if is tracking meaning that the user is currently running, the stop button will pause the service
        if (isTracking) {
            menu?.getItem(0)?.isVisible = true
            sendCommandToService(Constants.ACTION_PAUSE_SERVICE)
        } else {
            // else the user needs to start/resume the service
            sendCommandToService(Constants.ACTION_START_OR_RESUME_SERVICE)
        }
    }

    // on the top right when the user starts a walk, they can click this to cancel it
    private fun showCancelTrackingDialog() {

        // basic dialog allowing users to cancel their current walk or resume if accidently pressed it.
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Cancel Run?")
            .setMessage("Are you sure you want to cancel the current run and delete all of its data?")
            .setIcon(R.drawable.baseline_delete_forever_24)
            .setPositiveButton("Yes") { _, _ ->
                stopRun()
            }
            .setNegativeButton("No") { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
        dialog.show()
    }

    // stop run function, will reset the timer to zero, stop the service and navigate users to the homepage
    private fun stopRun() {
        binding.textTimer.text = "00:00:00:00"
        sendCommandToService(Constants.ACTION_STOP_SERVICE)
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    // finish run function, will reset the timer to zero, stop the service and navigate users to the homepage
    private fun finishRun() {
        binding.textTimer.text = "00:00:00:00"
        sendCommandToService(Constants.ACTION_STOP_SERVICE)
        val intent = Intent(this, RunningStatsActivity::class.java)
        startActivity(intent)
        finish()
    }

    // function to navigate users to the homepage
    private fun goToHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    // observe the status of the user run
    private fun subscribeToObservers() {

        // keep track of the buttons when the user is running
        TrackingService.isTracking.observe(this) {
            updateTracking(it)
            invalidateOptionsMenu()
        }

        // add the latest polylines (lines representing where the user ran) to the screen, whilst the camera follows them
        TrackingService.pathPoints.observe(this) {
            pathPoints = it
            addLatestPolyline()
            moveCameraToUser()

        }

        // update the text timer including the milliseconds.
        TrackingService.timeActivityInMillis.observe(this) {
            currentTimeMillis = it

            // include the milliseconds
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(currentTimeMillis, true)
            binding.textTimer.text = formattedTime
        }
    }

    // function to add the most latest polyline to the map
    private fun addLatestPolyline() {

        // check if a new polyline needs to be drawn based on the most recent path points
        if (this::mMap.isInitialized && pathPoints.isNotEmpty() && pathPoints.last().size > 1) {
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
            val lastLatLng =
                pathPoints.last().last() // last coordinate of the last polyline retrieved

            // draw the most recent polyline
            val polylineOptions = PolylineOptions()
                .color(Constants.POLYLINE_COLOR)
                .width(Constants.POLYLINE_WIDTH)
                .add(preLastLatLng)
                .add(lastLatLng)

            // add polyine to the map
            mMap.addPolyline(polylineOptions)
        }
    }

    // function to zoom in/out onto the whole route
    private fun moveCameraToUser() {
        // move the map camera zoom to encompass the users whole route
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    Constants.MAP_ZOOM
                )
            )
        }
    }
    // function to manage what buttons and text are shown while the user is running
    private fun updateTracking(isTracking: Boolean) {
        this.isTracking = isTracking

        // if the user is not running, only show the start button
        if (!isTracking && currentTimeMillis > 0L) {
            binding.btnToggleRun.text = "Start"
            binding.btnFinishRun.visibility = View.VISIBLE

            // Hide cancel button when not tracking
            binding.imgCancelRun.visibility = View.INVISIBLE
        } else if (isTracking) {
            // if the user is walking replace the start button with a stop button
            binding.btnToggleRun.text = "Stop"
            binding.btnFinishRun.visibility = View.GONE

            // Show cancel button when tracking
            binding.imgCancelRun.visibility = View.VISIBLE

            // Set click listener for cancel button
            binding.imgCancelRun.setOnClickListener {
                showCancelTrackingDialog()
            }
        }
    }

    // for every update of movement draw polylines on the map
    private fun addAllPolylines() {

        // go through all of the path points and draw a polyine
        for (polyline in pathPoints) {
            val polylineOptions = PolylineOptions()
                .color(Constants.POLYLINE_COLOR)
                .width(Constants.POLYLINE_WIDTH)
                .addAll(polyline)

            // add polylines to the map
            mMap.addPolyline(polylineOptions)
        }
    }

    // function to sendCommandToService
    private fun sendCommandToService(action: String) {

        // take what every the current service is and send it to the tracking service class
        Intent(applicationContext, TrackingService::class.java).also {
            it.action = action
            startService(it)
        }
    }

    // initilise the google maps will all of the polylines and subscribetoObservers
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        addAllPolylines()
        subscribeToObservers()
    }


}