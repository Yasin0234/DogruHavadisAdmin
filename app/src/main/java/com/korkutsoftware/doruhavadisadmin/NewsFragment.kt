package com.korkutsoftware.doruhavadisadmin

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.korkutsoftware.doruhavadisadmin.models.News
import com.korkutsoftware.doruhavadisadmin.pdf.NewspaperPdfActivity
import okhttp3.*
import java.io.IOException

class NewsFragment : Fragment() {

    private lateinit var dbAdapter: NewsAdapter
    private lateinit var xmlAdapter: XmlNewsAdapter 
    
    private val db = FirebaseFirestore.getInstance("dogruhavadis")
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddNews: FloatingActionButton
    private lateinit var filterPanel: LinearLayout
    private lateinit var chipGroupCities: ChipGroup
    private lateinit var spinnerDistricts: Spinner

    private val southeastCities = mapOf(
        "Adıyaman" to listOf("Merkez", "Besni", "Çelikhan", "Gerger", "Gölbaşı", "Kahta", "Samsat", "Sincik", "Tut"),
        "Batman" to listOf("Merkez", "Beşiri", "Gercüş", "Hasankeyf", "Kozluk", "Sason"),
        "Diyarbakır" to listOf("Bağlar", "Bismil", "Çermik", "Çınar", "Çüngüş", "Dicle", "Eğil", "Ergani", "Hani", "Hazro", "Kayapınar", "Kocaköy", "Kulp", "Lice", "Silvan", "Sur", "Yenişehir"),
        "Gaziantep" to listOf("Şahinbey", "Şehitkamil", "Araban", "İslahiye", "Karkamış", "Nizip", "Nurdağı", "Oğuzeli", "Yavuzeli"),
        "Kilis" to listOf("Merkez", "Elbeyli", "Musabeyli", "Polateli"),
        "Mardin" to listOf("Artuklu", "Dargeçit", "Derik", "Kızıltepe", "Mazıdağı", "Midyat", "Nusaybin", "Ömerli", "Savur", "Yeşilli"),
        "Siirt" to listOf("Merkez", "Baykan", "Eruh", "Kurtalan", "Pervari", "Şirvan", "Tillo"),
        "Şanlıurfa" to listOf("Eyyübiye", "Haliliye", "Karaköprü", "Akçakale", "Birecik", "Bozova", "Ceylanpınar", "Halfeti", "Harran", "Hilvan", "Siverek", "Suruç", "Viranşehir"),
        "Şırnak" to listOf("Merkez", "Beytüşşebap", "Cizre", "Güçlükonak", "İdil", "Silopi", "Uludere")
    )

    private val citySlugs = mapOf(
        "Gaziantep" to "gaziantep",
        "Şanlıurfa" to "sanliurfa",
        "Diyarbakır" to "diyarbakir",
        "Mardin" to "mardin",
        "Batman" to "batman",
        "Adıyaman" to "adiyaman",
        "Siirt" to "siirt",
        "Şırnak" to "sirnak",
        "Kilis" to "kilis"
    )

