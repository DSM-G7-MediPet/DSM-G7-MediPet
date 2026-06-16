package com.dsm.g7.medipet.ui.pets

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.Appointment
import com.dsm.g7.medipet.data.local.MedicalRecord
import com.dsm.g7.medipet.data.local.Pet
import com.dsm.g7.medipet.data.local.Vaccine
import com.dsm.g7.medipet.data.local.WeightRecord
import com.dsm.g7.medipet.util.PdfExporter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class PetDetailViewModel(app: Application, private val petId: String) : AndroidViewModel(app) {

    private val db = AppDatabase.getDatabase(app)
    private val petDao = db.petDao()
    private val weightRecordDao = db.weightRecordDao()
    private val medicalRecordDao = db.medicalRecordDao()
    private val vaccineDao = db.vaccineDao()
    private val appointmentDao = db.appointmentDao()

    val pet: StateFlow<Pet?> = petDao.observePetById(petId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val weightRecords: StateFlow<List<WeightRecord>> =
        weightRecordDao.getWeightRecordsForPet(petId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestRecord: StateFlow<MedicalRecord?> =
        medicalRecordDao.getRecordsForPet(petId)
            .map { list -> list.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nextVaccine: StateFlow<Vaccine?> =
        vaccineDao.getVaccinesForPet(petId)
            .map { list ->
                val now = System.currentTimeMillis()
                list.filter { !it.isApplied && it.dateMillis >= now }
                    .minByOrNull { it.dateMillis }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allRecords: StateFlow<List<MedicalRecord>> = medicalRecordDao.getRecordsForPet(petId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVaccines: StateFlow<List<Vaccine>> = vaccineDao.getVaccinesForPet(petId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAppointments: StateFlow<List<Appointment>> = appointmentDao.getAppointmentsForPet(petId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWeightRecord(weightKg: Float) {
        viewModelScope.launch {
            weightRecordDao.insertWeight(
                WeightRecord(
                    petId = petId,
                    weightKg = weightKg,
                    dateMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun exportPdf(context: Context, records: List<MedicalRecord>, vaccines: List<Vaccine>): File? {
        val currentPet = pet.value ?: return null
        return PdfExporter.exportPdf(context, currentPet, records, vaccines)
    }
}

class PetDetailViewModelFactory(
    private val app: Application,
    private val petId: String
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PetDetailViewModel(app, petId) as T
    }
}
