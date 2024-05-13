package com.example.fitnessapp


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Bundle

import android.util.Log
import android.view.View

import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

import androidx.appcompat.app.AppCompatActivity

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis


import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider

import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.fitnessapp.databinding.ActivityHeartBinding
import com.google.common.util.concurrent.ListenableFuture

import java.util.concurrent.Executor


class HeartActivity : AppCompatActivity(), ImageAnalysis.Analyzer {
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var grayView: ImageView? = null
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var binding: ActivityHeartBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHeartBinding.inflate(layoutInflater)

        setContentView(binding.root)

        previewView = binding.previewView
        grayView = binding.grayView
        grayView!!.setVisibility(View.GONE)

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        binding.imgBack.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.imgStartHeart.setOnClickListener {
            if (hasPermissions(baseContext)) {
                cameraProviderFuture!!.addListener({
                    cameraProvider = cameraProviderFuture!!.get()
                    startCameraX()

                }, executor)
            } else {
                // Request permissions if not granted
                activityResultLauncher.launch(REQUIRED_PERMISSIONS)
            }
        }

        val graySwitch = binding.grayscaleSwitch
        graySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                grayView!!.setVisibility(View.VISIBLE)
            } else {
                grayView!!.setVisibility(View.INVISIBLE)
            }
        }
    }

    private val executor: Executor
        get() = ContextCompat.getMainExecutor(this)

    private fun startCameraX() {
        cameraProvider?.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        val preview = Preview.Builder()
            .build()
        preview.setSurfaceProvider(previewView!!.getSurfaceProvider())

        // Image analysis use case
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(executor, this)

        // Bind to lifecycle
        val camera = cameraProvider?.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector,
            preview,
            imageAnalysis
        )

        // Get an instance of CameraControl and enable the torch
        camera?.cameraControl?.enableTorch(true)
    }

    override fun analyze(image: ImageProxy) {
        // image processing here for the current frame
        Log.d("TAG", "analyze: got the frame at: " + image.imageInfo.timestamp)
        val bitmap = previewView!!.getBitmap()
        image.close()
        if (bitmap == null) return
        val bitmap1 = toGrayscale(bitmap)
        runOnUiThread { grayView!!.setImageBitmap(bitmap1) }
    }

    private fun toGrayscale(bmpOriginal: Bitmap): Bitmap {
        val height: Int = bmpOriginal.getHeight()
        val width: Int = bmpOriginal.getWidth()
        val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmpGrayscale)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val f = ColorMatrixColorFilter(cm)
        paint.setColorFilter(f)
        c.drawBitmap(bmpOriginal, 0f, 0f, paint)
        return bmpGrayscale
    }
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCameraX()
            }
        }

    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()


        fun hasPermissions(context: Context) = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

}