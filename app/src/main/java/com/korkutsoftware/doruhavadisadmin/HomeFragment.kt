package com.korkutsoftware.doruhavadisadmin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.AggregateSource

class HomeFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var mAuth: FirebaseAuth
    
    private lateinit var tvTotalNews: TextView
    private lateinit var tvHeadlineNews: TextView
    private lateinit var tvColumnistCount: TextView
    private lateinit var tvUserName: TextView
    
    private lateinit var tvGundemCount: TextView
    private lateinit var tvSporCount: TextView
    private lateinit var tvEkonomiCount: TextView
    private lateinit var tvTeknolojiCount: TextView
    private lateinit var tvSaglikCount: TextView
    private lateinit var tvDunyaCount: TextView
    private lateinit var tvSiyasetCount: TextView
    private lateinit var tvEgitimCount: TextView
    private lateinit var tvTurkiyeCount: TextView
    private lateinit var tvAsayisCount: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        db = FirebaseFirestore.getInstance("dogruhavadis")
        mAuth = FirebaseAuth.getInstance()
        
        // Initialize UI
        tvTotalNews = view.findViewById(R.id.tv_total_news_count)
        tvHeadlineNews = view.findViewById(R.id.tv_headline_count)
        tvColumnistCount = view.findViewById(R.id.tv_columnist_count)
        tvUserName = view.findViewById(R.id.tv_user_display_name)
        
        val gundemView = view.findViewById<View>(R.id.stat_gundem)
        val sporView = view.findViewById<View>(R.id.stat_spor)
        val ekonomiView = view.findViewById<View>(R.id.stat_ekonomi)
        val teknolojiView = view.findViewById<View>(R.id.stat_teknoloji)
        val saglikView = view.findViewById<View>(R.id.stat_saglik)
        val dunyaView = view.findViewById<View>(R.id.stat_dunya)
        val siyasetView = view.findViewById<View>(R.id.stat_siyaset)
        val egitimView = view.findViewById<View>(R.id.stat_egitim)
        val turkiyeView = view.findViewById<View>(R.id.stat_turkiye)
        val asayisView = view.findViewById<View>(R.id.stat_asayis)

        tvGundemCount = gundemView.findViewById(R.id.tv_category_count)
        tvSporCount = sporView.findViewById(R.id.tv_category_count)
        tvEkonomiCount = ekonomiView.findViewById(R.id.tv_category_count)
        tvTeknolojiCount = teknolojiView.findViewById(R.id.tv_category_count)
        tvSaglikCount = saglikView.findViewById(R.id.tv_category_count)
        tvDunyaCount = dunyaView.findViewById(R.id.tv_category_count)
        tvSiyasetCount = siyasetView.findViewById(R.id.tv_category_count)
        tvEgitimCount = egitimView.findViewById(R.id.tv_category_count)
        tvTurkiyeCount = turkiyeView.findViewById(R.id.tv_category_count)
        tvAsayisCount = asayisView.findViewById(R.id.tv_category_count)
        
        // Set category names and colors
        setupCategoryView(gundemView, "Gündem", "#FF9800")
        setupCategoryView(sporView, "Spor", "#2196F3")
        setupCategoryView(ekonomiView, "Ekonomi", "#4CAF50")
        setupCategoryView(teknolojiView, "Teknoloji", "#9C27B0")
        setupCategoryView(saglikView, "Sağlık", "#E91E63")
        setupCategoryView(dunyaView, "Dünya", "#607D8B")
        setupCategoryView(siyasetView, "Siyaset", "#f44336")
        setupCategoryView(egitimView, "Eğitim", "#3f51b5")
        setupCategoryView(turkiyeView, "Türkiye", "#009688")
        setupCategoryView(asayisView, "Asayiş", "#795548")
        
        loadUserData()
        fetchStats()

        view.findViewById<View>(R.id.btn_open_drawer).setOnClickListener {
            (activity as? DashboardActivity)?.openDrawer()
        }
        
        return view
    }

    private fun setupCategoryView(view: View, name: String, colorHex: String) {
        view.findViewById<TextView>(R.id.tv_category_name).text = name
        view.findViewById<View>(R.id.view_category_indicator).setBackgroundColor(android.graphics.Color.parseColor(colorHex))
    }

    private fun loadUserData() {
        val uid = mAuth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name")
                    if (!name.isNullOrEmpty()) {
                        tvUserName.text = name
                    }
                }
            }
        }
    }

    private fun fetchStats() {
        // Total News Count
        db.collection("news").count().get(AggregateSource.SERVER).addOnSuccessListener { snapshot ->
            tvTotalNews.text = snapshot.count.toString()
        }

        // Headline News Count
        db.collection("news").whereEqualTo("isHeadline", true).count().get(AggregateSource.SERVER).addOnSuccessListener { snapshot ->
            tvHeadlineNews.text = snapshot.count.toString()
        }

        // Columnist Count
        db.collection("columnists").count().get(AggregateSource.SERVER).addOnSuccessListener { snapshot ->
            tvColumnistCount.text = snapshot.count.toString()
        }

        // Category Counts
        val categories = mapOf(
            "Gündem" to tvGundemCount,
            "Spor" to tvSporCount,
            "Ekonomi" to tvEkonomiCount,
            "Teknoloji" to tvTeknolojiCount,
            "Sağlık" to tvSaglikCount,
            "Dünya" to tvDunyaCount,
            "Siyaset" to tvSiyasetCount,
            "Eğitim" to tvEgitimCount,
            "Türkiye" to tvTurkiyeCount,
            "Asayiş" to tvAsayisCount
        )

        categories.forEach { (name, textView) ->
            db.collection("news").whereEqualTo("category", name).count().get(AggregateSource.SERVER).addOnSuccessListener { snapshot ->
                textView.text = "${snapshot.count} haber"
            }
        }
    }
}