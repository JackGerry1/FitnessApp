package com.example.fitnessapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

class ProfilePictureActivity : AppCompatActivity() {
    private lateinit var image: ImageView
    private lateinit var btnGallery: Button
    private lateinit var btnUpload: Button
    private lateinit var btnSkip: Button

    private lateinit var auth: FirebaseAuth
    private var storageRef = Firebase.storage

    private lateinit var uri: Uri
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile_picture)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = Firebase.auth
        val db = FirebaseFirestore.getInstance()
        storageRef = FirebaseStorage.getInstance()

        image = findViewById(R.id.imgProfilePicture)
        btnGallery = findViewById(R.id.btnGallery)
        btnUpload = findViewById(R.id.btnUpload)
        btnSkip = findViewById(R.id.btnSkip)

        val galleryImage =
            registerForActivityResult(ActivityResultContracts.GetContent(), ActivityResultCallback {
                image.setImageURI(it)
                if (it != null) {
                    uri = it
                    Glide.with(this)
                        .load(uri)
                        .transform(CircleCrop()) // Apply circular transformation
                        .into(image)
                }
            })

        btnSkip.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnGallery.setOnClickListener {
            galleryImage.launch("image/*")
        }

        // TODO: create the ability to take picture from camera and upload it.

        btnUpload.setOnClickListener {
            storageRef.getReference("Images").child(System.currentTimeMillis().toString())
                .putFile(uri).addOnSuccessListener { task ->
                task.metadata!!.reference!!.downloadUrl.addOnSuccessListener {
                    val userUID = auth.currentUser!!.uid
                    val profilePictureData = hashMapOf(
                        "photoURL" to it.toString()
                    )

                    db.collection("users").document(userUID)
                        .set(profilePictureData, SetOptions.merge())
                        .addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "Profile Picture Successfully Uploaded",
                                Toast.LENGTH_SHORT
                            ).show()

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
}