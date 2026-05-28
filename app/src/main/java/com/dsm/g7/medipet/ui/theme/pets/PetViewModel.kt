package com.dsm.g7.medipet.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.data.local.Pet
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class PetViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _pets = MutableStateFlow<List<Pet>>(emptyList())
    val pets: StateFlow<List<Pet>> = _pets.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun openAddDialog() { _showAddDialog.value = true }
    fun closeAddDialog() { _showAddDialog.value = false }

    fun addPet(name: String, species: String, breed: String, ageYears: Int, weightKg: Float) {
        if (name.isBlank() || species.isBlank()) {
            _errorMessage.value = "Nombre y especie son obligatorios"
            return
        }
        val ownerId = auth.currentUser?.uid ?: "sin-usuario"
        val newPet = Pet(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            name = name,
            species = species,
            breed = breed,
            ageYears = ageYears,
            weightKg = weightKg
        )
        _pets.update { currentList -> currentList + newPet }
        _showAddDialog.value = false
    }

    fun deletePet(pet: Pet) {
        _pets.update { currentList -> currentList - pet }
    }

    fun clearError() { _errorMessage.value = null }
}