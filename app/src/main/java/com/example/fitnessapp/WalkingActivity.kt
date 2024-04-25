package com.example.fitnessapp


import android.content.Intent
import android.graphics.Bitmap
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


class WalkingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityWalkingBinding
    private lateinit var auth: FirebaseAuth
    private var storageRef = Firebase.storage
    private var db = FirebaseFirestore.getInstance()
    private var currentUser = FirebaseAuth.getInstance().currentUser
    private var isTracking = false
    private var pathPoints = mutableListOf<Polyline>()
    private var currentTimeMillis = 0L
    private var weight = 0f
    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        auth = Firebase.auth
        storageRef = FirebaseStorage.getInstance()

        fetchUserWeight()

        binding.imgBack.setOnClickListener {
            goToHomeActivity()
        }
        binding.btnToggleWalk.setOnClickListener {
            toggleWalk()
        }

        binding.btnFinishWalk.setOnClickListener {
            zoomToSeeWholeTrack()
            endWalkAndSaveToDb()
        }
    }

    private fun fetchUserWeight() {

        val weightRef = db.collection("users").document(currentUser!!.uid)
        weightRef.get()
            .addOnSuccessListener { documentSnapshot ->
                val userWeight = documentSnapshot.getString("weight")
                weight = userWeight!!.toFloat()

            }
            .addOnFailureListener { e ->
                Log.e("Users Weight ERROR", e.toString())
            }
    }


    private fun toggleWalk() {
        if (isTracking) {
            Log.d("Toggle Run", "Pause Service")
            menu?.getItem(0)?.isVisible = true
            sendCommandToService(ACTION_PAUSE_SERVICE)
        } else {
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
            Log.d("Toggle Run", "Start Service/Resume Service")
        }
    }

    private fun showCancelTrackingDialog() {
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

    private fun stopWalk() {
        sendCommandToService(ACTION_STOP_SERVICE)
        goToHomeActivity()
    }

    private fun subscribeToObservers() {
        TrackingService.isTracking.observe(this) {
            updateTracking(it)
            invalidateOptionsMenu()
        }

        TrackingService.pathPoints.observe(this) {
            pathPoints = it
            addLatestPolyline()
            moveCameraToUser()

        }

        TrackingService.timeWalkInMillis.observe(this) {
            currentTimeMillis = it

            // include the milliseconds
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(currentTimeMillis, true)
            binding.textTimer.text = formattedTime
        }
    }

    private fun goToHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun updateTracking(isTracking: Boolean) {
        this.isTracking = isTracking
        if (!isTracking) {
            binding.btnToggleWalk.text = "Start"
            binding.btnFinishWalk.visibility = View.VISIBLE
            // Hide cancel button when not tracking
            binding.imgCancelWalk.visibility = View.INVISIBLE
        } else {
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

    private fun zoomToSeeWholeTrack() {
        val bounds = LatLngBounds.Builder()

        for (polyline in pathPoints) {
            for (pos in polyline) {
                bounds.include(pos)
            }
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        val mapView = mapFragment.view as View

        mMap.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                mapView.width,
                mapView.height,
                (mapView.height * 0.05f).toInt() // Add padding around the edges to ensure the entire walk path is visible on the map
            )
        )
    }

    private fun endWalkAndSaveToDb() {
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
                    // Handle failure
                    Log.e("endWalkAndSaveToDb ERROR", "Upload failed: ${task.exception}")
                }
            }


        }
    }

    // saveWalkDataToFirestore stores data in the format:
    /*
    walking
        |_ userID
            |_ documentId
                |_ date_timestamp: <value>
                |_ avg_speed: <value>
                |_ distance_in_meters: <value>
                |_ duration_in_millis: <value>
                |_ calories_burned: <value>
                |_ image_url: <value>
    */
    private fun saveWalkDataToFirestore(imageUrl: String) {
        val distanceInMeters = calculateTotalDistance()
        val avgSpeed = calculateAverageSpeed(distanceInMeters)
        val dateTimestamp = Calendar.getInstance().timeInMillis
        val caloriesBurned = calculateCaloriesBurnedWalking(distanceInMeters, weight, avgSpeed)

        val walkData = hashMapOf(
            "date_timestamp" to dateTimestamp,
            "avg_speed" to avgSpeed,
            "distance_in_meters" to distanceInMeters,
            "duration_in_millis" to currentTimeMillis,
            "calories_burned" to caloriesBurned,
            "image_url" to imageUrl // Add the image URL to the walk data
        )

        currentUser?.uid?.let { userId ->
            val db = FirebaseFirestore.getInstance()
            val userDocument = db.collection("walking").document(userId)
            val documentId = dateTimestamp.toString() // Use date timestamp as document ID

            val userData = hashMapOf(
                documentId to walkData
            )

            userDocument
                .set(userData,  SetOptions.merge()) // Merge with existing data
                .addOnSuccessListener {
                    Log.d("Successfully Uploaded Walk Data", "DocumentSnapshot added with ID: $documentId")
                    Toast.makeText(
                        applicationContext,
                        "Walk saved successfully",
                        Toast.LENGTH_LONG
                    ).show()
                    stopWalk()
                }
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



    private fun calculateTotalDistance(): Float {
        var distanceInMeters = 0f
        for (polyline in pathPoints) {
            distanceInMeters += TrackingUtility.calculatePolylineLength(polyline)
        }
        return distanceInMeters
    }

    private fun calculateAverageSpeed(distanceInMeters: Float): Float {
        val distanceInKilometers = distanceInMeters / 1000f
        val timeInHours = currentTimeMillis / 1000f / 60 / 60
        return distanceInKilometers / timeInHours
    }

    private fun calculateCaloriesBurnedWalking(
        distanceInMeters: Float,
        weight: Float,
        avgSpeed: Float
    ): Int {

        // formula obtained from here: https://captaincalculator.com/health/calorie/calories-burned-walking-calculator/
        // Average MET (Metabolic Equivalent of Task) for walking is approximately 3.5 METs
        val metValueWalking = 3.5f

        val timeInHours = distanceInMeters / avgSpeed

        return (metValueWalking * weight * timeInHours).toInt()
    }

    private fun createImageFile(): File {
        val imageFileName = System.currentTimeMillis().toString()
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".png", storageDir)
    }

    private fun moveCameraToUser() {
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    MAP_ZOOM
                )
            )
        }
    }

    private fun addAllPolylines() {
        for (polyline in pathPoints) {
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyline)

            mMap.addPolyline(polylineOptions)
        }
    }

    private fun addLatestPolyline() {
        if (this::mMap.isInitialized && pathPoints.isNotEmpty() && pathPoints.last().size > 1) {
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
            val lastLatLng =
                pathPoints.last().last() // last coordinate of the last polyline retrieved
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng)
                .add(lastLatLng)

            mMap.addPolyline(polylineOptions)
        }
    }

    private fun sendCommandToService(action: String) {
        Intent(applicationContext, TrackingService::class.java).also {
            it.action = action
            startService(it)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        addAllPolylines()
        subscribeToObservers()
    }
}