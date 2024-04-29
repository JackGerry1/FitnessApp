package com.example.fitnessapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.fitnessapp.R
import com.example.fitnessapp.constants.ActivityData
import com.example.fitnessapp.utilties.TrackingUtility
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityAdapter(private val activityDataList: List<ActivityData>) : RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder>() {

    inner class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateTextView: TextView = itemView.findViewById(R.id.textDate)
        val avgSpeedTextView: TextView = itemView.findViewById(R.id.textAvgSpeed)
        val distanceTextView: TextView = itemView.findViewById(R.id.textDistance)
        val durationTextView: TextView = itemView.findViewById(R.id.textDuration)
        val caloriesTextView: TextView = itemView.findViewById(R.id.textCaloriesBurned)
        val imageView: ImageView = itemView.findViewById(R.id.imgWalk)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_walk, parent, false)
        return ActivityViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val activityData = activityDataList[position]

        val formattedDuration = TrackingUtility.getFormattedStopWatchTime(activityData.durationInMillis!!, true)
        val dateTimestamp = activityData.dateTimestamp
        val dateFormat = SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault())
        val formattedDate = if (dateTimestamp != null) {
            dateFormat.format(Date(dateTimestamp))
        } else {
            "N/A"
        }

        holder.dateTextView.text = "Date: $formattedDate"
        holder.avgSpeedTextView.text = "Average Speed: ${activityData.avgSpeed ?: "N/A"} km/h"
        holder.distanceTextView.text = "Distance: ${activityData.distanceInKM ?: "N/A"} kilometers"
        holder.durationTextView.text = "Duration: ${formattedDuration ?: "N/A"}"
        holder.caloriesTextView.text = "Calories Burned: ${activityData.caloriesBurned ?: "N/A"}"
        activityData.imageUrl?.let {
            Glide.with(holder.imageView.context)
                .load(it)
                .into(holder.imageView)
        }
    }

    override fun getItemCount(): Int {
        return activityDataList.size
    }
}