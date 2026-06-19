package com.dsm.g7.medipet.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.AppointmentStatus
import com.dsm.g7.medipet.data.local.Pet
import com.dsm.g7.medipet.data.local.WeightRecord
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class HealthStatus { GREEN, YELLOW, RED }
enum class UpcomingEventType { APPOINTMENT, VACCINE }

data class UpcomingEvent(
    val dateMillis: Long,
    val title: String,
    val subtitle: String,
    val type: UpcomingEventType
)

class OwnerDashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val db             = AppDatabase.getDatabase(app)
    private val petDao         = db.petDao()
    private val vaccineDao     = db.vaccineDao()
    private val appointmentDao = db.appointmentDao()
    private val weightDao      = db.weightRecordDao()
    private val ownerUid: String get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val pets: StateFlow<List<Pet>> = petDao.getPetsForOwner(ownerUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPetId = MutableStateFlow<String?>(null)
    val selectedPetId: StateFlow<String?> = _selectedPetId.asStateFlow()

    val weightRecords: StateFlow<List<WeightRecord>> = _selectedPetId
        .flatMapLatest { id ->
            if (id != null) weightDao.getWeightRecordsForPet(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPet: StateFlow<Pet?> = combine(pets, _selectedPetId) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val vaccineStats: StateFlow<Pair<Int, Int>> = _selectedPetId
        .flatMapLatest { id ->
            if (id != null) vaccineDao.getVaccinesForPet(id)
            else flowOf(emptyList())
        }
        .map { list ->
            val applied = list.count { it.isApplied }
            applied to list.size
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0)

    val upcomingEvents: StateFlow<List<UpcomingEvent>> = _selectedPetId
        .flatMapLatest { id ->
            if (id == null) return@flatMapLatest flowOf(emptyList())
            combine(
                appointmentDao.getAppointmentsForPet(id),
                vaccineDao.getVaccinesForPet(id)
            ) { appointments, vaccines ->
                val now = System.currentTimeMillis()
                val apptEvents = appointments
                    .filter { it.dateMillis > now && it.status in listOf(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED) }
                    .map { UpcomingEvent(it.dateMillis, "Cita: ${it.petName}", it.reason, UpcomingEventType.APPOINTMENT) }
                val vaccineEvents = vaccines
                    .filter { !it.isApplied && it.dateMillis > now }
                    .map { UpcomingEvent(it.dateMillis, "Vacuna: ${it.name}", it.type, UpcomingEventType.VACCINE) }
                (apptEvents + vaccineEvents).sortedBy { it.dateMillis }.take(3)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val healthStatus: StateFlow<HealthStatus> = _selectedPetId
        .flatMapLatest { id ->
            if (id != null) vaccineDao.getVaccinesForPet(id)
            else flowOf(emptyList())
        }
        .map { vaccines ->
            val now = System.currentTimeMillis()
            val overdue = vaccines.any { !it.isApplied && it.dateMillis < now }
            val soon    = vaccines.any { !it.isApplied && it.dateMillis in now..(now + 7 * 86_400_000L) }
            when {
                overdue -> HealthStatus.RED
                soon    -> HealthStatus.YELLOW
                else    -> HealthStatus.GREEN
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HealthStatus.GREEN)

    fun selectPet(petId: String) { _selectedPetId.value = petId }

    init {
        viewModelScope.launch {
            pets.collect { list ->
                if (_selectedPetId.value == null && list.isNotEmpty()) {
                    _selectedPetId.value = list.first().id
                }
            }
        }
    }
}
