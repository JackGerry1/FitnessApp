package com.example.fitnessapp.utilties

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Build
import com.example.fitnessapp.services.Polyline
import com.vmadalin.easypermissions.EasyPermissions
import java.util.concurrent.TimeUnit
import kotlin.math.min

object TrackingUtility {
    fun getFormattedStopWatchTime(ms: Long, includeMillis: Boolean = false): String {
        var milliseconds = ms

        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        milliseconds -= TimeUnit.HOURS.toMillis(hours)

        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        milliseconds -= TimeUnit.MINUTES.toMillis(minutes)

        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)

        return if (includeMillis) {
            milliseconds -= TimeUnit.SECONDS.toMillis(seconds)
            milliseconds /= 10
            "%02d:%02d:%02d:%02d".format(hours, minutes, seconds, milliseconds)
        } else {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        }
    }

    fun calculatePolylineLength(polyline: Polyline): Float {
        var distance = 0f
        for(i in 0..polyline.size - 2) {
            val pos1 = polyline[i]
            val pos2 = polyline[i + 1]

            val result = FloatArray(1)
            Location.distanceBetween(
                pos1.latitude,
                pos1.longitude,
                pos2.latitude,
                pos2.longitude,
                result
            )
            distance += result[0]
        }
        return distance
    }
}