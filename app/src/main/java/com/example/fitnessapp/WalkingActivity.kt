package com.example.fitnessapp


import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.fitnessapp.constants.Constants
import com.example.fitnessapp.constants.Constants.Companion.ACTION_PAUSE_SERVICE
import com.example.fitnessapp.constants.Constants.Companion.ACTION_START_OR_RESUME_SERVICE
import com.example.fitnessapp.constants.Constants.Companion.ACTION_STOP_SERVICE
import com.example.fitnessapp.constants.Constants.Companion.MAP_ZOOM
import com.example.fitnessapp.constants.Constants.Companion.POLYLINE_COLOR
import com.example.fitnessapp.constants.Constants.Companion.POLYLINE_WIDTH
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.fitnessapp.databinding.ActivityWalkingBinding
import com.example.fitnessapp.services.Polyline
import com.example.fitnessapp.services.Polylines
import com.example.fitnessapp.services.TrackingService
import com.example.fitnessapp.utilties.TrackingUtility
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.appindexing.builders.Actions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.AccessController.getContext
import java.util.Calendar
import java.util.jar.Manifest
import kotlin.math.round

/*
References:

 Lackner, P. (2020a). Drawing the Running Track on the Map - MVVM Running Tracker App - Part 14. [online] YouTube.
 Available at: https://www.youtube.com/watch?v=fIekwHGo7cI&list=PLQkwcJG4YTCQ6emtoqSZS2FVwZR9FT3BV&index=14 [Accessed 19 Apr. 2024].

  Firebase (2024a). Add Data to Cloud Firestore.
  [online] Firebase. Available at: https://firebase.google.com/docs/firestore/manage-data/add-data#kotlin+ktx_2 [Accessed 19 Apr. 2024].
*/

class WalkingActivity : AppCompatActivity(), OnMapReadyCallback {

    // global variables for binding, google maps, firebase
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityWalkingBinding
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

