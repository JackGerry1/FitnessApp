package com.example.fitnessapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fitnessapp.R
import com.example.fitnessapp.constants.ActivityData
import com.example.fitnessapp.constants.HeartData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HeartAdapter (private val heartDataList: List<HeartData>) : RecyclerView.Adapter<HeartAdapter.HeartViewHolder>(){

    // where the data for the runs and walks will be displayed
    inner class HeartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateTextView: TextView = itemView.findViewById(R.id.textDateHeartRate)
        val heartRate: TextView = itemView.findViewById(R.id.textHeartRateStats)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HeartAdapter.HeartViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_heart, parent, false)
        return HeartViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: HeartAdapter.HeartViewHolder, position: Int) {
        val heartData = heartDataList[position]

        val dateTimestamp = heartData.dateTimestamp
        val dateFormat = SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault())
        val formattedDate = if (dateTimestamp != null) {
            dateFormat.format(Date(dateTimestamp))
        } else {
            "N/A"
        }

        holder.dateTextView.text = "$formattedDate"
        holder.heartRate.text = "Heart Rate: ${heartData.heartRate ?: "N/A"}"
    }

    override fun getItemCount(): Int {
        return heartDataList.size
    }
}