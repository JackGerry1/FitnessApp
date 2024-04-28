package com.example.fitnessapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.fitnessapp.R
import com.example.fitnessapp.constants.WalkData
import com.example.fitnessapp.utilties.TrackingUtility
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalkAdapter(private val walkDataList: List<WalkData>) : RecyclerView.Adapter<WalkAdapter.WalkViewHolder>() {

    inner class WalkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateTextView: TextView = itemView.findViewById(R.id.textDate)
        val avgSpeedTextView: TextView = itemView.findViewById(R.id.textAvgSpeed)
        val distanceTextView: TextView = itemView.findViewById(R.id.textDistance)
        val durationTextView: TextView = itemView.findViewById(R.id.textDuration)
        val caloriesTextView: TextView = itemView.findViewById(R.id.textCaloriesBurned)
        val imageView: ImageView = itemView.findViewById(R.id.imgWalk)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalkViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_walk, parent, false)
        return WalkViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: WalkViewHolder, position: Int) {
        val walkData = walkDataList[position]

        val formattedDuration = TrackingUtility.getFormattedStopWatchTime(walkData.durationInMillis!!, true)
        val dateTimestamp = walkData.dateTimestamp
        val dateFormat = SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault())
        val formattedDate = if (dateTimestamp != null) {
            dateFormat.format(Date(dateTimestamp))
        } else {
            "N/A"
        }

        holder.dateTextView.text = "Date: $formattedDate"
        holder.avgSpeedTextView.text = "Average Speed: ${walkData.avgSpeed ?: "N/A"} km/h"
        holder.distanceTextView.text = "Distance: ${walkData.distanceInKM ?: "N/A"} kilometers"
        holder.durationTextView.text = "Duration: ${formattedDuration ?: "N/A"}"
        holder.caloriesTextView.text = "Calories Burned: ${walkData.caloriesBurned ?: "N/A"}"
        walkData.imageUrl?.let {
            Glide.with(holder.imageView.context)
                .load(it)
                .into(holder.imageView)
        }
    }

    override fun getItemCount(): Int {
        return walkDataList.size
    }
}