    private val okHttpClient = OkHttpClient()
    private val rssParser = RssParser()
    private var currentCity = "Gaziantep"
    private var currentDistrict = "Şahinbey"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_news, container, false)

        recyclerView = view.findViewById(R.id.rv_news)
        fabAddNews = view.findViewById(R.id.fab_add_news)
        filterPanel = view.findViewById(R.id.ll_filter_panel)
        chipGroupCities = view.findViewById(R.id.chipGroup_cities)
        spinnerDistricts = view.findViewById(R.id.spinner_districts)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout_news)

        recyclerView.layoutManager = LinearLayoutManager(context)
        
        setupDbAdapter()
        setupXmlAdapter()
        setupTabs(tabLayout)
        setupCityChips()

        fetchDbNews()

        fabAddNews.setOnClickListener {
            val intent = Intent(requireContext(), NewsAdd::class.java)
            startActivity(intent)
        }

        view.findViewById<View>(R.id.fab_generate_pdf).setOnClickListener {
            val intent = Intent(requireContext(), NewspaperPdfActivity::class.java)
            startActivity(intent)
        }

        return view
    }

    private fun setupDbAdapter() {
        dbAdapter = NewsAdapter(emptyList()) { news, action ->
            when (action) {
                0 -> { // Update
                    val intent = Intent(requireContext(), NewsUpdateActivity::class.java)
                    intent.putExtra("newsId", news.id)
                    startActivity(intent)
                }
                1 -> { // Delete
                    showDeleteConfirmation(news)
                }
                2 -> { // Prepare Share (Edit)
                    val bundle = Bundle().apply {
                        putString("newsId", news.id)
                    }
                    val navOptions = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.navigation_news, false)
                        .setLaunchSingleTop(true)
                        .build()
                    findNavController().navigate(R.id.navigation_edit, bundle, navOptions)
                }
            }
        }
        recyclerView.adapter = dbAdapter
    }

    private fun setupXmlAdapter() {
        xmlAdapter = XmlNewsAdapter(emptyList()) { xmlNews ->
            openNewsAddWithXmlData(xmlNews)
        }
    }

    private fun openNewsAddWithXmlData(xmlNews: XmlNews) {
        val intent = Intent(requireContext(), NewsAdd::class.java).apply {
            putExtra("xml_title", xmlNews.title)
            putExtra("xml_description", xmlNews.description)
            putExtra("xml_content", xmlNews.fullContent)
            putExtra("xml_image", xmlNews.imageUrl)
            putExtra("xml_city", xmlNews.city)
            putExtra("xml_district", xmlNews.district)
            putExtra("xml_link", xmlNews.link)
        }
        startActivity(intent)
    }

    private fun setupTabs(tabLayout: TabLayout) {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Veritabanı
                        filterPanel.visibility = View.GONE
                        fabAddNews.visibility = View.VISIBLE
                        recyclerView.adapter = dbAdapter
                        fetchDbNews()
                    }
                    1 -> { // XML Kaynak
                        filterPanel.visibility = View.VISIBLE
                        fabAddNews.visibility = View.GONE
                        recyclerView.adapter = xmlAdapter
                        fetchRssNews(currentCity, currentDistrict)
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun fetchRssNews(city: String, district: String) {
        val slug = citySlugs[city] ?: "adiyaman"
        val url = "https://rss.haberler.com/RssNew.aspx?kategori=$slug"
        val cityDistricts = southeastCities[city] ?: emptyList()

        val request = Request.Builder().url(url).build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Bağlantı hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { responseBody ->
                    try {
                        var items = rssParser.parse(responseBody.byteStream(), city, district, cityDistricts)
                        
                        if (district.isNotEmpty() && district != "Tüm İlçeler") {
                            val filtered = items.filter { item ->
                                containsKeyword(item.title, district) ||
                                containsKeyword(item.description, district) ||
                                containsKeyword(item.fullContent, district) ||
                                containsKeyword(item.district, district)
                            }
                            if (filtered.isNotEmpty()) {
                                items = filtered
                            } else {
                                activity?.runOnUiThread {
                                    Toast.makeText(context, "$district ilçesine ait haber bulunamadı.", Toast.LENGTH_SHORT).show()
                                }
                                items = emptyList()
                            }
                        }

                        checkIfNewsSaved(items)
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Parse hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun containsKeyword(text: String, keyword: String): Boolean {
        if (text.isEmpty() || keyword.isEmpty()) return false
        val normalizedText = normalizeForSearch(text)
        val normalizedKeyword = normalizeForSearch(keyword)
        return normalizedText.contains(normalizedKeyword)
    }

    private fun normalizeForSearch(str: String): String {
        val locale = java.util.Locale.forLanguageTag("tr-TR")
        return str.lowercase(locale)
            .replace('ı', 'i')
            .replace('i', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ö', 'o')
            .replace('ç', 'c')
    }

    private fun checkIfNewsSaved(items: List<XmlNews>) {
        db.collection("news").get().addOnSuccessListener { snapshots ->
            val savedUrls = snapshots.documents.map { it.getString("sourceUrl") ?: "" }
            val savedTitles = snapshots.documents.map { it.getString("title") ?: "" }

            items.forEach { item ->
                if (savedUrls.contains(item.link) || savedTitles.contains(item.title)) {
                    item.isSaved = true
                }
            }

            activity?.runOnUiThread {
                xmlAdapter.updateData(items)
            }
        }
    }

    private fun setupCityChips() {
        southeastCities.keys.forEach { cityName ->
            val chip = Chip(requireContext()).apply {
                text = cityName
                isCheckable = true
                setChipBackgroundColorResource(R.color.white)
                setChipStrokeColorResource(R.color.outline_variant)
                setChipStrokeWidthResource(R.dimen.chip_stroke_width)
            }
            chipGroupCities.addView(chip)
        }

        chipGroupCities.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds[0])
                currentCity = selectedChip.text.toString()
                updateDistrictsSpinner(currentCity)
            }
        }
        
        // İlk ili seçili yap
        (chipGroupCities.getChildAt(0) as? Chip)?.isChecked = true
    }

    private fun updateDistrictsSpinner(cityName: String) {
        val cityDistricts = southeastCities[cityName] ?: emptyList()
        val spinnerDistrictsList = listOf("Tüm İlçeler") + cityDistricts
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerDistrictsList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDistricts.adapter = adapter

        spinnerDistricts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentDistrict = spinnerDistrictsList[position]
                fetchRssNews(currentCity, currentDistrict)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showDeleteConfirmation(news: News) {
        AlertDialog.Builder(requireContext())
            .setTitle("Haberi Sil")
            .setMessage("'${news.title}' başlıklı haberi silmek istediğinize emin misiniz?")
            .setPositiveButton("Sil") { _, _ -> deleteDbNews(news) }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun deleteDbNews(news: News) {
        db.collection("news").document(news.id)
            .delete()
            .addOnSuccessListener { Toast.makeText(context, "Haber silindi", Toast.LENGTH_SHORT).show() }
    }

    private fun fetchDbNews() {
        db.collection("news")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e == null && snapshots != null) {
                    val newsList = snapshots.toObjects(News::class.java)
                    dbAdapter.updateData(newsList)
                }
            }
    }
}