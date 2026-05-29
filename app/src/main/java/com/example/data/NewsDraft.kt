package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_drafts")
data class NewsDraft(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val subTitle: String = "",
    val author: String = "",
    val content: String,
    val category: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
