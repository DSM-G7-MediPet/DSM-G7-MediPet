package com.dsm.g7.medipet.ui.vaccines

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class VaccineViewModel : ViewModel() {

    // Esta lista temporal reemplaza a Room por ahora.
    private val _vaccines = MutableStateFlow<List<VaccineUI>>(emptyList())
    val vaccines: StateFlow<List<VaccineUI>> = _vaccines.asStateFlow()

    // Función para guardar lo que viene de tu DatePicker y TextFields
    fun addVaccine(name: String, type: String, vetName: String, date: String) {
        val newVaccine = VaccineUI(
            id = (1..10000).random(),
            name = name,
            type = type,
            vetName = vetName,
            date = date,
            isApplied = false
        )
        // Agrega la nueva vacuna a la lista
        _vaccines.update { currentList -> currentList + newVaccine }
    }

    // Función para el Checkbox
    fun toggleVaccineStatus(id: Int) {
        _vaccines.update { currentList ->
            currentList.map { vaccine ->
                if (vaccine.id == id) vaccine.copy(isApplied = !vaccine.isApplied) else vaccine
            }
        }
    }
}