package com.korkutsoftware.doruhavadisadmin.models

data class News(
    val id: String = "",
    val title: String = "",
    val category: String = "",
    val province: String = "",
    val district: String = "",
    val description: String = "",
    val adminName: String = "",
    val isHeadline: Boolean = false,
    val mediaUrls: List<String> = emptyList(),
    val timestamp: Any? = null, // Can be Long or Timestamp
    val sourceUrl: String = ""
)