package com.example.fitnessapp.constants

// data class for the step data
data class StepsData(
    val currentDate: String? = null,
    val steps: Long? = null,
    val distanceInKM: String? = null,
    val caloriesBurned: Long? = null,
    val goalReached: Boolean? = null
)