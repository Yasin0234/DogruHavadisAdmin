package com.korkutsoftware.doruhavadisadmin.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.korkutsoftware.doruhavadisadmin.R

class UpdateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_update)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val minVersion = intent.getStringExtra("minVersion")
        findViewById<TextView>(R.id.txt_update_version).text = "Gerekli Sürüm: $minVersion"

        val defaultUpdateUrl = "https://korkutsoftware.com/korkut-software-yazilim-paylasma-sistemi-v-1"
        val updateUrl = intent.getStringExtra("updateUrl")?.takeIf { it.isNotEmpty() } ?: defaultUpdateUrl

        findViewById<Button>(R.id.btn_update_app).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(defaultUpdateUrl)))
            }
        }
    }
}
