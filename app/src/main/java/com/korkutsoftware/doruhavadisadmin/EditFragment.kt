package com.korkutsoftware.doruhavadisadmin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.korkutsoftware.doruhavadisadmin.models.News
import java.io.File
import java.io.FileOutputStream

class EditFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance("dogruhavadis")
    private var newsList = listOf<News>()
    private var selectedNews: News? = null
    
    private lateinit var spinnerSelectNews: AutoCompleteTextView
    private lateinit var spinnerProvince: AutoCompleteTextView
    private lateinit var spinnerDistrict: AutoCompleteTextView
    
    private lateinit var tvTemplateLocation: TextView
    private lateinit var tvTemplateTitle: TextView
    private lateinit var ivTemplateBackground: ImageView
    private lateinit var layoutTemplate: View
    private lateinit var locationPill: View
    private lateinit var writerPill: View
    private lateinit var ivTemplateWriter: ImageView
    private lateinit var tvTemplateWriterName: TextView

    private var isColumnist: Boolean = false

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_edit, container, false)

        spinnerSelectNews = view.findViewById(R.id.spinner_select_news)
        spinnerProvince = view.findViewById(R.id.spinner_province)
        spinnerDistrict = view.findViewById(R.id.spinner_district)
        
        tvTemplateLocation = view.findViewById(R.id.tv_template_location)
        tvTemplateTitle = view.findViewById(R.id.tv_template_title)
        ivTemplateBackground = view.findViewById(R.id.iv_template_background)
        layoutTemplate = view.findViewById(R.id.layout_news_template)
        locationPill = view.findViewById(R.id.location_pill)
        writerPill = view.findViewById(R.id.writer_pill)
        ivTemplateWriter = view.findViewById(R.id.iv_template_writer)
        tvTemplateWriterName = view.findViewById(R.id.tv_template_writer_name)

        setupSpinners()
        fetchNews()

        // Handle newsId from NewsFragment or ColumnistFragment
        val id = arguments?.getString("newsId")
        val type = arguments?.getString("contentType") ?: "news"

        if (id != null) {
            if (type == "columnist") {
                loadSpecificColumnist(id)
            } else {
                loadSpecificNews(id)
            }
        }

        view.findViewById<View>(R.id.btn_share_instagram).setOnClickListener { 
            shareTemplate()
        }

        return view
    }

    private fun loadSpecificColumnist(id: String) {
        db.collection("columnists").document(id).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val columnist = document.toObject(com.korkutsoftware.doruhavadisadmin.models.Columnist::class.java)
                columnist?.let {
                    isColumnist = true
                    // Convert columnist to a News object for basic template info
                    selectedNews = News(
                        id = it.id,
                        title = it.title,
                        description = it.content,
                        mediaUrls = if (it.contentImageUrl.isNotEmpty()) listOf(it.contentImageUrl) else emptyList()
                    )
                    
                    tvTemplateWriterName.text = it.writerName.uppercase()
                    if (it.writerImageUrl.isNotEmpty()) {
                        Glide.with(this).load(it.writerImageUrl).circleCrop().into(ivTemplateWriter)
                    }
                    
                    updateTemplatePreview()
                    spinnerSelectNews.setText(it.title, false)
                }
            }
        }
    }

    private fun setupSpinners() {
        val provinceAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, provinceDistrictMap.keys.toTypedArray())
        spinnerProvince.setAdapter(provinceAdapter)

        spinnerProvince.setOnItemClickListener { parent, _, position, _ ->
            val province = parent.getItemAtPosition(position) as String
            val districts = provinceDistrictMap[province] ?: emptyArray()
            val districtAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, districts)
            spinnerDistrict.setAdapter(districtAdapter)
            spinnerDistrict.setText("", false)
            updateTemplateLocation()
        }

        spinnerDistrict.setOnItemClickListener { _, _, _, _ ->
            updateTemplateLocation()
        }
    }

    private fun fetchNews() {
        db.collection("news").orderBy("timestamp", Query.Direction.DESCENDING).limit(20).get()
            .addOnSuccessListener { snapshots ->
                newsList = snapshots.toObjects(News::class.java)
                val newsTitles = newsList.map { it.title }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, newsTitles)
                spinnerSelectNews.setAdapter(adapter)
            }

        spinnerSelectNews.setOnItemClickListener { parent, _, position, _ ->
            val title = parent.getItemAtPosition(position) as String
            selectedNews = newsList.find { it.title == title }
            isColumnist = false // Regular news from spinner
            updateTemplatePreview()
        }
    }

    private fun loadSpecificNews(id: String) {
        db.collection("news").document(id).get().addOnSuccessListener { document ->
            if (document.exists()) {
                selectedNews = document.toObject(News::class.java)
                isColumnist = false
                updateTemplatePreview()
                selectedNews?.let {
                    spinnerSelectNews.setText(it.title, false)
                }
            }
        }
    }

    private fun updateTemplatePreview() {
        selectedNews?.let { news ->
            tvTemplateTitle.text = news.title
            if (news.mediaUrls.isNotEmpty()) {
                Glide.with(this).load(news.mediaUrls[0]).into(ivTemplateBackground)
            }
            
            if (isColumnist) {
                locationPill.visibility = View.GONE
                writerPill.visibility = View.VISIBLE
            } else {
                writerPill.visibility = View.GONE
                // Auto-fill province/district from news data
                if (news.province.isNotEmpty()) {
                    spinnerProvince.setText(news.province, false)
                    val districts = provinceDistrictMap[news.province] ?: emptyArray()
                    spinnerDistrict.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, districts))
                    if (news.district.isNotEmpty()) {
                        spinnerDistrict.setText(news.district, false)
                    }
                }
                updateTemplateLocation()
            }
        }
    }

    private fun updateTemplateLocation() {
        val province = spinnerProvince.text.toString().uppercase()
        val district = spinnerDistrict.text.toString().uppercase()
        
        if (province.isNotEmpty() || district.isNotEmpty()) {
            locationPill.visibility = View.VISIBLE
            if (province.isNotEmpty() && district.isNotEmpty()) {
                tvTemplateLocation.text = "$province - $district"
            } else {
                tvTemplateLocation.text = province.ifEmpty { district }
            }
        } else {
            locationPill.visibility = View.GONE
        }
    }

    private fun shareTemplate() {
        if (selectedNews == null) {
            Toast.makeText(context, "Lütfen önce bir haber seçin", Toast.LENGTH_SHORT).show()
            return
        }

        layoutTemplate.post {
            if (layoutTemplate.width <= 0 || layoutTemplate.height <= 0) {
                Toast.makeText(context, "Şablon henüz hazır değil veya geçersiz boyutta", Toast.LENGTH_SHORT).show()
                return@post
            }

            try {
                val bitmap = createBitmapFromView(layoutTemplate)
                val uri = saveBitmapToCache(bitmap)
                
                val shareText = generateShareText(selectedNews!!)
                
                val bundle = Bundle().apply {
                    putString("imageUri", uri.toString())
                    putString("shareText", shareText)
                }
                
                findNavController().navigate(R.id.navigation_tests, bundle)
                
            } catch (e: Exception) {
                Toast.makeText(context, "Hazırlanırken hata oluştu: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private fun toSlug(text: String): String {
        val trMap = mapOf(
            'ç' to 'c', 'Ç' to 'c',
            'ğ' to 'g', 'Ğ' to 'g',
            'ı' to 'i', 'I' to 'i', 'İ' to 'i',
            'ö' to 'o', 'Ö' to 'o',
            'ş' to 's', 'Ş' to 's',
            'ü' to 'u', 'Ü' to 'u'
        )
        val replaced = text.map { trMap[it] ?: it }.joinToString("")
        return replaced.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("[\\s-]+"), "-")
            .trim('-')
    }

    private fun generateShareText(news: News): String {
        val title = news.title
        val slug = toSlug(title)
        val link = if (slug.isNotEmpty()) {
            "https://www.dogruhavadis.com/#/haber/${slug}_${news.id}"
        } else {
            "https://www.dogruhavadis.com/#/haber/${news.id}"
        }
        
        // Temel hashtag'ler
        val baseHashtags = mutableListOf("dogruhavadis", "haber", "sondakika", "güncel", "gazete")
        
        // Başlıktan kelime çekme (opsiyonel ama daha iyi olur)
        val keywords = title.split(" ")
            .filter { it.length > 5 }
            .map { it.lowercase().replace(Regex("[^a-zçğıöşü]"), "") }
            .filter { it.isNotEmpty() }

        val allTags = (keywords + baseHashtags).distinct().take(5)
        val hashtagString = allTags.joinToString(" ") { "#$it" }
        
        return "$title\n\n$link\n\n$hashtagString"
    }

    private fun createBitmapFromView(view: View): Bitmap {
        val targetWidth = 1080
        val targetHeight = 1350
        val viewWidth = view.width.coerceAtLeast(1)
        val viewHeight = view.height.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scaleX = targetWidth.toFloat() / viewWidth.toFloat()
        val scaleY = targetHeight.toFloat() / viewHeight.toFloat()
        canvas.scale(scaleX, scaleY)

        view.draw(canvas)
        return bitmap
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val cachePath = File(requireContext().cacheDir, "images")
        cachePath.mkdirs()
        val imageFile = File(cachePath, "news_insta_share.png")
        val stream = FileOutputStream(imageFile)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        
        return FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", imageFile)
    }
}
