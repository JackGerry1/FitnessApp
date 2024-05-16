package com.example.fitnessapp.utilties

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Build
import com.example.fitnessapp.services.Polyline
import com.vmadalin.easypermissions.EasyPermissions
import java.util.concurrent.TimeUnit
import kotlin.math.min

/*
References:

  Lackner, P. (2020b). Implementing the Stop Watch - MVVM Running Tracker App - Part 15. [online] YouTube.
  Available at: https://www.youtube.com/watch?v=LTUmtp7IDEg&list=PLQkwcJG4YTCQ6emtoqSZS2FVwZR9FT3BV&index=15 [Accessed 24 Apr. 2024].

*/
object TrackingUtility {

    // function to obtain the formatted stop watch time for the text timer and notification
    fun getFormattedStopWatchTime(ms: Long, includeMillis: Boolean = false): String {

        // take in the milliseconds
        var milliseconds = ms

        // convert to hours
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        milliseconds -= TimeUnit.HOURS.toMillis(hours)

        // convert to minutes
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        milliseconds -= TimeUnit.MINUTES.toMillis(minutes)

        // convert to seconds
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)

        // if true (this means that this data is for the text timer), include the millseconds
        return if (includeMillis) {
            milliseconds -= TimeUnit.SECONDS.toMillis(seconds)
            milliseconds /= 10
            "%02d:%02d:%02d:%02d".format(hours, minutes, seconds, milliseconds)
        } else {
            // this is the time for the notification
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        }
    }

    // function to calculate the length of an indivdual most recent polyline
    fun calculatePolylineLength(polyline: Polyline): Float {

        // initialise distance to zero
        var distance = 0f

        // go through every polyline - 2, to not get an out of bounds error
        for(i in 0..polyline.size - 2) {

            // start and end of polyline to be drawn
            val pos1 = polyline[i]
            val pos2 = polyline[i + 1]

            // store the results of the lat and lng positions
            // and calculate the distance between them
            // the result is empty at the start to prevent errors
            val result = FloatArray(1)
            Location.distanceBetween(
                pos1.latitude,
                pos1.longitude,
                pos2.latitude,
                pos2.longitude,
                result
            )
            // calculate and return the total distance
            distance += result[0]
        }
        return distance
    }
}