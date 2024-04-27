package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fitnessapp.databinding.ActivityWalkingStatsBinding

class WalkingStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalkingStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalkingStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // highlight stats icon
        binding.bottomNavigation.selectedItemId = R.id.bottom_stats

        // Bottom navigation view item selection listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_stats -> true
                R.id.bottom_walk -> {
                    startActivity(Intent(applicationContext, WalkingActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}