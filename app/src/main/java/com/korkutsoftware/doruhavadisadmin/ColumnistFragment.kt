package com.korkutsoftware.doruhavadisadmin

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.korkutsoftware.doruhavadisadmin.models.Columnist

class ColumnistFragment : Fragment() {

    private lateinit var adapter: ColumnistAdapter
    private val db = FirebaseFirestore.getInstance("dogruhavadis")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_columnist, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_columnists)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        adapter = ColumnistAdapter(emptyList()) { columnist, action ->
            when (action) {
                0 -> { // Update
                    val intent = Intent(requireContext(), ColumnistUpdateActivity::class.java)
                    intent.putExtra("columnistId", columnist.id)
                    startActivity(intent)
                }
                1 -> { // Delete
                    showDeleteConfirmation(columnist)
                }
                2 -> { // Prepare Share (Edit)
                    val bundle = Bundle().apply {
                        // For columnists, we might need a different handling in EditFragment or a generic 'contentId'
                        // For now, let's pass it as newsId if the EditFragment can handle both
                        putString("newsId", columnist.id) 
                        putString("contentType", "columnist")
                    }
                    val navOptions = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.navigation_columnist, false)
                        .setLaunchSingleTop(true)
                        .build()
                    findNavController().navigate(R.id.navigation_edit, bundle, navOptions)
                }
            }
        }
        recyclerView.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.fab_add_columnist).setOnClickListener {
            val intent = Intent(requireContext(), ColumnistAddActivity::class.java)
            startActivity(intent)
        }

        fetchColumnists()

        return view
    }

    private fun showDeleteConfirmation(columnist: Columnist) {
        AlertDialog.Builder(requireContext())
            .setTitle("Köşe Yazısını Sil")
            .setMessage("'${columnist.title}' başlıklı yazıyı silmek istediğinize emin misiniz?")
            .setPositiveButton("Sil") { _, _ ->
                deleteColumnist(columnist)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun deleteColumnist(columnist: Columnist) {
        db.collection("columnists").document(columnist.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Köşe yazısı silindi", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchColumnists() {
        db.collection("columnists")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    if (isAdded) {
                        Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val columnistList = snapshots.toObjects(Columnist::class.java)
                    adapter.updateData(columnistList)
                }
            }
    }
}