package com.dsm.g7.medipet.ui.vaccines

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.Vaccine
import com.dsm.g7.medipet.worker.VaccineReminderWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class VaccineViewModel(app: Application, val petId: String) : AndroidViewModel(app) {

    private val vaccineDao = AppDatabase.getDatabase(app).vaccineDao()
    private val petDao = AppDatabase.getDatabase(app).petDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val workManager = WorkManager.getInstance(app)

    val isAllPetsMode: Boolean = petId == "all"
    private val ownerUid: String get() = auth.currentUser?.uid ?: ""

    val vaccines: StateFlow<List<Vaccine>> = if (isAllPetsMode) {
        vaccineDao.getAllVaccinesForOwner(ownerUid)
    } else {
        vaccineDao.getVaccinesForPet(petId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val petNames: StateFlow<Map<String, String>> = if (isAllPetsMode) {
        petDao.getPetsForOwner(ownerUid)
            .map { pets -> pets.associate { it.id to it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    } else {
        flowOf(emptyMap<String, String>())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    init {
        if (!isAllPetsMode) syncVetVaccines()
    }

    private fun syncVetVaccines() {
        val uid = ownerUid
        if (uid.isBlank()) return
        firestore.collection("vet_vaccines")
            .whereEqualTo("petId", petId)
            .whereEqualTo("ownerUid", uid)
            .get()
            .addOnSuccessListener { result ->
                viewModelScope.launch {
                    for (doc in result.documents) {
                        val name = doc.getString("name") ?: continue
                        val dateMillis = doc.getLong("dateMillis") ?: continue
                        if (vaccineDao.findByDetails(petId, name, dateMillis) == null) {
                            vaccineDao.insertVaccine(
                                Vaccine(
                                    petId = petId,
                                    name = name,
                                    type = doc.getString("type") ?: "",
                                    vetName = doc.getString("vetName") ?: "",
                                    dateMillis = dateMillis,
                                    isApplied = doc.getBoolean("applied") ?: false
                                )
                            )
                        }
                    }
                }
            }
    }

    fun addVaccine(name: String, type: String, vetName: String, dateMillis: Long) {
        if (isAllPetsMode) return
        viewModelScope.launch {
            val vaccine = Vaccine(
                petId = petId,
                name = name,
                type = type,
                vetName = vetName,
                dateMillis = dateMillis,
                isApplied = false
            )
            vaccineDao.insertVaccine(vaccine)
        }
        scheduleReminder(dateMillis)
    }

    fun toggleVaccineApplied(vaccine: Vaccine) {
        viewModelScope.launch {
            vaccineDao.updateVaccineStatus(vaccine.id, !vaccine.isApplied)
        }
    }

    fun deleteVaccine(vaccine: Vaccine) {
        viewModelScope.launch {
            vaccineDao.deleteVaccine(vaccine)
        }
    }

    private fun scheduleReminder(vaccineDateMillis: Long) {
        val delay = vaccineDateMillis - System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        if (delay <= 0) return
        val request = OneTimeWorkRequestBuilder<VaccineReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueue(request)
    }
}

class VaccineViewModelFactory(private val app: Application, private val petId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return VaccineViewModel(app, petId) as T
    }
}
