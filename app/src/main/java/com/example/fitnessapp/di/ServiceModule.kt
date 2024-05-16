package com.example.fitnessapp.di

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.fitnessapp.R
import com.example.fitnessapp.WalkingActivity
import com.example.fitnessapp.constants.Constants
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
/*
References:

  Lackner, P. (2020c). Updating the Notification - MVVM Running Tracker App - Part 16. [online] YouTube.
  Available at: https://www.youtube.com/watch?v=OE6wB_MHmgA&list=PLQkwcJG4YTCQ6emtoqSZS2FVwZR9FT3BV&index=16 [Accessed 24 Apr. 2024].

*/
@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    // depenendcy injection to give the current location of the user
    @ServiceScoped
    @Provides
    fun provideFusedLocationProviderClient(
        @ApplicationContext app: Context
    ): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(app)
    }

    // gives a basic notification template so that this code doesn't have to be written multiple times.
    @ServiceScoped
    @Provides
    fun provideBaseNotificationBuilder(
        @ApplicationContext app: Context,
        pendingIntent: PendingIntent
    ) = NotificationCompat.Builder(app, Constants.NOTIFICATION_CHANNEL_ID)
        .setAutoCancel(false)
        .setOngoing(true)
        .setSmallIcon(R.drawable.baseline_directions_walk_24)
        .setContentTitle("Fitness App")
        .setContentText("00:00")
        .setContentIntent(pendingIntent)
        .build()
}