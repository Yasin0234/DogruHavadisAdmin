package com.korkutsoftware.doruhavadisadmin.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.korkutsoftware.doruhavadisadmin.R
import com.korkutsoftware.doruhavadisadmin.models.News
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class NewspaperPdfActivity : AppCompatActivity() {

    private lateinit var btnViewAllNewspapers: MaterialButton
    private lateinit var chipGroupDate: ChipGroup
    private lateinit var chipLast7Days: Chip
    private lateinit var chipLast14Days: Chip
    private lateinit var chipLast30Days: Chip
    private lateinit var tvNewsSelectionHeader: TextView
    private lateinit var btnToggleSelectAll: MaterialButton
    private lateinit var spinnerMainHeadline: Spinner
    private lateinit var pbFetchNews: ProgressBar
    private lateinit var tvEmptyNewsWarning: TextView
    private lateinit var rvNewsSelection: RecyclerView
    private lateinit var radioGroupPageSize: RadioGroup
    private lateinit var radioA3: RadioButton
    private lateinit var switchIncludeColumnists: SwitchMaterial
    private lateinit var btnAddAd: MaterialButton
    private lateinit var layoutAdsContainer: LinearLayout
    private lateinit var btnGeneratePdf: MaterialButton
    private lateinit var layoutLoading: LinearLayout
    private lateinit var tvLoadingStatus: TextView
    private lateinit var cardPreview: MaterialCardView
    private lateinit var webViewPreview: WebView
    private lateinit var btnPublishToWebsite: MaterialButton
    private lateinit var btnSharePdf: MaterialButton
    private lateinit var btnPrintPdf: MaterialButton

    private lateinit var newsSelectionAdapter: NewsSelectionAdapter
    private var isAllSelected = true
    private var currentFetchedNewsList: List<News> = emptyList()
    private var currentStartDateMillis: Long = 0L
    private var currentEndDateMillis: Long = 0L

    private var generatedPdfFile: File? = null
    private var generatedHtmlContent: String? = null

    // Advertisements Data Structure
    data class AdViewHolder(
        val rootView: View,
        val tvHeaderNumber: TextView,
        val etTitle: TextInputEditText,
        val etDesc: TextInputEditText,
        val etPhone: TextInputEditText,
        val ivPreview: ImageView,
        val btnSelectImage: MaterialButton,
        val btnRemoveImage: MaterialButton,
        val btnRemoveAd: MaterialButton,
        var imageUri: Uri? = null
    )

    private val adViewHolderList = mutableListOf<AdViewHolder>()
    private var currentSelectingAdIndex: Int = -1

    private val pickAdImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && currentSelectingAdIndex in adViewHolderList.indices) {
            val adHolder = adViewHolderList[currentSelectingAdIndex]
            adHolder.imageUri = uri
            adHolder.ivPreview.visibility = View.VISIBLE
            adHolder.ivPreview.setImageURI(uri)
            adHolder.btnRemoveImage.visibility = View.VISIBLE
            adHolder.btnSelectImage.text = "Görseli Değiştir"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_newspaper_pdf)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        btnViewAllNewspapers = findViewById(R.id.btnViewAllNewspapers)
        chipGroupDate = findViewById(R.id.chipGroupDate)
        chipLast7Days = findViewById(R.id.chipLast7Days)
        chipLast14Days = findViewById(R.id.chipLast14Days)
        chipLast30Days = findViewById(R.id.chipLast30Days)
        
        tvNewsSelectionHeader = findViewById(R.id.tvNewsSelectionHeader)
        btnToggleSelectAll = findViewById(R.id.btnToggleSelectAll)
        spinnerMainHeadline = findViewById(R.id.spinnerMainHeadline)
        pbFetchNews = findViewById(R.id.pbFetchNews)
        tvEmptyNewsWarning = findViewById(R.id.tvEmptyNewsWarning)
        rvNewsSelection = findViewById(R.id.rvNewsSelection)

        radioGroupPageSize = findViewById(R.id.radioGroupPageSize)
        radioA3 = findViewById(R.id.radioA3)
        switchIncludeColumnists = findViewById(R.id.switchIncludeColumnists)
        
        btnAddAd = findViewById(R.id.btnAddAd)
        layoutAdsContainer = findViewById(R.id.layoutAdsContainer)

        btnGeneratePdf = findViewById(R.id.btnGeneratePdf)
        layoutLoading = findViewById(R.id.layoutLoading)
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        cardPreview = findViewById(R.id.cardPreview)
        webViewPreview = findViewById(R.id.webViewPreview)
        btnPublishToWebsite = findViewById(R.id.btnPublishToWebsite)
        btnSharePdf = findViewById(R.id.btnSharePdf)
        btnPrintPdf = findViewById(R.id.btnPrintPdf)

        btnViewAllNewspapers.setOnClickListener {
            val intent = Intent(this, DigitalNewspapersActivity::class.java)
            startActivity(intent)
        }

        setupRecyclerView()
        setupDateListeners()
        setupAdSection()

        webViewPreview.settings.javaScriptEnabled = true
        webViewPreview.settings.builtInZoomControls = true
        webViewPreview.settings.displayZoomControls = false
        webViewPreview.settings.useWideViewPort = true
        webViewPreview.settings.loadWithOverviewMode = true

        btnGeneratePdf.setOnClickListener {
            startPdfGeneration()
        }

        btnPublishToWebsite.setOnClickListener {
            publishNewspaperToWebsite()
        }

        btnSharePdf.setOnClickListener {
            sharePdfFile()
        }

        btnPrintPdf.setOnClickListener {
            printPdfDocument()
        }

        // Fetch news for default selected date range (Last 7 Days)
        loadNewsForSelectedDateRange()
    }

    private fun setupRecyclerView() {
        newsSelectionAdapter = NewsSelectionAdapter {
            updateSelectionHeader()
            syncSpinnerSelection()
        }
        rvNewsSelection.layoutManager = LinearLayoutManager(this)
        rvNewsSelection.adapter = newsSelectionAdapter

        btnToggleSelectAll.setOnClickListener {
            if (isAllSelected) {
                newsSelectionAdapter.deselectAll()
                btnToggleSelectAll.text = "Tümünü Seç"
                isAllSelected = false
            } else {
                newsSelectionAdapter.selectAll()
                btnToggleSelectAll.text = "Tümünü Kaldır"
                isAllSelected = true
            }
            updateSelectionHeader()
        }
    }

    private fun syncSpinnerSelection() {
        val selectedHeadlineId = newsSelectionAdapter.getMainHeadlineNewsId() ?: return
        val position = currentFetchedNewsList.indexOfFirst { it.id == selectedHeadlineId }
        if (position >= 0 && spinnerMainHeadline.selectedItemPosition != position) {
            spinnerMainHeadline.setSelection(position)
        }
    }

    private fun setupMainHeadlineSpinner(newsList: List<News>) {
        if (newsList.isEmpty()) {
            spinnerMainHeadline.adapter = null
            return
        }

        val titles = newsList.map { "⭐ ${it.title}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, titles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMainHeadline.adapter = adapter

        val initialHeadlineId = newsSelectionAdapter.getMainHeadlineNewsId()
        val initialIndex = newsList.indexOfFirst { it.id == initialHeadlineId }.let { if (it >= 0) it else 0 }
        spinnerMainHeadline.setSelection(initialIndex)

        spinnerMainHeadline.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in newsList.indices) {
                    val news = newsList[position]
                    newsSelectionAdapter.setMainHeadline(news.id)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupAdSection() {
        btnAddAd.setOnClickListener {
            addAdInputView()
        }
        // Add 1 default advertisement slot
        addAdInputView()
    }

    private fun addAdInputView() {
        if (adViewHolderList.size >= 5) {
            Toast.makeText(this, "En fazla 5 reklam ekleyebilirsiniz.", Toast.LENGTH_SHORT).show()
            return
        }

        val inflater = LayoutInflater.from(this)
        val adView = inflater.inflate(R.layout.item_ad_input, layoutAdsContainer, false)

        val tvHeaderNumber = adView.findViewById<TextView>(R.id.tvAdHeaderNumber)
        val etTitle = adView.findViewById<TextInputEditText>(R.id.etAdTitle)
        val etDesc = adView.findViewById<TextInputEditText>(R.id.etAdDescription)
        val etPhone = adView.findViewById<TextInputEditText>(R.id.etAdPhone)
        val ivPreview = adView.findViewById<ImageView>(R.id.ivAdImagePreview)
        val btnSelectImage = adView.findViewById<MaterialButton>(R.id.btnSelectAdImage)
        val btnRemoveImage = adView.findViewById<MaterialButton>(R.id.btnRemoveAdImage)
        val btnRemoveAd = adView.findViewById<MaterialButton>(R.id.btnRemoveAd)

        val index = adViewHolderList.size
        tvHeaderNumber.text = "📢 Reklam #${index + 1}"

        val holder = AdViewHolder(
            rootView = adView,
            tvHeaderNumber = tvHeaderNumber,
            etTitle = etTitle,
            etDesc = etDesc,
            etPhone = etPhone,
            ivPreview = ivPreview,
            btnSelectImage = btnSelectImage,
            btnRemoveImage = btnRemoveImage,
            btnRemoveAd = btnRemoveAd
        )

        btnSelectImage.setOnClickListener {
            val currentIdx = adViewHolderList.indexOf(holder)
            if (currentIdx != -1) {
                currentSelectingAdIndex = currentIdx
                pickAdImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        btnRemoveImage.setOnClickListener {
            holder.imageUri = null
            holder.ivPreview.setImageDrawable(null)
            holder.ivPreview.visibility = View.GONE
            holder.btnRemoveImage.visibility = View.GONE
            holder.btnSelectImage.text = "🖼️ Görsel Ekle"
        }

        btnRemoveAd.setOnClickListener {
            if (adViewHolderList.size <= 1) {
                Toast.makeText(this, "En az 1 reklam alanı bulunmalıdır.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            layoutAdsContainer.removeView(adView)
            adViewHolderList.remove(holder)
            updateAdHeaders()
        }

        layoutAdsContainer.addView(adView)
        adViewHolderList.add(holder)
        updateAdHeaders()
    }

    private fun updateAdHeaders() {
        adViewHolderList.forEachIndexed { i, holder ->
            holder.tvHeaderNumber.text = "📢 Reklam #${i + 1}"
        }
    }

    private fun updateSelectionHeader() {
        val selected = newsSelectionAdapter.getSelectedCount()
        val total = newsSelectionAdapter.getTotalCount()
        tvNewsSelectionHeader.text = "📰 Eklenecek Haberler ($selected/$total)"
    }

    private fun setupDateListeners() {
        chipGroupDate.setOnCheckedStateChangeListener { _, _ ->
            loadNewsForSelectedDateRange()
        }
    }

    private fun loadNewsForSelectedDateRange() {
        val now = System.currentTimeMillis()
        val daysCount = when {
            chipLast14Days.isChecked -> 14
            chipLast30Days.isChecked -> 30
            else -> 7
        }
        currentStartDateMillis = now - (daysCount * 24 * 60 * 60 * 1000L)
        currentEndDateMillis = now

        pbFetchNews.visibility = View.VISIBLE
        rvNewsSelection.visibility = View.GONE
        tvEmptyNewsWarning.visibility = View.GONE

        lifecycleScope.launch {
            val generator = NewspaperPdfGenerator(this@NewspaperPdfActivity)
            val newsList = generator.fetchNewsForDateRange(currentStartDateMillis, currentEndDateMillis)
            currentFetchedNewsList = newsList

            pbFetchNews.visibility = View.GONE
            if (newsList.isEmpty()) {
                tvEmptyNewsWarning.visibility = View.VISIBLE
                newsSelectionAdapter.updateList(emptyList())
                setupMainHeadlineSpinner(emptyList())
            } else {
                rvNewsSelection.visibility = View.VISIBLE
                newsSelectionAdapter.updateList(newsList)
                setupMainHeadlineSpinner(newsList)
                isAllSelected = true
                btnToggleSelectAll.text = "Tümünü Kaldır"
            }
            updateSelectionHeader()
        }
    }

    private fun startPdfGeneration() {
        val daysCount = when {
            chipLast14Days.isChecked -> 14
            chipLast30Days.isChecked -> 30
            else -> 7
        }

        val selectedNewsIds = newsSelectionAdapter.getSelectedNewsIds()
        if (selectedNewsIds.isEmpty() && newsSelectionAdapter.getTotalCount() > 0) {
            Toast.makeText(this, "Lütfen gazeteye basılacak en az 1 haber seçin.", Toast.LENGTH_SHORT).show()
            return
        }

        val mainHeadlineNewsId = newsSelectionAdapter.getMainHeadlineNewsId()
        val coverNewsIds = newsSelectionAdapter.getCoverNewsIds()
        val pageSize = if (radioA3.isChecked) "A3" else "A4"

        val adsList = adViewHolderList.map { holder ->
            NewspaperPdfGenerator.AdItem(
                title = holder.etTitle.text.toString().ifEmpty { "REKLAM ALANI" },
                description = holder.etDesc.text.toString().ifEmpty { "Doğru Havadis Gazetesi'nde reklamınızın yer almasını isterseniz bizimle iletişime geçin." },
                phone = holder.etPhone.text.toString().ifEmpty { "0505 000 00 00" },
                imageUri = holder.imageUri
            )
        }

        val options = NewspaperPdfGenerator.PdfOptions(
            startDateMillis = currentStartDateMillis,
            endDateMillis = currentEndDateMillis,
            pageSize = pageSize,
            includeColumnists = switchIncludeColumnists.isChecked,
            selectedNewsIds = selectedNewsIds,
            mainHeadlineNewsId = mainHeadlineNewsId,
            coverNewsIds = coverNewsIds,
            ads = adsList
        )

        layoutLoading.visibility = View.VISIBLE
        btnGeneratePdf.isEnabled = false
        cardPreview.visibility = View.GONE
        tvLoadingStatus.text = "Seçilen $daysCount günün haberleri toplanıyor ve gazeteye işleniyor..."

        lifecycleScope.launch {
            val generator = NewspaperPdfGenerator(this@NewspaperPdfActivity)
            generator.generatePdf(options, object : NewspaperPdfGenerator.PdfCallback {
                override fun onSuccess(pdfFile: File, htmlContent: String) {
                    runOnUiThread {
                        layoutLoading.visibility = View.GONE
                        btnGeneratePdf.isEnabled = true
                        generatedPdfFile = pdfFile
                        generatedHtmlContent = htmlContent

                        cardPreview.visibility = View.VISIBLE
                        webViewPreview.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)

                        Toast.makeText(this@NewspaperPdfActivity, "Gazete PDF'i başarıyla oluşturuldu!", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: Exception) {
                    runOnUiThread {
                        layoutLoading.visibility = View.GONE
                        btnGeneratePdf.isEnabled = true
                        Toast.makeText(this@NewspaperPdfActivity, "Hata: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
    }

    private fun publishNewspaperToWebsite() {
        val file = generatedPdfFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Önce gazete PDF'ini oluşturmalısınız.", Toast.LENGTH_SHORT).show()
            return
        }

        layoutLoading.visibility = View.VISIBLE
        tvLoadingStatus.text = "Dijital gazete PDF'i yükleniyor ve siteye yayınlanıyor..."
        btnPublishToWebsite.isEnabled = false

        val storageRef = FirebaseStorage.getInstance().reference
        val newspaperRef = storageRef.child("newspapers/gazete_${System.currentTimeMillis()}.pdf")

        newspaperRef.putFile(Uri.fromFile(file))
            .addOnSuccessListener {
                newspaperRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val locale = Locale.forLanguageTag("tr-TR")
                    val dateFormat = SimpleDateFormat("dd MMMM yyyy", locale)
                    val startDateStr = dateFormat.format(Date(currentStartDateMillis))
                    val endDateStr = dateFormat.format(Date(currentEndDateMillis))
                    val issueDateStr = "$startDateStr - $endDateStr"

                    val headlineId = newsSelectionAdapter.getMainHeadlineNewsId()
                    val headlineNews = currentFetchedNewsList.firstOrNull { it.id == headlineId }
                    val headlineTitleStr = headlineNews?.title ?: ""

                    val pageSize = if (radioA3.isChecked) "A3" else "A4"
                    val selectedCount = newsSelectionAdapter.getSelectedCount()

                    val digitalNewspaper = mapOf(
                        "title" to "Doğru Havadis Gazetesi - $issueDateStr",
                        "issueDate" to issueDateStr,
                        "headlineTitle" to headlineTitleStr,
                        "pdfUrl" to downloadUrl.toString(),
                        "newsCount" to selectedCount,
                        "pageSize" to pageSize,
                        "timestamp" to System.currentTimeMillis(),
                        "publisherName" to "Doğru Havadis Admin"
                    )

                    FirebaseFirestore.getInstance("dogruhavadis")
                        .collection("digital_newspapers")
                        .add(digitalNewspaper)
                        .addOnSuccessListener {
                            layoutLoading.visibility = View.GONE
                            btnPublishToWebsite.isEnabled = true
                            Toast.makeText(this, "🎉 Dijital gazete siteye başarıyla yayınlandı!", Toast.LENGTH_LONG).show()

                            // Open published digital newspapers list
                            val intent = Intent(this, DigitalNewspapersActivity::class.java)
                            startActivity(intent)
                        }
                        .addOnFailureListener { e ->
                            layoutLoading.visibility = View.GONE
                            btnPublishToWebsite.isEnabled = true
                            Toast.makeText(this, "Firestore kayıt hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }.addOnFailureListener { e ->
                    layoutLoading.visibility = View.GONE
                    btnPublishToWebsite.isEnabled = true
                    Toast.makeText(this, "Download URL alma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                layoutLoading.visibility = View.GONE
                btnPublishToWebsite.isEnabled = true
                Toast.makeText(this, "PDF yükleme hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sharePdfFile() {
        val file = generatedPdfFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Önce gazete PDF'ini oluşturmalısınız.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Doğru Havadis Haftalık Gazetesi")
                putExtra(Intent.EXTRA_TEXT, "Doğru Havadis Haftalık Gazetesi PDF baskısı ekte sunulmuştur.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Gazeteyi Paylaş"))
        } catch (e: Exception) {
            Toast.makeText(this, "Paylaşılırken hata oluştu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun printPdfDocument() {
        if (generatedHtmlContent == null) {
            Toast.makeText(this, "Önce gazete PDF'ini oluşturmalısınız.", Toast.LENGTH_SHORT).show()
            return
        }

        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        val printAdapter = webViewPreview.createPrintDocumentAdapter("DogruHavadisGazete")
        val jobName = "Doğru Havadis Gazetesi Print"
        printManager.print(jobName, printAdapter, null)
    }
}
