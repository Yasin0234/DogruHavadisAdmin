package com.korkutsoftware.doruhavadisadmin

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.korkutsoftware.doruhavadisadmin.models.Columnist
import java.util.*

class ColumnistAddActivity : AppCompatActivity() {

    private var selectedWriterImageUri: Uri? = null
    private var selectedContentImageUri: Uri? = null
    private lateinit var ivWriterPreview: ImageView
    private lateinit var ivContentPreview: ImageView
    private lateinit var progressIndicator: LinearProgressIndicator

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
        setContentView(R.layout.activity_columnist_add)

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

        findViewById<MaterialButton>(R.id.btn_add_columnist).setOnClickListener {
            validateAndSave()
        }
    }

    private fun validateAndSave() {
        val writerName = findViewById<TextInputEditText>(R.id.et_writer_name).text.toString()
        val title = findViewById<TextInputEditText>(R.id.et_columnist_title).text.toString()
        val content = findViewById<TextInputEditText>(R.id.et_columnist_content).text.toString()

        if (writerName.isEmpty() || title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedWriterImageUri == null) {
            Toast.makeText(this, "Lütfen yazar fotoğrafı seçin", Toast.LENGTH_SHORT).show()
            return
        }

        uploadImagesAndSave(writerName, title, content)
    }

    private fun uploadImagesAndSave(writerName: String, title: String, content: String) {
        progressIndicator.visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.btn_add_columnist).isEnabled = false

        val storageRef = FirebaseStorage.getInstance().reference
        var writerImageUrl = ""
        var contentImageUrl = ""

        // Upload Writer Image
        val writerImageRef = storageRef.child("columnist_writers/${System.currentTimeMillis()}.jpg")
        writerImageRef.putFile(selectedWriterImageUri!!).addOnSuccessListener {
            writerImageRef.downloadUrl.addOnSuccessListener { writerUri ->
                writerImageUrl = writerUri.toString()
                
                // Check if content image is selected
                if (selectedContentImageUri != null) {
                    val contentImageRef = storageRef.child("columnist_contents/${System.currentTimeMillis()}.jpg")
                    contentImageRef.putFile(selectedContentImageUri!!).addOnSuccessListener {
                        contentImageRef.downloadUrl.addOnSuccessListener { contentUri ->
                            contentImageUrl = contentUri.toString()
                            saveToFirestore(writerName, title, content, writerImageUrl, contentImageUrl)
                        }
                    }.addOnFailureListener {
                        handleError(it.message)
                    }
                } else {
                    saveToFirestore(writerName, title, content, writerImageUrl, "")
                }
            }
        }.addOnFailureListener {
            handleError(it.message)
        }
    }

    private fun handleError(message: String?) {
        progressIndicator.visibility = View.GONE
        findViewById<MaterialButton>(R.id.btn_add_columnist).isEnabled = true
        Toast.makeText(this, "Yükleme başarısız: $message", Toast.LENGTH_SHORT).show()
    }

    private fun saveToFirestore(writerName: String, title: String, content: String, writerUrl: String, contentUrl: String) {
        val firestore = FirebaseFirestore.getInstance("dogruhavadis")
        val docRef = firestore.collection("columnists").document()
        val columnist = Columnist(
            id = docRef.id,
            writerName = writerName,
            title = title,
            content = content,
            writerImageUrl = writerUrl,
            contentImageUrl = contentUrl
        )

        docRef.set(columnist).addOnSuccessListener {
            progressIndicator.visibility = View.GONE
            Toast.makeText(this, "Köşe yazısı başarıyla kaydedildi", Toast.LENGTH_LONG).show()
            finish()
        }.addOnFailureListener {
            progressIndicator.visibility = View.GONE
            findViewById<MaterialButton>(R.id.btn_add_columnist).isEnabled = true
            Toast.makeText(this, "Firestore hatası: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}