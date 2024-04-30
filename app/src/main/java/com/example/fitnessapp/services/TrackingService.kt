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

    // global variables for is first activity and service killed
    private var isFirstActivity = true
    private var serviceKilled = false

    // dependecy injection for location client
    @Inject
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    // keep track of the time for the activity
    private val timeActivityInSeconds = MutableLiveData<Long>()

    // dependecy injection for basic notificaiton
    @Inject
    private lateinit var baseNotificationBuilder: NotificationCompat.Builder

    // keep track of the current notification builder
    private lateinit var currentNotificationBuilder: NotificationCompat.Builder

    // object to store all of the relevent details for the walk route
    companion object {
        val timeActivityInMillis = MutableLiveData<Long>()
        val isTracking = MutableLiveData<Boolean>()
        val pathPoints = MutableLiveData<Polylines>()

    }

    // set initial values for variables
    private fun postInitialValues() {
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())
        timeActivityInSeconds.postValue(0L)
        timeActivityInMillis.postValue(0L)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {

        super.onCreate()
        postInitialValues()


        // get base notification and fusedlocation provider
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        baseNotificationBuilder = createBaseNotificationBuilder()

        // observive is tracking keep track of updateLocation Tracking and updateNotificatoin, passing in this
        isTracking.observe(this) {
            updateLocationTracking(it)
            updateNotificationTrackingState(it)
        }
    }


    // setup basic notification for activity
    private fun createBaseNotificationBuilder(): NotificationCompat.Builder {

        // set notificationManager
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // android phone modern enough to create a notificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        // return basic notification with initial values
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.baseline_directions_walk_24)
            .setContentTitle("Fitness App")
            .setContentText("00:00")
            .setContentIntent(getActivityPendingIntent())
    }

    // update the existing notification
    private fun updateNotificationTrackingState(isTracking: Boolean) {

        // if the user is walking/running set notification to Pause else set it to resume
        val notificationActionText = if (isTracking) "Pause" else "Resume"

        // if the user is walking/running allow users to pause the service
        val pendingIntent = if (isTracking) {
            val pauseIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            PendingIntent.getService(this, 1, pauseIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        } else {
            // else allow users to start and resume the service
            val resumeIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_START_OR_RESUME_SERVICE
            }
            PendingIntent.getService(this, 2, resumeIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        }

        // Get the system notification manager
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Set the current notification builder to the base notification builder
        currentNotificationBuilder = baseNotificationBuilder

        // Clear any existing notification actions, so that it can be updated later
        currentNotificationBuilder.javaClass.getDeclaredField("mActions").apply {
            isAccessible = true
            set(currentNotificationBuilder, ArrayList<NotificationCompat.Action>())
        }

        // If the service is not killed, update the notification with the appropriate action button
        if (!serviceKilled) {
            currentNotificationBuilder = baseNotificationBuilder
                .addAction(
                    R.drawable.baseline_pause_24,
                    notificationActionText,
                    pendingIntent
                )
            notificationManager.notify(NOTIFICATION_ID, currentNotificationBuilder.build())
        }
    }

    // manage actions that the user can take
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            // if the user starts/resumes a walk
            ACTION_START_OR_RESUME_SERVICE -> {
                if (isFirstActivity) {
                    start()
                    Log.d("TrackingService", "Starting Service")
                    isFirstActivity = false
                } else {
                    Log.d("TrackingService", "Resuming Service")
                    startTimer()
                }
            }

            // allow users to pause the service
            ACTION_PAUSE_SERVICE -> {
                Log.d("TrackingService", "Paused Service")
                pauseService()
            }

            // allow users to stop the service
            ACTION_STOP_SERVICE -> {
                Log.d("TrackingService", "Stopped Service")
                killService()
            }

            // error that is unlikely to occur
            else -> {
                Log.d("TrackingService", "Error Failed Tracking Service")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // vars to update the timer
    private var isTimerEnabled = false
    private var lapTime = 0L
    private var timeActivity = 0L
    private var timeStarted = 0L
    private var lastSecondTimeStamp = 0L


    // function to start the timer
    private fun startTimer() {
        addEmptyPolyline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true

        // continously update the timer whilst the user is walking/running
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value!!) {
                // difference between now and time started
                lapTime = System.currentTimeMillis() - timeStarted

                // Post new lap time
                timeActivityInMillis.postValue(timeActivity + lapTime)

                // check if a new whole second has passed before updating the stopwatch
                if (timeActivityInMillis.value!! >= lastSecondTimeStamp + 1000L) {
                    timeActivityInSeconds.postValue(timeActivityInSeconds.value!! + 1)
                    lastSecondTimeStamp += 1000L
                }
                // update timer every 50ms
                delay(TIMER_UPDATE_INTERVAL)
            }
            // add to current time
            timeActivity += lapTime
        }
    }

    // kill the service and reset, so users can walk/run again.
    private fun killService() {
        serviceKilled = true
        isFirstActivity = true
        pauseService()
        postInitialValues()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    // pause the service
    private fun pauseService() {
        isTracking.postValue(false)
        isTimerEnabled = false
    }
    // update the users location so that the polylines can be drawn
    // suppress the issue for missing location permissions because they have been obtained in the HomeActivity
    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking: Boolean) {

        // if tracking update the users location
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
            // stop tracking the users location
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }

    // obtain the lat and lng values of the users current location
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            if (isTracking.value!!) {
                result.locations.let { locations ->

                    // loop through location and log to logcat and pass to the addPathPoint Function
                    for (location in locations) {
                        addPathPoint(location)
                        Log.d("NEW LOCATION:", "${location.latitude}, ${location.longitude}")
                    }
                }
            }
        }
    }

    // add the latest location to the path points
    private fun addPathPoint(location: android.location.Location?) {
        location?.let {
            val pos = LatLng(location.latitude, location.longitude)
            pathPoints.value?.apply {
                last().add(pos)
                pathPoints.postValue(this)
            }
        }
    }

    // Adds an empty polyline to the list of path points.
    private fun addEmptyPolyline() = pathPoints.value?.apply {
        // If path points value is not null, add an empty polyline to it
        add(mutableListOf())

        // Update the LiveData with the modified list of path points
        pathPoints.postValue(this)

    // If path points value is null, initialize a new list containing an empty polyline and update the LiveData
    } ?: pathPoints.postValue(mutableListOf(mutableListOf()))


    @RequiresApi(Build.VERSION_CODES.O)
    private fun start() {
        // Start timer for tracking activity
        startTimer()
        // Set tracking state to true
        isTracking.postValue(true)

        // Get notification manager service
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for devices with Android Oreo (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        // Start the service as a foreground service with the base notification builder
        startForeground(NOTIFICATION_ID, baseNotificationBuilder.build())

        // Observe time activity and update the notification with stopwatch time
        timeActivityInSeconds.observe(this) {
            // Check if the service is not killed
            if (!serviceKilled) {
                // Update notification content text with formatted stopwatch time
                val notification = currentNotificationBuilder
                    .setContentText(TrackingUtility.getFormattedStopWatchTime(it * 1000L, false))

                // Notify the notification manager with the updated notification
                notificationManager.notify(NOTIFICATION_ID, notification.build())
            }
        }
    }
    // if the users clicks on the notification redirect them to the WalkingActivity
    private fun getActivityPendingIntent() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, WalkingActivity::class.java).apply {
            action = Constants.ACTION_SHOW_TRACKING_FRAGMENT
        },
        FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
    )

    // create a a notification channel, with the importance low so it doesn't make a sound every time it is updated.
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