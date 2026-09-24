package com.korkutsoftware.doruhavadisadmin.login

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
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

class LoginActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        
        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance("dogruhavadis")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etEmail = findViewById<TextInputEditText>(R.id.et_login_email)
        val etPassword = findViewById<TextInputEditText>(R.id.et_login_password)
        val btnLogin = findViewById<MaterialButton>(R.id.btn_login)
        val footerText = findViewById<TextView>(R.id.footerText)

        setupFooter(footerText)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "E-posta ve şifre giriniz", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = mAuth.currentUser?.uid
                        if (uid != null) checkUserProfile(uid)
                    } else {
                        Toast.makeText(this, "Giriş başarısız: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        findViewById<TextView>(R.id.tv_register).setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        findViewById<TextView>(R.id.tv_forgot_password).setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkUserProfile(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val hasProfile = document.getBoolean("hasProfile") ?: false
                    if (hasProfile) {
                        val intent = Intent(this, DashboardActivity::class.java)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this, AddProfileActivity::class.java)
                        startActivity(intent)
                    }
                    finish()
                } else {
                    // Kullanıcı verisi yoksa profile gönder
                    val intent = Intent(this, AddProfileActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Veritabanı hatası", Toast.LENGTH_SHORT).show()
            }
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
