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
import com.korkutsoftware.doruhavadisadmin.R

class RegisterActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        
        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance("dogruhavadis")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etEmail = findViewById<TextInputEditText>(R.id.et_reg_email)
        val etPassword = findViewById<TextInputEditText>(R.id.et_reg_password)
        val etRepassword = findViewById<TextInputEditText>(R.id.et_reg_repassword)
        val btnRegister = findViewById<MaterialButton>(R.id.btn_register)
        val footerText = findViewById<TextView>(R.id.footerText)

        setupFooter(footerText)

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val repassword = etRepassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || repassword.isEmpty()) {
                Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != repassword) {
                Toast.makeText(this, "Şifreler uyuşmuyor", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Şifre en az 6 karakter olmalıdır", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = mAuth.currentUser
                        user?.let { saveUserInfo(it.uid, email) }
                    } else {
                        Toast.makeText(this, "Kayıt hatası: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        findViewById<TextView>(R.id.tv_login).setOnClickListener {
            finish()
        }
    }

    private fun saveUserInfo(uid: String, email: String) {
        val userMap = hashMapOf(
            "uid" to uid,
            "email" to email,
            "role" to "admin",
            "hasProfile" to false
        )

        db.collection("users").document(uid)
            .set(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Hesap oluşturuldu, lütfen profilinizi tamamlayın", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, AddProfileActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Veri kaydetme hatası: ${e.message}", Toast.LENGTH_LONG).show()
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
