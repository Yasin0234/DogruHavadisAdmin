package com.korkutsoftware.doruhavadisadmin

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.korkutsoftware.doruhavadisadmin.login.LoginActivity

class ProfileFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance("dogruhavadis")

    private lateinit var ivProfile: ImageView
    private lateinit var tvHeaderName: TextView
    private lateinit var tvHeaderEmail: TextView
    private lateinit var etName: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var spinnerJob: AutoCompleteTextView
    private lateinit var btnUpdate: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var progress: LinearProgressIndicator

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        
        ivProfile = view.findViewById(R.id.iv_profile_image)
        tvHeaderName = view.findViewById(R.id.tv_display_name_header)
        tvHeaderEmail = view.findViewById(R.id.tv_display_email_header)
        etName = view.findViewById(R.id.et_profile_name)
        etUsername = view.findViewById(R.id.et_profile_username)
        spinnerJob = view.findViewById(R.id.spinner_profile_job)
        btnUpdate = view.findViewById(R.id.btn_update_profile)
        btnLogout = view.findViewById(R.id.btn_profile_logout)
        progress = view.findViewById(R.id.profile_progress)

        setupJobDropdown()
        fetchUserProfile()
        
        btnUpdate.setOnClickListener { updateProfile() }
        btnLogout.setOnClickListener { showLogoutConfirmation() }
        
        view.findViewById<View>(R.id.card_edit_profile_image).setOnClickListener {
            Toast.makeText(context, "Profil fotoğrafı güncelleme yakında!", Toast.LENGTH_SHORT).show()
        }
        
        return view
    }

    private fun setupJobDropdown() {
        val jobs = arrayOf("Haber Editörü", "Köşe Yazarı")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, jobs)
        spinnerJob.setAdapter(adapter)
    }

    private fun fetchUserProfile() {
        val user = auth.currentUser ?: return
        tvHeaderEmail.text = user.email
        
        progress.visibility = View.VISIBLE
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                progress.visibility = View.GONE
                if (document.exists()) {
                    val name = document.getString("name")
                    val username = document.getString("username")
                    val jobTitle = document.getString("jobTitle")
                    
                    tvHeaderName.text = name ?: "Admin"
                    etName.setText(name)
                    etUsername.setText(username)
                    spinnerJob.setText(jobTitle, false)
                }
            }
            .addOnFailureListener {
                progress.visibility = View.GONE
            }
    }

    private fun updateProfile() {
        val user = auth.currentUser ?: return
        val name = etName.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val job = spinnerJob.text.toString().trim()

        if (name.isEmpty() || username.isEmpty() || job.isEmpty()) {
            Toast.makeText(context, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
            return
        }

        progress.visibility = View.VISIBLE
        val updates = hashMapOf<String, Any>(
            "name" to name,
            "username" to username,
            "jobTitle" to job
        )

        db.collection("users").document(user.uid).update(updates)
            .addOnSuccessListener {
                progress.visibility = View.GONE
                tvHeaderName.text = name
                Toast.makeText(context, "Profil güncellendi", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progress.visibility = View.GONE
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Çıkış Yap")
            .setMessage("Oturumu kapatmak istediğinize emin misiniz?")
            .setPositiveButton("Evet") { _, _ ->
                auth.signOut()
                val intent = Intent(requireActivity(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}