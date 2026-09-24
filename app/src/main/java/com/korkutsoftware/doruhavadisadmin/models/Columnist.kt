package com.korkutsoftware.doruhavadisadmin.models

data class Columnist(
    val id: String = "",
    val writerName: String = "",
    val title: String = "",
    val content: String = "",
    val writerImageUrl: String = "",
    val contentImageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)