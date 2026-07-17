package com.dsm.g7.medipet.ui.diseases

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.Disease
import com.dsm.g7.medipet.data.repository.DiseaseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DiseaseViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getDatabase(app).diseaseDao()
    private val repo = DiseaseRepository(dao)

    private val _selectedEspecie = MutableStateFlow("perro")
    val selectedEspecie: StateFlow<String> = _selectedEspecie.asStateFlow()

    val diseases: StateFlow<List<Disease>> = _selectedEspecie
        .flatMapLatest { repo.getDiseases(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        selectEspecie("perro")
    }

    fun selectEspecie(especie: String) {
        _selectedEspecie.value = especie
        viewModelScope.launch {
            _isLoading.value = true
            repo.refreshIfNeeded(especie)
            _isLoading.value = false
        }
    }
}
