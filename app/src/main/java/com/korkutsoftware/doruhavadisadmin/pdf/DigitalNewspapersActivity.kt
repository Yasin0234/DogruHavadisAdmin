package com.korkutsoftware.doruhavadisadmin.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.korkutsoftware.doruhavadisadmin.R
import com.korkutsoftware.doruhavadisadmin.models.DigitalNewspaper

class DigitalNewspapersActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance("dogruhavadis")
    private lateinit var rvDigitalNewspapers: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmptyWarning: TextView
    private lateinit var fabCreateNewspaper: ExtendedFloatingActionButton
    private lateinit var adapter: DigitalNewspaperAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_digital_newspapers)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        rvDigitalNewspapers = findViewById(R.id.rvDigitalNewspapers)
        pbLoading = findViewById(R.id.pbLoadingNewspapers)
        tvEmptyWarning = findViewById(R.id.tvEmptyNewspapersWarning)
        fabCreateNewspaper = findViewById(R.id.fabCreateNewspaper)

        rvDigitalNewspapers.layoutManager = LinearLayoutManager(this)
        adapter = DigitalNewspaperAdapter { item, actionType ->
            when (actionType) {
                0 -> viewNewspaperPdf(item)
                1 -> shareNewspaper(item)
                2 -> confirmDeleteNewspaper(item)
            }
        }
        rvDigitalNewspapers.adapter = adapter

        fabCreateNewspaper.setOnClickListener {
            val intent = Intent(this, NewspaperPdfActivity::class.java)
            startActivity(intent)
        }

        listenToDigitalNewspapers()
    }

    private fun listenToDigitalNewspapers() {
        pbLoading.visibility = View.VISIBLE
        tvEmptyWarning.visibility = View.GONE

        db.collection("digital_newspapers")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                pbLoading.visibility = View.GONE
                if (e != null) {
                    Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    val list = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(DigitalNewspaper::class.java)?.copy(id = doc.id)
                    }
                    tvEmptyWarning.visibility = View.GONE
                    rvDigitalNewspapers.visibility = View.VISIBLE
                    adapter.updateList(list)
                } else {
                    rvDigitalNewspapers.visibility = View.GONE
                    tvEmptyWarning.visibility = View.VISIBLE
                    adapter.updateList(emptyList())
                }
            }
    }

    private fun viewNewspaperPdf(newspaper: DigitalNewspaper) {
        if (newspaper.pdfUrl.isEmpty()) {
            Toast.makeText(this, "Bu gazetenin PDF bağlantısı bulunamadı.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(newspaper.pdfUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "PDF açılırken hata oluştu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareNewspaper(newspaper: DigitalNewspaper) {
        if (newspaper.pdfUrl.isEmpty()) {
            Toast.makeText(this, "Paylaşılacak PDF bağlantısı yok.", Toast.LENGTH_SHORT).show()
            return
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, newspaper.title)
            putExtra(Intent.EXTRA_TEXT, "${newspaper.title}\n\nDijital Gazete PDF'ini incelemek için tıklayın:\n${newspaper.pdfUrl}")
        }
        startActivity(Intent.createChooser(shareIntent, "Gazeteyi Paylaş"))
    }

    private fun confirmDeleteNewspaper(newspaper: DigitalNewspaper) {
        AlertDialog.Builder(this)
            .setTitle("Gazeteyi Sil")
            .setMessage("'${newspaper.title}' dijital gazete kaydını silmek istediğinize emin misiniz?")
            .setPositiveButton("Sil") { _, _ ->
                deleteNewspaper(newspaper)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun deleteNewspaper(newspaper: DigitalNewspaper) {
        db.collection("digital_newspapers").document(newspaper.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Dijital gazete silindi.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Silinirken hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
