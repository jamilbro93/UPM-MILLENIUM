package com.example.data

import kotlinx.coroutines.flow.Flow

class NewsDraftRepository(private val newsDraftDao: NewsDraftDao) {
    val allDrafts: Flow<List<NewsDraft>> = newsDraftDao.getAllDrafts()

    suspend fun getDraftById(id: Int): NewsDraft? {
        return newsDraftDao.getDraftById(id)
    }

    suspend fun insert(draft: NewsDraft): Long {
        return newsDraftDao.insertDraft(draft)
    }

    suspend fun delete(draft: NewsDraft) {
        newsDraftDao.deleteDraft(draft)
    }

    suspend fun deleteById(id: Int) {
        newsDraftDao.deleteDraftById(id)
    }
}