        binding = ActivityWalkingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapWalk) as SupportMapFragment
        mapFragment.getMapAsync(this)


        // initiliase firebase auth and cloud storage
        auth = Firebase.auth
        storageRef = FirebaseStorage.getInstance()


        fetchUserWeight()

        // button to get back to homepage
        binding.imgBack.setOnClickListener {
            goToHomeActivity()
        }

        // button to start/stop a walk
        binding.btnToggleWalk.setBackgroundColor(Color.rgb(245, 20, 43))
        binding.btnToggleWalk.setOnClickListener {
            toggleWalk()
        }

        // button to finish the walk
        binding.btnFinishWalk.setBackgroundColor(Color.rgb(245, 20, 43))
        binding.btnFinishWalk.setOnClickListener {
            zoomToSeeWholeTrack()
            endWalkAndSaveToDb()
        }

        // Bottom navigation view item selection listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // if walk stay on the same page because you are already here
                R.id.bottom_walk -> true

                // if clicking on the stats navigate to the stats page
                R.id.bottom_walk_stats -> {
                    startActivity(Intent(applicationContext, WalkingStatsActivity::class.java))
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

    // function to toggle the walk and its coresponding service
    private fun toggleWalk() {
        // if is tracking meaning that the user is currently walking, the stop button will pause the service
        if (isTracking) {
            menu?.getItem(0)?.isVisible = true
            sendCommandToService(ACTION_PAUSE_SERVICE)
        } else {
            // else the user needs to start/resume the service
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
        }
    }

    // on the top right when the user starts a walk, they can click this to cancel it
    private fun showCancelTrackingDialog() {

        // basic dialog allowing users to cancel their current walk or resume if accidently pressed it.
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Cancel Walk?")
            .setMessage("Are you sure you want to cancel the current walk and delete all of its data?")
            .setIcon(R.drawable.baseline_delete_forever_24)
            .setPositiveButton("Yes") { _, _ ->
                stopWalk()
            }
            .setNegativeButton("No") { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
        dialog.show()
    }

    // stop walk function, will reset the timer to zero, stop the service and navigate users to the homepage
    private fun stopWalk() {
        binding.textTimerWalk.text = "00:00:00:00"
        sendCommandToService(ACTION_STOP_SERVICE)
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
    // finish walk function, will reset the timer to zero, stop the service and navigate users to the walking stats activity
    private fun finishWalk() {
        binding.textTimerWalk.text = "00:00:00:00"
        sendCommandToService(ACTION_STOP_SERVICE)
        val intent = Intent(this, WalkingStatsActivity::class.java)
        startActivity(intent)
        finish()
    }

    // observe the status of the user walk
    private fun subscribeToObservers() {

        // keep track of the buttons when the user is walking
        TrackingService.isTracking.observe(this) {
            updateTracking(it)
            invalidateOptionsMenu()
        }

        // add the latest polylines (lines representing where the user walked) to the screen, whilst the camera follows them
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
            binding.textTimerWalk.text = formattedTime
        }
    }

    // function to navigate to the homeactivity
    private fun goToHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    // function to manage what buttons and text are shown while the user is walking
    private fun updateTracking(isTracking: Boolean) {
        this.isTracking = isTracking

        // if the user is not walking, only show the start button
        if (!isTracking && currentTimeMillis > 0L) {
            binding.btnToggleWalk.text = "Start"
            binding.btnFinishWalk.visibility = View.VISIBLE

            // Hide cancel button when not tracking
            binding.imgCancelWalk.visibility = View.INVISIBLE
        } else if (isTracking) {

            // if the user is walking replace the start button with a stop button
            binding.btnToggleWalk.text = "Stop"
            binding.btnFinishWalk.visibility = View.GONE

            // Show cancel button when tracking
            binding.imgCancelWalk.visibility = View.VISIBLE

            // Set click listener for cancel button
            binding.imgCancelWalk.setOnClickListener {
                showCancelTrackingDialog()
            }
        }
    }

    // when the walk has finished zoom so that you can only see the route taken
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
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapWalk) as SupportMapFragment
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

    // save image of map and put it into cloud firestore
    private fun endWalkAndSaveToDb() {
        // take screenshot of map
        mMap.snapshot { bmp ->
            // Save the snapshot to a temporary file
            val imageFile = createImageFile()
            val outputStream = FileOutputStream(imageFile)
            bmp?.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()

            // Upload the image file to Firebase Cloud Storage
            val storageRef = FirebaseStorage.getInstance().reference
            val imageRef =
                storageRef.child("walking_images/${currentUser!!.uid}/${System.currentTimeMillis()}.png")
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

                    // Save walk data along with image URL to Firestore
                    saveWalkDataToFirestore(downloadUri.toString())
                } else {
                    // if failed to image uri
                    Log.e("endWalkAndSaveToDb ERROR", "Upload failed: ${task.exception}")
                }
            }


        }
    }

    // save walk data with image to firestore
    private fun saveWalkDataToFirestore(imageUrl: String) {

        val timeInSeconds = currentTimeMillis / 1000
        // get walk stats using helper functions
        val distanceInMeters = calculateTotalDistance()
        val avgSpeed = calculateAverageSpeed(distanceInMeters, timeInSeconds)
        val dateTimestamp = Calendar.getInstance().timeInMillis
        val caloriesBurned = calculateCaloriesBurnedWalking(weight, timeInSeconds)


        // Format the avgsped and distance to two decimal places
        val formattedAvgSpeed = "%.2f".format(avgSpeed)
        val formattedDistanceInKM =
            "%.2f".format(distanceInMeters / 1000.0)

        // set hashmap of stored data
        val walkData = hashMapOf(
            "date_timestamp" to dateTimestamp,
            "avg_speed" to formattedAvgSpeed,
            "distance_in_KM" to formattedDistanceInKM,
            "duration_in_millis" to currentTimeMillis,
            "calories_burned" to caloriesBurned,
            "image_url" to imageUrl // link to the image cloud storage
        )


        // the below code stores the data like this:
        /*
        * structure data like this
        * walking
        *   userUID
        *       walks
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
            val userDocument = db.collection("walking").document(userId)
            val documentId = dateTimestamp.toString()

            // create walks subcollection and various documentIDs
            userDocument
                .collection("walks")
                .document(documentId)
                .set(walkData)

                // succesfully uploaded data
                .addOnSuccessListener {
                    Toast.makeText(
                        applicationContext,
                        "Walk saved successfully",
                        Toast.LENGTH_LONG
                    ).show()
                    // finish the walk, so that users go their stats page
                    finishWalk()
                }

                // catch any error uploading data
                .addOnFailureListener { e ->
                    Log.w("Failed Uploading Walk Data", "Error adding document", e)
                    Toast.makeText(
                        applicationContext,
                        "Failed to save walk",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    // helper function to calculate total distance
    private fun calculateTotalDistance(): Float {

        // loop through each polyline and adds it length using the tracking utility and return the result
        var distanceInMeters = 0f
        for (polyline in pathPoints) {
            distanceInMeters += TrackingUtility.calculatePolylineLength(polyline)
        }
        return distanceInMeters
    }

    // calculate average speed in KM/H
    private fun calculateAverageSpeed(distanceInMeters: Float, timeInSeconds: Long): Float {
        // convert seconds to hours
        val timeInHours = timeInSeconds / 3600f
        val distanceInKilometers = distanceInMeters / 1000f
        return distanceInKilometers / timeInHours
    }

    // estimate calories burned walking
    private fun calculateCaloriesBurnedWalking(
        weight: Float,
        timeInSeconds: Long
    ): Int {
        // formula obtained from here: https://captaincalculator.com/health/calorie/calories-burned-walking-calculator/
        // Average MET (Metabolic Equivalent of Task) for walking is approximately 3.5 METs
        val metValueWalking = 3.5f

        // convert seconds to hours
        val timeInHours = timeInSeconds / 3600f

        // return calories estimated result
        return (metValueWalking * weight * timeInHours).toInt()
    }

    // create an image file for the map screenshot
    private fun createImageFile(): File {
        val imageFileName = System.currentTimeMillis().toString()
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".png", storageDir)
    }

    // function to zoom in/out onto the whole route
    private fun moveCameraToUser() {
        // move the map camera zoom to encompass the users whole route
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    MAP_ZOOM
                )
            )
        }
    }

    // for every update of movement draw polylines on the map
    private fun addAllPolylines() {

        // go through all of the path points and draw a polyine
        for (polyline in pathPoints) {
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyline)

            // add polylines to the map
            mMap.addPolyline(polylineOptions)
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
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng)
                .add(lastLatLng)

            // add polyine to the map
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