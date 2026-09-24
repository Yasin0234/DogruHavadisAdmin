package com.korkutsoftware.doruhavadisadmin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SiteStatusFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance("dogruhavadis")

    private lateinit var switchMaintenance: MaterialSwitch
    private lateinit var cardStatusBadge: MaterialCardView
    private lateinit var tvStatusBadge: TextView
    private lateinit var etTitle: TextInputEditText
    private lateinit var etDetail: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var tilDate: TextInputLayout
    private lateinit var tvPreviewTitle: TextView
    private lateinit var tvPreviewDetail: TextView
    private lateinit var tvPreviewDate: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_site_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        loadSiteStatus()
    }

    private fun initViews(view: View) {
        switchMaintenance = view.findViewById(R.id.switch_maintenance_mode)
        cardStatusBadge = view.findViewById(R.id.card_status_badge)
        tvStatusBadge = view.findViewById(R.id.tv_status_badge)
        etTitle = view.findViewById(R.id.et_maintenance_title)
        etDetail = view.findViewById(R.id.et_maintenance_detail)
        etDate = view.findViewById(R.id.et_maintenance_date)
        tilDate = view.findViewById(R.id.til_maintenance_date)
        tvPreviewTitle = view.findViewById(R.id.tv_preview_title)
        tvPreviewDetail = view.findViewById(R.id.tv_preview_detail)
        tvPreviewDate = view.findViewById(R.id.tv_preview_date)
        btnSave = view.findViewById(R.id.btn_save_site_status)
        progressBar = view.findViewById(R.id.pb_site_status)
    }

    private fun setupListeners() {
        switchMaintenance.setOnCheckedChangeListener { _, isChecked ->
            updateStatusUI(isChecked)
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview(
                    etTitle.text.toString().trim(),
                    etDetail.text.toString().trim(),
                    etDate.text.toString().trim()
                )
            }
        }

        etTitle.addTextChangedListener(textWatcher)
        etDetail.addTextChangedListener(textWatcher)
        etDate.addTextChangedListener(textWatcher)

        tilDate.setEndIconOnClickListener {
            showDateTimePicker()
        }

        etDate.setOnClickListener {
            showDateTimePicker()
        }

        btnSave.setOnClickListener {
            saveSiteStatus()
        }
    }

    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)

                TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)

                        val sdf = SimpleDateFormat("d MMMM yyyy - HH:mm", Locale.forLanguageTag("tr-TR"))
                        val formattedDate = sdf.format(calendar.time)
                        etDate.setText(formattedDate)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadSiteStatus() {
        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false

        db.collection("configWeb").document("siteBakim")
            .get()
            .addOnSuccessListener { webDoc ->
                if (!isAdded) return@addOnSuccessListener
                if (webDoc != null && webDoc.exists()) {
                    applyDocumentData(webDoc)
                } else {
                    db.collection("configAdm").document("siteBakim")
                        .get()
                        .addOnSuccessListener { admDoc ->
                            if (!isAdded) return@addOnSuccessListener
                            if (admDoc != null && admDoc.exists()) {
                                applyDocumentData(admDoc)
                            } else {
                                progressBar.visibility = View.GONE
                                btnSave.isEnabled = true
                                updateStatusUI(isBakimActive = false)
                            }
                        }
                        .addOnFailureListener {
                            if (!isAdded) return@addOnFailureListener
                            progressBar.visibility = View.GONE
                            btnSave.isEnabled = true
                            updateStatusUI(isBakimActive = false)
                        }
                }
            }
            .addOnFailureListener {
                db.collection("configAdm").document("siteBakim")
                    .get()
                    .addOnSuccessListener { admDoc ->
                        if (!isAdded) return@addOnSuccessListener
                        if (admDoc != null && admDoc.exists()) {
                            applyDocumentData(admDoc)
                        } else {
                            progressBar.visibility = View.GONE
                            btnSave.isEnabled = true
                            updateStatusUI(isBakimActive = false)
                        }
                    }
                    .addOnFailureListener {
                        if (!isAdded) return@addOnFailureListener
                        progressBar.visibility = View.GONE
                        btnSave.isEnabled = true
                        updateStatusUI(isBakimActive = false)
                    }
            }
    }

    private fun applyDocumentData(doc: DocumentSnapshot) {
        progressBar.visibility = View.GONE
        btnSave.isEnabled = true

        val bakimDurumu = doc.getBoolean("siteBakim")
            ?: doc.getBoolean("bakimDurumu")
            ?: doc.getBoolean("isMaintenance")
            ?: false

        val bakimBaslik = doc.getString("siteBaslik")
            ?: doc.getString("bakimBaslik")
            ?: doc.getString("title")
            ?: "Sistem Bakımda"

        val bakimDetay = doc.getString("siteDetay")
            ?: doc.getString("bakimDetay")
            ?: doc.getString("detail")
            ?: "Sizlere daha iyi bir deneyim sunabilmek için kısa süreli bir bakım çalışması yapıyoruz."

        val bakimTarih = doc.getString("siteTarih")
            ?: doc.getString("bakimTarih")
            ?: doc.getString("date")
            ?: "23 Temmuz 2026 - 18:00"

        switchMaintenance.isChecked = bakimDurumu
        etTitle.setText(bakimBaslik)
        etDetail.setText(bakimDetay)
        etDate.setText(bakimTarih)

        updateStatusUI(bakimDurumu)
        updatePreview(bakimBaslik, bakimDetay, bakimTarih)
    }

    private fun saveSiteStatus() {
        val isBakimActive = switchMaintenance.isChecked
        val title = etTitle.text.toString().trim()
        val detail = etDetail.text.toString().trim()
        val date = etDate.text.toString().trim()

        if (isBakimActive && (title.isEmpty() || detail.isEmpty())) {
            Toast.makeText(context, "Lütfen bakım başlığını ve detayını doldurun.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false

        val data = hashMapOf<String, Any>(
            "bakimDurumu" to isBakimActive,
            "siteBakim" to isBakimActive,
            "isMaintenance" to isBakimActive,

            "bakimBaslik" to title,
            "siteBaslik" to title,
            "title" to title,

            "bakimDetay" to detail,
            "siteDetay" to detail,
            "detail" to detail,

            "bakimTarih" to date,
            "siteTarih" to date,
            "date" to date,

            "updatedAt" to Timestamp.now()
        )

        val batch = db.batch()
        val refWeb = db.collection("configWeb").document("siteBakim")
        val refAdmSite = db.collection("configAdm").document("siteBakim")
        val refAdmBakim = db.collection("configAdm").document("adminBakim")

        batch.set(refWeb, data, SetOptions.merge())
        batch.set(refAdmSite, data, SetOptions.merge())

        // Web bakımı kaydedilirken Admin panelinin kilitlenmemesi için adminBakim dökümanındaki bakimDurumu false yapılıyor
        val admData = hashMapOf<String, Any>(
            "bakimDurumu" to false
        )
        batch.set(refAdmBakim, admData, SetOptions.merge())

        batch.commit()
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
                Toast.makeText(context, "Web sitesi bakım durumu başarıyla güncellendi!", Toast.LENGTH_LONG).show()
                updateStatusUI(isBakimActive)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
                Toast.makeText(context, "Kaydedilirken hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateStatusUI(isBakimActive: Boolean) {
        if (!isAdded) return
        if (isBakimActive) {
            tvStatusBadge.text = "🔴 WEB SİTESİ BAKIMDA (Ziyaretçilere Bakım Ekranı Gösteriliyor)"
            tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
            cardStatusBadge.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.error_container))
            cardStatusBadge.strokeColor = ContextCompat.getColor(requireContext(), R.color.error)
        } else {
            tvStatusBadge.text = "🟢 WEB SİTESİ YAYINDA (Site Aktif ve Erişilebilir)"
            tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            cardStatusBadge.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_container_low))
            cardStatusBadge.strokeColor = ContextCompat.getColor(requireContext(), R.color.outline_variant)
        }
    }

    private fun updatePreview(title: String, detail: String, date: String) {
        tvPreviewTitle.text = title.ifEmpty { "Sistem Bakımda" }
        tvPreviewDetail.text = detail.ifEmpty { "Sizlere daha iyi bir deneyim sunabilmek için kısa süreli bir bakım çalışması yapıyoruz." }
        tvPreviewDate.text = date.ifEmpty { "23 Temmuz 2026 - 18:00" }
    }
}