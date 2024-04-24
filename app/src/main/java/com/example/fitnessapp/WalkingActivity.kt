package com.example.fitnessapp


import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.view.children
import androidx.lifecycle.Observer
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
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.appindexing.builders.Actions
import kotlinx.coroutines.withContext
import java.security.AccessController.getContext
import java.util.jar.Manifest


class WalkingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityWalkingBinding
    private var isTracking = false
    private var pathPoints = mutableListOf<Polyline>()
    private var currentTimeMillis = 0L

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

        binding.imgBack.setOnClickListener {
            goToHomeActivity()
        }
        binding.btnToggleWalk.setOnClickListener {
            toggleRun()
        }
    }

    private fun toggleRun() {
        if(isTracking) {
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
               stopRun()
            }
            .setNegativeButton("No") { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
        dialog.show()
    }

    private fun stopRun() {
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
            binding.btnFinishRun.visibility = View.VISIBLE
            // Hide cancel button when not tracking
            binding.imgCancelWalk.visibility = View.INVISIBLE
        } else {
            binding.btnToggleWalk.text = "Stop"
            binding.btnFinishRun.visibility = View.GONE
            // Show cancel button when tracking
            binding.imgCancelWalk.visibility = View.VISIBLE
            // Set click listener for cancel button
            binding.imgCancelWalk.setOnClickListener {
                showCancelTrackingDialog()
            }
        }
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