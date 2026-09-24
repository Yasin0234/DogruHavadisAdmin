package com.korkutsoftware.doruhavadisadmin.login

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.korkutsoftware.doruhavadisadmin.R

class MaintenanceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_maintenance)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Verileri al ve göster
        val title = intent.getStringExtra("title")
        val detail = intent.getStringExtra("detail")
        val date = intent.getStringExtra("date")

        findViewById<TextView>(R.id.txt_maintenance_title).text = title ?: getString(R.string.maintenance_title)
        findViewById<TextView>(R.id.txt_maintenance_detail).text = detail ?: getString(R.string.maintenance_subtitle)
        findViewById<TextView>(R.id.txt_maintenance_date).text = date ?: getString(R.string.maintenance_date)
    }
}