package com.example.fitnessapp.constants

import android.graphics.Color

class Constants {
    // constant values that will be used throughout the application
    companion object {

        // request location code
        const val REQUEST_CODE_LOCATION_PERMISSION = 0

        // various ACTIONS For running and walking with the notification
        const val ACTION_START_OR_RESUME_SERVICE = "ACTION_START_OR_RESUME_SERVICE"
        const val ACTION_PAUSE_SERVICE = "ACTION_PAUSE_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_SHOW_TRACKING_FRAGMENT = "ACTION_SHOW_TRACKING_FRAGMENT"

        // update the timer every 50 milliseconds
        const val TIMER_UPDATE_INTERVAL = 50L

        // update the location every 2 seconds
        const val LOCATION_UPDATE_INTERVAL = 2000L

        // polyline customisation
        const val POLYLINE_COLOR = Color.RED
        const val POLYLINE_WIDTH = 8f
        const val MAP_ZOOM = 15f

        // notification channel names and id
        const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Tracking"
        const val NOTIFICATION_ID = 1

        /*
        * calories burned: https://www.omnicalculator.com/sports/steps-to-calories
        * steps to km: https://www.thecalculatorsite.com/health/steps-km.php
        * */

        // step constants
        const val STEPS_TO_KM = 0.000762
        const val CALORIES_PER_STEP = 0.04
    }

}