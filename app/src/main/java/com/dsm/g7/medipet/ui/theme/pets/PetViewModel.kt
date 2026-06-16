package com.dsm.g7.medipet.ui.pets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.Pet
import com.dsm.g7.medipet.data.local.Vaccine
import com.dsm.g7.medipet.util.StorageUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

enum class PetBadge { UP_TO_DATE, VACCINE_SOON, VACCINE_OVERDUE }

class PetViewModel(app: Application) : AndroidViewModel(app) {

    private val petDao = AppDatabase.getDatabase(app).petDao()
    private val vaccineDao = AppDatabase.getDatabase(app).vaccineDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val ownerId: String get() = auth.currentUser?.uid ?: ""

    val pets: StateFlow<List<Pet>> = petDao.getPetsForOwner(ownerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val petBadges: StateFlow<Map<String, PetBadge>> = pets
        .flatMapLatest { petList ->
            if (petList.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val petIds = petList.map { it.id }
                vaccineDao.getPendingVaccinesForPets(petIds)
                    .map { pendingVaccines ->
                        val todayLocalStart = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val sevenDaysLaterStart = todayLocalStart + 7 * 86_400_000L

                        fun vaccineDayLocalStart(dateMillis: Long): Long {
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = dateMillis
                            }
                            return Calendar.getInstance().apply {
                                set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH),
                                    utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        }

                        petList.associate { pet ->
                            val petVaccines = pendingVaccines.filter { it.petId == pet.id }
                            val badge = when {
                                petVaccines.any { vaccineDayLocalStart(it.dateMillis) < todayLocalStart } ->
                                    PetBadge.VACCINE_OVERDUE
                                petVaccines.any {
                                    vaccineDayLocalStart(it.dateMillis) in todayLocalStart..sevenDaysLaterStart
                                } -> PetBadge.VACCINE_SOON
                                else -> PetBadge.UP_TO_DATE
                            }
                            pet.id to badge
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun openAddDialog() { _showAddDialog.value = true }
    fun closeAddDialog() { _showAddDialog.value = false }

    fun addPet(
        name: String,
        species: String,
        breed: String,
        ageYears: Int,
        weightKg: Float,
        photoFile: File? = null,
        photoUri: android.net.Uri? = null
    ) {
        if (name.isBlank() || species.isBlank()) {
            _errorMessage.value = "Nombre y especie son obligatorios"
            return
        }
        val uid = ownerId
        val petId = UUID.randomUUID().toString()
        val newPet = Pet(
            id = petId,
            ownerId = uid,
            name = name,
            species = species,
            breed = breed,
            ageYears = ageYears,
            weightKg = weightKg
        )
        viewModelScope.launch {
            petDao.insertPet(newPet)
            syncToFirestore(newPet)
            if (photoFile != null || photoUri != null) {
                runCatching {
                    val storagePath = "pets/$petId/profile.jpg"
                    val url = when {
                        photoFile != null -> StorageUploader.uploadFile(photoFile, storagePath)
                        else -> StorageUploader.uploadUri(getApplication(), photoUri!!, storagePath)
                    }
                    val updated = newPet.copy(photoUrl = url)
                    petDao.updatePet(updated)
                    firestore.collection("pets").document(petId).update("photoUrl", url)
                }
            }
        }
        _showAddDialog.value = false
    }

    fun deletePet(pet: Pet) {
        viewModelScope.launch {
            petDao.deletePet(pet)
            firestore.collection("pets").document(pet.id).delete()
        }
    }

    fun uploadPetPhoto(petId: String, file: File) {
        viewModelScope.launch {
            try {
                val storagePath = "pets/$petId/profile.jpg"
                val url = StorageUploader.uploadFile(file, storagePath)
                val pet = petDao.getPetById(petId) ?: return@launch
                val updated = pet.copy(photoUrl = url)
                petDao.updatePet(updated)
                firestore.collection("pets").document(petId).update("photoUrl", url)
            } catch (_: Exception) {
                // fire-and-forget
            }
        }
    }

    private fun syncToFirestore(pet: Pet) {
        firestore.collection("pets").document(pet.id).set(
            mapOf(
                "id" to pet.id,
                "ownerId" to pet.ownerId,
                "name" to pet.name,
                "species" to pet.species,
                "breed" to pet.breed,
                "ageYears" to pet.ageYears,
                "weightKg" to pet.weightKg,
                "photoUrl" to pet.photoUrl
            )
        )
    }

    fun clearError() { _errorMessage.value = null }
}
