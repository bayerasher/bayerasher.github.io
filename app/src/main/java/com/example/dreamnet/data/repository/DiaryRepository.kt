package com.example.dreamnet.data.repository

import com.example.dreamnet.data.DiaryDao
import com.example.dreamnet.data.DiaryEntryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(
    private val diaryDao: DiaryDao
) {
    fun getEntries(userId: String): Flow<List<DiaryEntryEntity>> = 
        diaryDao.getEntriesByUserId(userId)

    suspend fun getEntryById(id: Long): DiaryEntryEntity? = 
        diaryDao.getEntryById(id)

    suspend fun saveEntry(entry: DiaryEntryEntity): Long = 
        diaryDao.insertEntry(entry)

    suspend fun updateEntry(entry: DiaryEntryEntity) = 
        diaryDao.updateEntry(entry)

    suspend fun deleteEntry(entry: DiaryEntryEntity) = 
        diaryDao.deleteEntry(entry)

    suspend fun deleteEntryById(id: Long) = 
        diaryDao.deleteEntryById(id)
}
