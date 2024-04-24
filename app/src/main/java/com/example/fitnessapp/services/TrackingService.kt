package com.example.fitnessapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.app.Service
import android.content.pm.PackageManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ServiceInfo
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.example.fitnessapp.R
import com.example.fitnessapp.WalkingActivity
import com.example.fitnessapp.constants.Constants

import com.example.fitnessapp.constants.Constants.Companion.ACTION_PAUSE_SERVICE
import com.example.fitnessapp.constants.Constants.Companion.ACTION_START_OR_RESUME_SERVICE
import com.example.fitnessapp.constants.Constants.Companion.ACTION_STOP_SERVICE
import com.example.fitnessapp.constants.Constants.Companion.LOCATION_UPDATE_INTERVAL
import com.example.fitnessapp.constants.Constants.Companion.NOTIFICATION_CHANNEL_ID
import com.example.fitnessapp.constants.Constants.Companion.NOTIFICATION_CHANNEL_NAME
import com.example.fitnessapp.constants.Constants.Companion.NOTIFICATION_ID
import com.example.fitnessapp.constants.Constants.Companion.TIMER_UPDATE_INTERVAL
import com.example.fitnessapp.utilties.TrackingUtility
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.maps.model.LatLng
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias Polyline = MutableList<LatLng>
typealias Polylines = MutableList<Polyline>


@AndroidEntryPoint
class TrackingService : LifecycleService() {
    private var isFirstWalk = true
    private var serviceKilled = false

    @Inject
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private val timeWalkInSeconds = MutableLiveData<Long>()

    @Inject
    private lateinit var baseNotificationBuilder: NotificationCompat.Builder


    private lateinit var currentNotificationBuilder: NotificationCompat.Builder

    companion object {
        val timeWalkInMillis = MutableLiveData<Long>()
        val isTracking = MutableLiveData<Boolean>()
        val pathPoints = MutableLiveData<Polylines>()

    }

    private fun postInitialValues() {
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())
        timeWalkInSeconds.postValue(0L)
        timeWalkInMillis.postValue(0L)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {

        super.onCreate()
        postInitialValues()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        baseNotificationBuilder = createBaseNotificationBuilder()
        isTracking.observe(this) {
            updateLocationTracking(it)
            updateNotificationTrackingState(it)
        }
    }

    private fun createBaseNotificationBuilder(): NotificationCompat.Builder {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.baseline_directions_run_24)
            .setContentTitle("Running App")
            .setContentText("00:00")
            .setContentIntent(getWalkingActivityPendingIntent())
    }


    private fun updateNotificationTrackingState(isTracking: Boolean) {
        val notificationActionText = if (isTracking) "Pause" else "Resume"
        val pendingIntent = if (isTracking) {
            val pauseIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            PendingIntent.getService(this, 1, pauseIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        } else {
            val resumeIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_START_OR_RESUME_SERVICE
            }
            PendingIntent.getService(this, 2, resumeIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        currentNotificationBuilder = baseNotificationBuilder

        currentNotificationBuilder.javaClass.getDeclaredField("mActions").apply {
            isAccessible = true
            set(currentNotificationBuilder, ArrayList<NotificationCompat.Action>())
        }
        if (!serviceKilled) {
            currentNotificationBuilder = baseNotificationBuilder
                .addAction(R.drawable.baseline_pause_24, notificationActionText, pendingIntent)
            notificationManager.notify(NOTIFICATION_ID, currentNotificationBuilder.build())
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OR_RESUME_SERVICE -> {
                if (isFirstWalk) {
                    start()
                    Log.d("TrackingService", "Starting Service")
                    isFirstWalk = false
                } else {
                    Log.d("TrackingService", "Resuming Service")
                    startTimer()
                }
            }

            ACTION_PAUSE_SERVICE -> {
                Log.d("TrackingService", "Paused Service")
                pauseService()
            }

            ACTION_STOP_SERVICE -> {
                Log.d("TrackingService", "Stopped Service")
                killService()
            }

            else -> {
                Log.d("TrackingService", "Error Failed Tracking Service")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private var isTimerEnabled = false
    private var lapTime = 0L
    private var timeWalk = 0L
    private var timeStarted = 0L
    private var lastSecondTimeStamp = 0L

    private fun startTimer() {
        addEmptyPolyline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value!!) {
                // difference between now and time started
                lapTime = System.currentTimeMillis() - timeStarted

                // Post new lap time
                timeWalkInMillis.postValue(timeWalk + lapTime)

                // check if a new whole second has passed before updating the stopwatch
                if (timeWalkInMillis.value!! >= lastSecondTimeStamp + 1000L) {
                    timeWalkInSeconds.postValue(timeWalkInSeconds.value!! + 1)
                    lastSecondTimeStamp += 1000L
                }
                delay(TIMER_UPDATE_INTERVAL)
            }
            timeWalk += lapTime
        }
    }

    private fun killService() {
        serviceKilled = true
        isFirstWalk = true
        pauseService()
        postInitialValues()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun pauseService() {
        isTracking.postValue(false)
        isTimerEnabled = false
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking: Boolean) {
        if (isTracking) {
            val request =
                LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL).apply {
                    setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                    setWaitForAccurateLocation(true)
                }.build()
            fusedLocationProviderClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )

        } else {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            if (isTracking.value!!) {
                result.locations.let { locations ->
                    for (location in locations) {
                        addPathPoint(location)
                        Log.d("NEW LOCATION:", "${location.latitude}, ${location.longitude}")
                    }
                }
            }
        }
    }

    private fun addPathPoint(location: android.location.Location?) {
        location?.let {
            val pos = LatLng(location.latitude, location.longitude)
            pathPoints.value?.apply {
                last().add(pos)
                pathPoints.postValue(this)
            }
        }
    }

    private fun addEmptyPolyline() = pathPoints.value?.apply {
        add(mutableListOf())
        pathPoints.postValue(this)
    } ?: pathPoints.postValue(mutableListOf(mutableListOf()))

    @RequiresApi(Build.VERSION_CODES.O)
    private fun start() {
        startTimer()
        isTracking.postValue(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }
        startForeground(NOTIFICATION_ID, baseNotificationBuilder.build())

        timeWalkInSeconds.observe(this) {
            if (!serviceKilled) {
                val notification = currentNotificationBuilder
                    .setContentText(TrackingUtility.getFormattedStopWatchTime(it * 1000L, false))

                notificationManager.notify(NOTIFICATION_ID, notification.build())
            }
        }
    }

    private fun getWalkingActivityPendingIntent() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, WalkingActivity::class.java).apply {
            action = Constants.ACTION_SHOW_TRACKING_FRAGMENT
        },
        FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
    )

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}