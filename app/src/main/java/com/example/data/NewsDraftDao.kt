package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDraftDao {
    @Query("SELECT * FROM news_drafts ORDER BY lastUpdated DESC")
    fun getAllDrafts(): Flow<List<NewsDraft>>

    @Query("SELECT * FROM news_drafts WHERE id = :id")
    suspend fun getDraftById(id: Int): NewsDraft?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: NewsDraft): Long

    @Delete
    suspend fun deleteDraft(draft: NewsDraft)

    @Query("DELETE FROM news_drafts WHERE id = :id")
    suspend fun deleteDraftById(id: Int)
}
