package com.korkutsoftware.doruhavadisadmin.login

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.korkutsoftware.doruhavadisadmin.DashboardActivity
import com.korkutsoftware.doruhavadisadmin.R

class AddProfileActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_profile)
        
        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance("dogruhavadis")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etName = findViewById<TextInputEditText>(R.id.et_name)
        val etUsername = findViewById<TextInputEditText>(R.id.et_username)
        val actvJobTitle = findViewById<AutoCompleteTextView>(R.id.actv_job_title)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save_continue)
        val footerText = findViewById<TextView>(R.id.footerText)

        setupFooter(footerText)
        setupJobTitleDropdown(actvJobTitle)

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val jobTitle = actvJobTitle.text.toString().trim()

            if (name.isEmpty() || username.isEmpty() || jobTitle.isEmpty()) {
                Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uid = mAuth.currentUser?.uid
            if (uid != null) {
                val profileUpdates = hashMapOf(
                    "name" to name,
                    "username" to username,
                    "jobTitle" to jobTitle,
                    "hasProfile" to true
                )

                db.collection("users").document(uid)
                    .update(profileUpdates as Map<String, Any>)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profiliniz başarıyla oluşturuldu", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, DashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            mAuth.signOut()
            finish()
        }

        findViewById<android.view.View>(R.id.card_edit_photo).setOnClickListener {
            // Fotoğraf seçme mantığı buraya eklenebilir
            Toast.makeText(this, "Fotoğraf seçme özelliği yakında!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupJobTitleDropdown(autoCompleteTextView: AutoCompleteTextView) {
        val jobTitles = arrayOf("Haber Editörü", "Köşe Yazarı")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jobTitles)
        autoCompleteTextView.setAdapter(adapter)
    }

    private fun setupFooter(footerTextView: TextView) {
        try {
            val pInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            val copyrightText = "© 2026 Korkut Yazılım ve Medya A.Ş. Doğru Havadis Admin Paneli Tüm Hakları Saklıdır. V$version"
            footerTextView.text = copyrightText
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }
}
