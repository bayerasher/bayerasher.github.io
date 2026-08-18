package com.example.dreamnet.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamnet.data.DiaryEntryEntity
import com.example.dreamnet.data.repository.DiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    // Всегда используем локальный гостевой ID для работы без авторизации
    private val guestId = "guest_user"

    val entries: StateFlow<List<DiaryEntryEntity>> = diaryRepository.getEntries(guestId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveEntry(title: String, content: String, date: String, aiInterpretation: String = "") {
        viewModelScope.launch {
            val entry = DiaryEntryEntity(
                userId = guestId,
                title = title,
                content = content,
                date = date,
                aiInterpretation = aiInterpretation
            )
            diaryRepository.saveEntry(entry)
        }
    }

    fun updateEntry(entry: DiaryEntryEntity) {
        viewModelScope.launch {
            diaryRepository.updateEntry(entry)
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            diaryRepository.deleteEntryById(id)
        }
    }
}
