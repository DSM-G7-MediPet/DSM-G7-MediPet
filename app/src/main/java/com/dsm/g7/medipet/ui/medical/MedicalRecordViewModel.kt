package com.dsm.g7.medipet.ui.medical

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.MedicalRecord
import com.dsm.g7.medipet.data.local.Pet
import com.dsm.g7.medipet.data.local.Vaccine
import com.dsm.g7.medipet.util.StorageUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MedicalRecordViewModel(app: Application, private val petId: String) : AndroidViewModel(app) {

    private val recordDao = AppDatabase.getDatabase(app).medicalRecordDao()
    private val petDao = AppDatabase.getDatabase(app).petDao()
    private val vaccineDao = AppDatabase.getDatabase(app).vaccineDao()
    private val firestore = FirebaseFirestore.getInstance()
    private val ownerUid: String get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val records: StateFlow<List<MedicalRecord>> = recordDao.getRecordsForPet(petId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pet: StateFlow<Pet?> = petDao.observePetById(petId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val vaccines: StateFlow<List<Vaccine>> = vaccineDao.getVaccinesForPet(petId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    init {
        syncFromFirestore()
    }

    private fun syncFromFirestore() {
        firestore.collection("medical_records")
            .whereEqualTo("petId", petId)
            .get()
            .addOnSuccessListener { snapshot ->
                viewModelScope.launch {
                    for (doc in snapshot.documents) {
                        val dateMillis = doc.getLong("dateMillis") ?: continue
                        val diagnosis = doc.getString("diagnosis") ?: continue
                        if (recordDao.findByDetails(petId, dateMillis, diagnosis) == null) {
                            recordDao.insertRecord(
                                MedicalRecord(
                                    petId = petId,
                                    ownerUid = doc.getString("ownerUid") ?: ownerUid,
                                    dateMillis = dateMillis,
                                    diagnosis = diagnosis,
                                    treatment = doc.getString("treatment") ?: "",
                                    medications = doc.getString("medications") ?: "",
                                    vetName = doc.getString("vetName") ?: "",
                                    photoUri = doc.getString("photoUri") ?: "",
                                    voiceNoteUrl = doc.getString("voiceNoteUrl") ?: ""
                                )
                            )
                        }
                    }
                }
            }
    }

    fun openAddDialog() { _showAddDialog.value = true }
    fun closeAddDialog() { _showAddDialog.value = false }

    fun addRecord(
        diagnosis: String,
        treatment: String,
        medications: String,
        vetName: String,
        dateMillis: Long,
        photoUri: String = "",
        voiceNoteUrl: String = ""
    ) {
        if (diagnosis.isBlank()) return
        val uid = ownerUid
        val record = MedicalRecord(
            petId = petId,
            ownerUid = uid,
            dateMillis = dateMillis,
            diagnosis = diagnosis,
            treatment = treatment,
            medications = medications,
            vetName = vetName,
            photoUri = photoUri,
            voiceNoteUrl = voiceNoteUrl
        )
        viewModelScope.launch {
            recordDao.insertRecord(record)

            // Sync to Firestore so owner's device sees vet records
            val docData = mutableMapOf<String, Any>(
                "petId" to petId,
                "ownerUid" to uid,
                "dateMillis" to dateMillis,
                "diagnosis" to diagnosis,
                "treatment" to treatment,
                "medications" to medications,
                "vetName" to vetName,
                "photoUri" to photoUri,
                "voiceNoteUrl" to voiceNoteUrl
            )
            firestore.collection("medical_records").add(docData)

            val needsPhoto = photoUri.isNotBlank() && !photoUri.startsWith("https")
            val needsVoice = voiceNoteUrl.isNotBlank() && !voiceNoteUrl.startsWith("https")
            if (needsPhoto || needsVoice) {
                var uploadedPhoto: String? = null
                var uploadedVoice: String? = null
                if (needsPhoto) {
                    val f = File(photoUri)
                    if (f.exists()) runCatching {
                        val ts = System.currentTimeMillis()
                        uploadedPhoto = StorageUploader.uploadFile(f, "medical_records/$petId/${ts}_photo.jpg")
                    }
                }
                if (needsVoice) {
                    val f = File(voiceNoteUrl)
                    if (f.exists()) runCatching {
                        val ts = System.currentTimeMillis()
                        uploadedVoice = StorageUploader.uploadFile(f, "medical_records/$petId/${ts}_voice.m4a")
                    }
                }
                if (uploadedPhoto != null || uploadedVoice != null) {
                    val inserted = recordDao.findByDetails(petId, dateMillis, diagnosis)
                    if (inserted != null) {
                        recordDao.insertRecord(inserted.copy(
                            photoUri = uploadedPhoto ?: inserted.photoUri,
                            voiceNoteUrl = uploadedVoice ?: inserted.voiceNoteUrl
                        ))
                    }
                }
            }
        }
        _showAddDialog.value = false
    }

    fun markAppointmentAttended(appointmentFirestoreId: String) {
        firestore.collection("appointments").document(appointmentFirestoreId)
            .update("status", "ATTENDED")
    }

    fun updateVoiceNote(record: MedicalRecord, voiceNoteUrl: String) {
        viewModelScope.launch {
            recordDao.insertRecord(record.copy(voiceNoteUrl = voiceNoteUrl))
        }
    }

    fun deleteRecord(record: MedicalRecord) {
        viewModelScope.launch { recordDao.deleteRecord(record) }
    }
}

class MedicalRecordViewModelFactory(
    private val app: Application,
    private val petId: String
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MedicalRecordViewModel(app, petId) as T
    }
}
