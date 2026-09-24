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
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.korkutsoftware.doruhavadisadmin.models.News
import java.util.*

class NewsUpdateActivity : AppCompatActivity() {

    private var newsId: String? = null
    private lateinit var db: FirebaseFirestore
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var layoutMedia: LinearLayout
    
    private val mediaItems = mutableListOf<MediaItem>()

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                if (mediaItems.size < 4) {
                    mediaItems.add(MediaItem(uri = uri))
                }
            }
            updateMediaPreview()
        }
    }

    data class MediaItem(
        val url: String? = null,
        val uri: Uri? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_news_update)

        newsId = intent.getStringExtra("newsId")
        db = FirebaseFirestore.getInstance("dogruhavadis")

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        progressIndicator = findViewById(R.id.progress_indicator)
        layoutMedia = findViewById(R.id.layout_media_preview)

        findViewById<MaterialCardView>(R.id.card_add_media).setOnClickListener {
            if (mediaItems.size < 4) {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            } else {
                Toast.makeText(this, "Maksimum 4 görsel ekleyebilirsiniz", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up categories
        val categories = arrayOf("Gündem", "Siyaset", "Eğitim", "Türkiye", "Asayiş", "Spor", "Ekonomi", "Teknoloji", "Sağlık", "Dünya")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        findViewById<AutoCompleteTextView>(R.id.spinner_category).setAdapter(adapter)

        // Set up Provinces and Districts
        setupProvinceDistrictSpinners()

        fetchNewsDetails()

        findViewById<MaterialButton>(R.id.btn_update_news).setOnClickListener {
            validateAndUpdateNews()
        }
    }

    private val provinceDistrictMap = mapOf(
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

    private fun setupProvinceDistrictSpinners() {
        val provinceSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_province)
        val districtSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_district)

        val provinceAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, provinceDistrictMap.keys.toTypedArray())
        provinceSpinner.setAdapter(provinceAdapter)

        provinceSpinner.setOnItemClickListener { parent, _, position, _ ->
            val selectedProvince = parent.getItemAtPosition(position) as String
            updateDistrictAdapter(selectedProvince, "")
        }
    }

    private fun updateDistrictAdapter(province: String, currentDistrict: String) {
        val districtSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_district)
        val districts = provinceDistrictMap[province] ?: emptyArray()
        val districtAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, districts)
        districtSpinner.setAdapter(districtAdapter)
        districtSpinner.setText(currentDistrict, false)
    }

    private fun fetchNewsDetails() {
        val id = newsId ?: return
        progressIndicator.visibility = View.VISIBLE

        db.collection("news").document(id).get()
            .addOnSuccessListener { document ->
                progressIndicator.visibility = View.GONE
                if (document.exists()) {
                    val news = document.toObject(News::class.java)
                    news?.let { populateFields(it) }
                }
            }
            .addOnFailureListener {
                progressIndicator.visibility = View.GONE
                Toast.makeText(this, "Hata: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateFields(news: News) {
        findViewById<TextInputEditText>(R.id.et_title).setText(news.title)
        findViewById<AutoCompleteTextView>(R.id.spinner_category).setText(news.category, false)
        findViewById<AutoCompleteTextView>(R.id.spinner_province).setText(news.province, false)
        updateDistrictAdapter(news.province, news.district)
        
        findViewById<TextInputEditText>(R.id.et_description).setText(news.description)
        findViewById<TextInputEditText>(R.id.et_admin).setText(news.adminName)
        findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_headline).isChecked = news.isHeadline

        // Populate media items
        mediaItems.clear()
        news.mediaUrls.forEach { url ->
            mediaItems.add(MediaItem(url = url))
        }
        updateMediaPreview()
    }

    private fun updateMediaPreview() {
        // Keep the "Add" button
        val addButton = layoutMedia.getChildAt(0)
        layoutMedia.removeAllViews()
        layoutMedia.addView(addButton)

        mediaItems.forEachIndexed { index, item ->
            val container = layoutInflater.inflate(R.layout.item_media_preview, layoutMedia, false)
            val imageView = container.findViewById<ImageView>(R.id.iv_preview)
            val btnDelete = container.findViewById<View>(R.id.btn_delete_media)

            if (item.url != null) {
                Glide.with(this).load(item.url).centerCrop().into(imageView)
            } else if (item.uri != null) {
                imageView.setImageURI(item.uri)
            }

            btnDelete.setOnClickListener {
                mediaItems.removeAt(index)
                updateMediaPreview()
            }

            layoutMedia.addView(container)
        }
    }

    private fun validateAndUpdateNews() {
        val title = findViewById<TextInputEditText>(R.id.et_title).text.toString()
        val category = findViewById<AutoCompleteTextView>(R.id.spinner_category).text.toString()
        val province = findViewById<AutoCompleteTextView>(R.id.spinner_province).text.toString()
        val district = findViewById<AutoCompleteTextView>(R.id.spinner_district).text.toString()
        val description = findViewById<TextInputEditText>(R.id.et_description).text.toString()
        val adminName = findViewById<TextInputEditText>(R.id.et_admin).text.toString()
        val isHeadline = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_headline).isChecked

        if (title.isEmpty() || description.isEmpty() || category.isEmpty() || province.isEmpty() || district.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
            return
        }

        if (mediaItems.isEmpty()) {
            Toast.makeText(this, "Lütfen en az 1 görsel ekleyin", Toast.LENGTH_SHORT).show()
            return
        }

        progressIndicator.visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.btn_update_news).isEnabled = false

        val existingUrls = mediaItems.filter { it.url != null }.map { it.url!! }.toMutableList()
        val newUris = mediaItems.filter { it.uri != null }.map { it.uri!! }

        if (newUris.isEmpty()) {
            saveToFirestore(title, category, province, district, description, adminName, isHeadline, existingUrls)
        } else {
            uploadNewMedia(title, category, province, district, description, adminName, isHeadline, existingUrls, newUris)
        }
    }

    private fun uploadNewMedia(title: String, category: String, province: String, district: String, description: String, admin: String, headline: Boolean, existingUrls: MutableList<String>, newUris: List<Uri>) {
        val storageRef = FirebaseStorage.getInstance().reference
        var uploadCount = 0

        newUris.forEachIndexed { index, uri ->
            val fileRef = storageRef.child("news_media/${System.currentTimeMillis()}_$index")
            fileRef.putFile(uri).addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    existingUrls.add(downloadUrl.toString())
                    uploadCount++
                    if (uploadCount == newUris.size) {
                        saveToFirestore(title, category, province, district, description, admin, headline, existingUrls)
                    }
                }
            }.addOnFailureListener {
                progressIndicator.visibility = View.GONE
                findViewById<MaterialButton>(R.id.btn_update_news).isEnabled = true
                Toast.makeText(this, "Medya yükleme başarısız: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToFirestore(title: String, category: String, province: String, district: String, description: String, admin: String, headline: Boolean, urls: List<String>) {
        val id = newsId ?: return
        val updates = hashMapOf<String, Any>(
            "title" to title,
            "category" to category,
            "province" to province,
            "district" to district,
            "description" to description,
            "adminName" to admin,
            "isHeadline" to headline,
            "mediaUrls" to urls
        )

        db.collection("news").document(id)
            .update(updates)
            .addOnSuccessListener {
                progressIndicator.visibility = View.GONE
                Toast.makeText(this, "Haber başarıyla güncellendi", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                progressIndicator.visibility = View.GONE
                findViewById<MaterialButton>(R.id.btn_update_news).isEnabled = true
                Toast.makeText(this, "Güncelleme hatası: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
