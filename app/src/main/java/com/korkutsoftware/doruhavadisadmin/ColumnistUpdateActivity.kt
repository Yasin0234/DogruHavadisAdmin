package com.korkutsoftware.doruhavadisadmin

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.korkutsoftware.doruhavadisadmin.models.Columnist

class ColumnistUpdateActivity : AppCompatActivity() {

    private var columnistId: String? = null
    private var selectedWriterImageUri: Uri? = null
    private var selectedContentImageUri: Uri? = null
    private var currentWriterImageUrl: String = ""
    private var currentContentImageUrl: String = ""
    
    private lateinit var ivWriterPreview: ImageView
    private lateinit var ivContentPreview: ImageView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var db: FirebaseFirestore

    private val pickWriterImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedWriterImageUri = uri
            ivWriterPreview.setImageURI(uri)
            ivWriterPreview.scaleType = ImageView.ScaleType.CENTER_CROP
            ivWriterPreview.imageTintList = null
        }
    }

    private val pickContentImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedContentImageUri = uri
            ivContentPreview.setImageURI(uri)
            ivContentPreview.scaleType = ImageView.ScaleType.CENTER_CROP
            ivContentPreview.imageTintList = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_columnist_update)

        columnistId = intent.getStringExtra("columnistId")
        db = FirebaseFirestore.getInstance("dogruhavadis")

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ivWriterPreview = findViewById(R.id.iv_writer_preview)
        ivContentPreview = findViewById(R.id.iv_content_preview)
        progressIndicator = findViewById(R.id.progress_indicator)

        findViewById<MaterialCardView>(R.id.card_add_writer_image).setOnClickListener {
            pickWriterImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<MaterialCardView>(R.id.card_add_content_image).setOnClickListener {
            pickContentImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<MaterialButton>(R.id.btn_update_columnist).setOnClickListener {
            validateAndUpdate()
        }

        fetchColumnistDetails()
    }

    private fun fetchColumnistDetails() {
        val id = columnistId ?: return
        progressIndicator.visibility = android.view.View.VISIBLE

        db.collection("columnists").document(id).get()
            .addOnSuccessListener { document ->
                progressIndicator.visibility = android.view.View.GONE
                if (document.exists()) {
                    val columnist = document.toObject(Columnist::class.java)
                    columnist?.let { populateFields(it) }
                }
            }
            .addOnFailureListener {
                progressIndicator.visibility = android.view.View.GONE
                Toast.makeText(this, "Hata: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateFields(columnist: Columnist) {
        findViewById<TextInputEditText>(R.id.et_writer_name).setText(columnist.writerName)
        findViewById<TextInputEditText>(R.id.et_columnist_title).setText(columnist.title)
        findViewById<TextInputEditText>(R.id.et_columnist_content).setText(columnist.content)

        currentWriterImageUrl = columnist.writerImageUrl
        currentContentImageUrl = columnist.contentImageUrl

        if (currentWriterImageUrl.isNotEmpty()) {
            Glide.with(this).load(currentWriterImageUrl).centerCrop().into(ivWriterPreview)
            ivWriterPreview.imageTintList = null
        }

        if (currentContentImageUrl.isNotEmpty()) {
            Glide.with(this).load(currentContentImageUrl).centerCrop().into(ivContentPreview)
            ivContentPreview.imageTintList = null
        }
    }

    private fun validateAndUpdate() {
        val writerName = findViewById<TextInputEditText>(R.id.et_writer_name).text.toString()
        val title = findViewById<TextInputEditText>(R.id.et_columnist_title).text.toString()
        val content = findViewById<TextInputEditText>(R.id.et_columnist_content).text.toString()

        if (writerName.isEmpty() || title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
            return
        }

        progressIndicator.visibility = android.view.View.VISIBLE
        findViewById<MaterialButton>(R.id.btn_update_columnist).isEnabled = false

        uploadImagesIfNeeded(writerName, title, content)
    }

    private fun uploadImagesIfNeeded(writerName: String, title: String, content: String) {
        val storageRef = FirebaseStorage.getInstance().reference
        var writerUrl = currentWriterImageUrl
        var contentUrl = currentContentImageUrl

        if (selectedWriterImageUri != null) {
            val ref = storageRef.child("columnist_writers/${System.currentTimeMillis()}.jpg")
            ref.putFile(selectedWriterImageUri!!).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    writerUrl = uri.toString()
                    uploadContentImageIfNeeded(writerName, title, content, writerUrl, contentUrl)
                }
            }.addOnFailureListener { handleError(it.message) }
        } else {
            uploadContentImageIfNeeded(writerName, title, content, writerUrl, contentUrl)
        }
    }

    private fun uploadContentImageIfNeeded(writerName: String, title: String, content: String, writerUrl: String, contentUrl: String) {
        var finalContentUrl = contentUrl
        if (selectedContentImageUri != null) {
            val ref = FirebaseStorage.getInstance().reference.child("columnist_contents/${System.currentTimeMillis()}.jpg")
            ref.putFile(selectedContentImageUri!!).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    finalContentUrl = uri.toString()
                    updateFirestore(writerName, title, content, writerUrl, finalContentUrl)
                }
            }.addOnFailureListener { handleError(it.message) }
        } else {
            updateFirestore(writerName, title, content, writerUrl, finalContentUrl)
        }
    }

    private fun updateFirestore(writerName: String, title: String, content: String, writerUrl: String, contentUrl: String) {
        val id = columnistId ?: return
        val updates = hashMapOf<String, Any>(
            "writerName" to writerName,
            "title" to title,
            "content" to content,
            "writerImageUrl" to writerUrl,
            "contentImageUrl" to contentUrl
        )

        db.collection("columnists").document(id).update(updates)
            .addOnSuccessListener {
                progressIndicator.visibility = android.view.View.GONE
                Toast.makeText(this, "Köşe yazısı güncellendi", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { handleError(it.message) }
    }

    private fun handleError(message: String?) {
        progressIndicator.visibility = android.view.View.GONE
        findViewById<MaterialButton>(R.id.btn_update_columnist).isEnabled = true
        Toast.makeText(this, "Hata: $message", Toast.LENGTH_SHORT).show()
    }
}
