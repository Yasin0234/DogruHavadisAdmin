package com.korkutsoftware.doruhavadisadmin.models

import com.google.firebase.firestore.Exclude

data class DigitalNewspaper(
    @get:Exclude var id: String = "",
    val title: String = "",
    val issueDate: String = "",
    val headlineTitle: String = "",
    val pdfUrl: String = "",
    val newsCount: Int = 0,
    val pageSize: String = "A3",
    val timestamp: Any? = null,
    val publisherName: String = "Doğru Havadis"
)
