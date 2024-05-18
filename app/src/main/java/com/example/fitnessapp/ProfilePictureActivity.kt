package com.example.fitnessapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import java.io.File

/*
References:

    Firebase (2024a). Add Data to Cloud Firestore.
    [online] Firebase. Available at: https://firebase.google.com/docs/firestore/manage-data/add-data#kotlin+ktx_2 [Accessed 18 Apr. 2024].

    Firebase (2024b). Upload Files with Cloud Storage on Android.
    [online] Firebase. Available at: https://firebase.google.com/docs/storage/android/upload-files [Accessed 18 Apr. 2024].

    Coding Reel (2022). Camera X Image Analysis Convert Realtime Preview to Grayscale in Java. [online]
    YouTube. Available at: https://www.youtube.com/watch?v=4vv2PtfdWRQ [Accessed 13 May 2024].

    W L PROJECT (2023). Upload Image to Firebase in Android Studio Jetpack Compose | Upload Image Jetpack Compose | #4.
    [online] YouTube. Available at: https://www.youtube.com/watch?v=7ZBCvf2sh5E [Accessed 18 Apr. 2024].

    Ghinna, R. (2020). Android : How to make circle image with Glide.
    [online] Medium. Available at: https://rizkaghina29.medium.com/android-how-to-make-circle-image-with-glide-bb0b50fbbda [Accessed 18 Apr. 2024].

*/
class ProfilePictureActivity : AppCompatActivity() {
    // global variables for buttons firebase and images
    private lateinit var image: ImageView
    private lateinit var uri: Uri
    private lateinit var btnSkip: Button
    private lateinit var btnUpload: Button
    private lateinit var btnGallery: Button
    private lateinit var auth: FirebaseAuth
    private var storageRef = Firebase.storage

    // contract for when a picture is uploaded from the camera
    private val contract = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            // Update URI after taking picture
            uri = Uri.fromFile(File(filesDir, "camera_photos.png"))
            image.setImageURI(null)
            image.setImageURI(uri)
            Glide.with(this)
                .load(uri)
                .transform(CircleCrop())
                .into(image)
        // no photo has been chosen
        } else {
            Toast.makeText(this, "Picture not uploaded. Your profile picture remains unchanged.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_picture)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // firebase auth, firestore and storage setup
        auth = Firebase.auth
        val db = FirebaseFirestore.getInstance()
        storageRef = FirebaseStorage.getInstance()

        // setup buttons and image view
        btnUpload = findViewById(R.id.btnUpload)
        btnSkip = findViewById(R.id.btnSkip)
        btnGallery = findViewById(R.id.btnGallery)
        image = findViewById(R.id.imgProfilePicture)

        // if does not have permissions obtain them else do nothing
        if (!hasPermissions(baseContext)) {
            // Request permissions if not granted
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }

        // take the uri of the image from the camera and use in the college
        val btnCamera = findViewById<Button>(R.id.btnCamera)
        btnCamera.setBackgroundColor(Color.rgb(245, 20, 43))
        btnCamera.setOnClickListener {
            // Update URI before launching camera
            uri = createImageUri()
            contract.launch(uri)
        }

        // display the image chosen from the gallery if any
        val galleryImage =
            registerForActivityResult(ActivityResultContracts.GetContent()) {
                image.setImageURI(it)
                if (it != null) {
                    uri = it
                    Glide.with(this)
                        .load(uri)
                        .transform(CircleCrop())
                        .into(image)
                }
            }

        // launch gallery on button click
        btnGallery.setBackgroundColor(Color.rgb(245, 20, 43))
        btnGallery.setOnClickListener {
            galleryImage.launch("image/*")
        }

        // use the default profile picture
        btnSkip.setBackgroundColor(Color.rgb(245, 20, 43))
        btnSkip.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }

        // upload the photo to firebase storage and firestore on the current user id
        btnUpload.setBackgroundColor(Color.rgb(245, 20, 43))
        btnUpload.setOnClickListener {
            // create a unique image name based on current time in milliseconds
            storageRef.getReference("Images").child(System.currentTimeMillis().toString())
                .putFile(uri).addOnSuccessListener { task ->
                    task.metadata!!.reference!!.downloadUrl.addOnSuccessListener {

                        // photo id for the firestore db
                        val userUID = auth.currentUser!!.uid
                        val profilePictureData = hashMapOf(
                            "photoURL" to it.toString()
                        )

                        // add the profile picture url to the firestore, without affecting the other data
                        // for the current user that signed up
                        db.collection("users").document(userUID)
                            .set(profilePictureData, SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this,
                                    "Profile Picture Successfully Uploaded",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // navigate to signin page upon succesfull phtot upload
                                val intent = Intent(this, SignInActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    this,
                                    "FAILURE TO UPLOAD PROFILE PICTURE",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
        }
    }

    // create a temporary image uri, when it is uploaded so it can be displayed before being uploaded to firebase
    private fun createImageUri(): Uri {
        val image = File(filesDir, "camera_photos.png")
        return FileProvider.getUriForFile(
            this,
            "com.coding.fitnessapp.FileProvider",
            image
        )
    }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle Permission granted/rejected
        var permissionGranted = true
        // check to make sure all permissions are granted
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
        }
        if (!permissionGranted) {
            Toast.makeText(
                baseContext, "Permission request denied", Toast.LENGTH_SHORT
            ).show()
        }
    }

    // object to store needed permissions
    companion object {
        // try and find the camera permission and older devices try and find the write to external storage permission
        private val REQUIRED_PERMISSIONS = mutableListOf(
            android.Manifest.permission.CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        // check if the permissions have been granted true or false
        fun hasPermissions(context: Context) = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}