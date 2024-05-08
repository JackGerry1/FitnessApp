package com.example.fitnessapp.constants

// data for the walks and runs that will be stored and displayed to users
data class ActivityData(
    val dateTimestamp: Long? = null,
    val avgSpeed: String? = null,
    val distanceInKM: String? = null,
    val durationInMillis: Long? = null,
    val caloriesBurned: Long? = null,
    val imageUrl: String? = null
)
