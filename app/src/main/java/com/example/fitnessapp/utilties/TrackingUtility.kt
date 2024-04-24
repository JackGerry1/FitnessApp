package com.example.fitnessapp.utilties

import android.Manifest
import android.content.Context
import android.os.Build
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

}