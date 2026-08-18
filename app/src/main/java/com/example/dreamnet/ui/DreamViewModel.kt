package com.example.dreamnet.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamnet.R
import com.example.dreamnet.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class SearchState { IDLE, LOADING, SUCCESS, ERROR }

@HiltViewModel
class DreamViewModel @Inject constructor(
    private val repository: DreamRepository
) : ViewModel() {
    val allEntries = repository.getAllDreams().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    var searchState by mutableStateOf(SearchState.IDLE)
    var text by mutableStateOf("")

    private var _selectedLanguage by mutableStateOf(repository.getSelectedLanguage())
    var selectedLanguage: String
        get() = _selectedLanguage
        set(value) {
            if (_selectedLanguage != value) {
                val oldLang = _selectedLanguage
                _selectedLanguage = value
                repository.saveSelectedLanguage(value)
                onLanguageChanged(oldLang, value)
            }
        }
    val languages = listOf("Русский", "English", "O'zbek")
    
    var interpretationResult by mutableStateOf<InterpretationResult?>(null)
    var sources by mutableStateOf<Map<String, DreamSource>>(emptyMap())
    var isLoading by mutableStateOf(false)
    var errorResId by mutableStateOf<Int?>(null)

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            sources = repository.getAllSources().associateBy { it.id }
        }
    }

    fun prepopulateDatabase() {
        viewModelScope.launch {
            repository.prepopulateDatabase()
            Log.d("DreamNet", "DATABASE PREPOPULATED")
        }
    }

    fun onSearchClick(context: Context) {
        if (text.isBlank()) return
        
        searchJob?.cancel()
        
        errorResId = null
        interpretationResult = null
        isLoading = true
        searchState = SearchState.LOADING
        
        searchJob = viewModelScope.launch {
            try {
                Log.d("DreamNet", "SEARCH START: $text, LANG: $selectedLanguage")
                val result = repository.getDreamInterpretation(text, selectedLanguage)
                interpretationResult = result
                
                when (result) {
                    is InterpretationResult.Local -> {
                        Log.d("DreamNet", "LOCAL RESULT: FOUND")
                        searchState = SearchState.SUCCESS
                    }
                    is InterpretationResult.AI -> {
                        Log.d("DreamNet", "AI RESULT: GENERATED")
                        searchState = SearchState.SUCCESS
                    }
                    is InterpretationResult.NotFound -> {
                        Log.d("DreamNet", "RESULT: NOT FOUND")
                        searchState = SearchState.ERROR
                        errorResId = R.string.error_no_result
                    }
                    is InterpretationResult.Error -> {
                        Log.e("DreamNet", "INTERPRETATION ERROR: ${result.messageResId}")
                        searchState = SearchState.ERROR
                        errorResId = result.messageResId
                    }
                }
                isLoading = false
            } catch (e: CancellationException) {
                Log.d("DreamNet", "SEARCH CANCELLED")
            } catch (e: Exception) {
                Log.e("DreamNet", "CRITICAL SEARCH ERROR", e)
                searchState = SearchState.ERROR
                errorResId = R.string.error_unknown
                isLoading = false
            }
        }
    }

    private fun onLanguageChanged(oldLang: String, newLang: String) {
        val current = interpretationResult
        if (current is InterpretationResult.Local) {
            viewModelScope.launch {
                val synced = repository.syncLanguage(current.result.entry, newLang)
                if (synced != null) {
                    interpretationResult = InterpretationResult.Local(result = synced)
                    text = synced.entry.word
                } else {
                    onSearchClickInternal(newLang)
                }
            }
        } else if (text.isNotBlank()) {
            onSearchClickInternal(newLang)
        }
    }

    private fun onSearchClickInternal(language: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val result = repository.getDreamInterpretation(text, language)
            interpretationResult = result
        }
    }
}
