package com.korkutsoftware.doruhavadisadmin

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.korkutsoftware.doruhavadisadmin.models.News
import com.bumptech.glide.Glide
import java.util.*

class NewsAdd : AppCompatActivity() {

    private val selectedMediaUris = mutableListOf<Uri>()
    private var xmlImageUrl: String? = null
    private var xmlSourceUrl: String? = null
    private lateinit var layoutMedia: LinearLayout
    private lateinit var progressIndicator: LinearProgressIndicator

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.isNotEmpty()) {
            selectedMediaUris.clear()
            selectedMediaUris.addAll(uris)
            updateMediaPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_news_add)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        layoutMedia = findViewById(R.id.layout_media)
        progressIndicator = findViewById(R.id.progress_indicator)
        
        fetchAdminName()
        handleIncomingIntent()
        
        val btnAddMedia = findViewById<MaterialCardView>(R.id.card_add_media)
        btnAddMedia.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }

        // Set up categories
        val categories = arrayOf("Gündem", "Siyaset", "Eğitim", "Türkiye", "Asayiş", "Spor", "Ekonomi", "Teknoloji", "Sağlık", "Dünya")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        findViewById<AutoCompleteTextView>(R.id.spinner_category).setAdapter(categoryAdapter)

        // Set up Provinces and Districts
        setupProvinceDistrictSpinners()

        findViewById<MaterialButton>(R.id.btn_add_news).setOnClickListener {
            validateAndSaveNews()
        }
    }

    private fun handleIncomingIntent() {
        intent?.let {
            val title = it.getStringExtra("xml_title")
            val description = it.getStringExtra("xml_description")
            val content = it.getStringExtra("xml_content")
            val imageUrl = it.getStringExtra("xml_image")
            val city = it.getStringExtra("xml_city")
            val district = it.getStringExtra("xml_district")
            xmlSourceUrl = it.getStringExtra("xml_link")

            if (title != null) {
                findViewById<TextInputEditText>(R.id.et_title).setText(title)
                
                val fullDescription = if (!content.isNullOrEmpty()) {
                    "$description\n\n$content"
                } else {
                    description
                }
                findViewById<TextInputEditText>(R.id.et_description).setText(fullDescription)
                
                if (!imageUrl.isNullOrEmpty()) {
                    xmlImageUrl = imageUrl
                    addRemoteImageToPreview(imageUrl)
                }

                // Fill City and District
                val provinceSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_province)
                provinceSpinner.setText(city, false)
                
                // Trigger district update manually
                updateDistrictsForProvince(city ?: "")
                findViewById<AutoCompleteTextView>(R.id.spinner_district).setText(district, false)
            }
        }
    }

    private fun addRemoteImageToPreview(url: String) {
        val cardView = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(250, 250).apply {
                setMargins(0, 0, 16, 0)
            }
            radius = 24f
        }
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        Glide.with(this).load(url).into(imageView)
        cardView.addView(imageView)
        layoutMedia.addView(cardView)
    }

    private fun setupProvinceDistrictSpinners() {
        val provinceDistrictMap = mapOf(
            "Adıyaman" to arrayOf("Merkez", "Besni", "Çelikhan", "Gerger", "Gölbaşı", "Kahta", "Samsat", "Sincik", "Tut"),
            "Batman" to arrayOf("Merkez", "Beşiri", "Gercüş", "Hasankeyf", "Kozluk", "Sason"),
            "Diyarbakır" to arrayOf("Bağlar", "Bismil", "Çermik", "Çınar", "Çüngüş", "Dicle", "Eğil", "Ergani", "Hani", "Hazro", "Kayapınar", "Kocaköy", "Kulp", "Lice", "Silvan", "Sur", "Yenişehir"),
            "Gaziantep" to arrayOf("Şahinbey", "Şehitkamil", "Araban", "İslahiye", "Karkamış", "Nizip", "Nurdağı", "Oğuzeli", "Yavuzeli"),
            "Kilis" to arrayOf("Merkez", "Elbeyli", "Musabeyli", "Polateli"),
            "Mardin" to arrayOf("Artuklu", "Dargeçit", "Derik", "Kızıltepe", "Mazıdağı", "Midyat", "Nusaybin", "Ömerli", "Savur", "Yeşilli"),
            "Siirt" to arrayOf("Merkez", "Baykan", "Eruh", "Kurtalan", "Pervari", "Şirvan", "Tillo"),
            "Şanlıurfa" to arrayOf("Eyyübiye", "Haliliye", "Karaköprü", "Akçakale", "Birecik", "Bozova", "Ceylanpınar", "Halfeti", "Harran", "Hilvan", "Siverek", "Suruç", "Viranşehir"),
            "Şırnak" to arrayOf("Merkez", "Beytüşşebap", "Cizre", "Güçlükonak", "İdil", "Silopi", "Uludere")
        )

        val provinceSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_province)

        val provinceAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, provinceDistrictMap.keys.toTypedArray())
        provinceSpinner.setAdapter(provinceAdapter)

        provinceSpinner.setOnItemClickListener { parent, _, position, _ ->
            val selectedProvince = parent.getItemAtPosition(position) as String
            updateDistrictsForProvince(selectedProvince)
        }
    }

    private fun updateDistrictsForProvince(province: String) {
        val provinceDistrictMap = mapOf(
            "Adıyaman" to arrayOf("Merkez", "Besni", "Çelikhan", "Gerger", "Gölbaşı", "Kahta", "Samsat", "Sincik", "Tut"),
            "Batman" to arrayOf("Merkez", "Beşiri", "Gercüş", "Hasankeyf", "Kozluk", "Sason"),
            "Diyarbakır" to arrayOf("Bağlar", "Bismil", "Çermik", "Çınar", "Çüngüş", "Dicle", "Eğil", "Ergani", "Hani", "Hazro", "Kayapınar", "Kocaköy", "Kulp", "Lice", "Silvan", "Sur", "Yenişehir"),
            "Gaziantep" to arrayOf("Şahinbey", "Şehitkamil", "Araban", "İslahiye", "Karkamış", "Nizip", "Nurdağı", "Oğuzeli", "Yavuzeli"),
            "Kilis" to arrayOf("Merkez", "Elbeyli", "Musabeyli", "Polateli"),
            "Mardin" to arrayOf("Artuklu", "Dargeçit", "Derik", "Kızıltepe", "Mazıdağı", "Midyat", "Nusaybin", "Ömerli", "Savur", "Yeşilli"),
            "Siirt" to arrayOf("Merkez", "Baykan", "Eruh", "Kurtalan", "Pervari", "Şirvan", "Tillo"),
            "Şanlıurfa" to arrayOf("Eyyübiye", "Haliliye", "Karaköprü", "Akçakale", "Birecik", "Bozova", "Ceylanpınar", "Halfeti", "Harran", "Hilvan", "Siverek", "Suruç", "Viranşehir"),
            "Şırnak" to arrayOf("Merkez", "Beytüşşebap", "Cizre", "Güçlükonak", "İdil", "Silopi", "Uludere")
        )
        val districts = provinceDistrictMap[province] ?: emptyArray()
        val districtSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_district)
        val districtAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, districts)
        districtSpinner.setAdapter(districtAdapter)
        districtSpinner.setText("", false)
    }

    private fun fetchAdminName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance("dogruhavadis")
        
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val username = document.getString("username")
                    findViewById<TextInputEditText>(R.id.et_admin).setText(username)
                }
            }
    }

    private fun updateMediaPreview() {
        // Keep the "Add" button, remove others
        val addButton = layoutMedia.getChildAt(0)
        layoutMedia.removeAllViews()
        layoutMedia.addView(addButton)

        // If there's an XML image, add it back first
        xmlImageUrl?.let { addRemoteImageToPreview(it) }

        selectedMediaUris.forEach { uri ->
            val cardView = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(250, 250).apply {
                    setMargins(0, 0, 16, 0)
                }
                radius = 24f
            }
            val imageView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
            }
            cardView.addView(imageView)
            layoutMedia.addView(cardView)
        }
    }

    private fun validateAndSaveNews() {
        val title = findViewById<TextInputEditText>(R.id.et_title).text.toString()
        val category = findViewById<AutoCompleteTextView>(R.id.spinner_category).text.toString()
        val province = findViewById<AutoCompleteTextView>(R.id.spinner_province).text.toString()
        val district = findViewById<AutoCompleteTextView>(R.id.spinner_district).text.toString()
        val description = findViewById<TextInputEditText>(R.id.et_description).text.toString()
        val adminName = findViewById<TextInputEditText>(R.id.et_admin).text.toString()
        val isHeadline = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_headline).isChecked

        if (selectedMediaUris.isEmpty() && xmlImageUrl == null) {
            Toast.makeText(this, "Lütfen en az 1 görsel veya video seçin", Toast.LENGTH_SHORT).show()
            return
        }
        if (title.isEmpty() || description.isEmpty() || category.isEmpty() || province.isEmpty() || district.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
            return
        }

        saveNewsToFirebase(title, category, province, district, description, adminName, isHeadline)
    }

    private fun saveNewsToFirebase(title: String, category: String, province: String, district: String, description: String, admin: String, headline: Boolean) {
        progressIndicator.visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.btn_add_news).isEnabled = false

        val storageRef = FirebaseStorage.getInstance().reference
        val uploadedUrls = mutableListOf<String>()
        
        // If there's an XML image, add it to the list
        xmlImageUrl?.let { uploadedUrls.add(it) }

        if (selectedMediaUris.isEmpty()) {
            // No local files to upload, just save to Firestore
            saveToFirestore(title, category, province, district, description, admin, headline, uploadedUrls)
            return
        }

        var uploadCount = 0
        selectedMediaUris.forEachIndexed { index, uri ->
            val fileRef = storageRef.child("news_media/${System.currentTimeMillis()}_$index")
            fileRef.putFile(uri).addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    uploadedUrls.add(downloadUrl.toString())
                    uploadCount++
                    if (uploadCount == selectedMediaUris.size) {
                        saveToFirestore(title, category, province, district, description, admin, headline, uploadedUrls)
                    }
                }
            }.addOnFailureListener {
                progressIndicator.visibility = View.GONE
                findViewById<MaterialButton>(R.id.btn_add_news).isEnabled = true
                Toast.makeText(this, "Medya yükleme başarısız: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToFirestore(title: String, category: String, province: String, district: String, description: String, admin: String, headline: Boolean, urls: List<String>) {
        val firestore = FirebaseFirestore.getInstance("dogruhavadis")
        val docRef = firestore.collection("news").document()
        val news = mapOf(
            "id" to docRef.id,
            "title" to title,
            "category" to category,
            "province" to province,
            "district" to district,
            "description" to description,
            "adminName" to admin,
            "isHeadline" to headline,
            "mediaUrls" to urls,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "sourceUrl" to (xmlSourceUrl ?: "")
        )

        docRef.set(news).addOnSuccessListener {
            progressIndicator.visibility = View.GONE
            Toast.makeText(this, "Haber başarıyla kaydedildi", Toast.LENGTH_LONG).show()
            finish()
        }.addOnFailureListener {
            progressIndicator.visibility = View.GONE
            findViewById<MaterialButton>(R.id.btn_add_news).isEnabled = true
            Toast.makeText(this, "Firestore hatası: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}