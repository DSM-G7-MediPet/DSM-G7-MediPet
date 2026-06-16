package com.dsm.g7.medipet.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.data.local.Appointment
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.AppointmentStatus
import com.dsm.g7.medipet.data.local.Pet
import com.dsm.g7.medipet.data.local.Vaccine
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.TimeZone

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getDatabase(app)
    private val petDao = db.petDao()
    private val appointmentDao = db.appointmentDao()
    private val vaccineDao = db.vaccineDao()
    private val auth = FirebaseAuth.getInstance()

    private val ownerUid: String get() = auth.currentUser?.uid ?: ""

    val userName: String
        get() {
            val user = auth.currentUser ?: return "Usuario"
            return user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore('@')
                ?: "Usuario"
        }

    val pets: StateFlow<List<Pet>> = petDao.getPetsForOwner(ownerUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayAppointments: StateFlow<List<Appointment>> =
        appointmentDao.getAppointmentsForOwner(ownerUid)
            .map { list ->
                val today = Calendar.getInstance()
                val todayYear = today.get(Calendar.YEAR)
                val todayMonth = today.get(Calendar.MONTH)
                val todayDay = today.get(Calendar.DAY_OF_MONTH)
                list.filter { appt ->
                    val apptCal = Calendar.getInstance().apply { timeInMillis = appt.dateMillis }
                    apptCal.get(Calendar.YEAR) == todayYear &&
                    apptCal.get(Calendar.MONTH) == todayMonth &&
                    apptCal.get(Calendar.DAY_OF_MONTH) == todayDay &&
                    appt.status in listOf(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingVaccines: StateFlow<List<Vaccine>> =
        vaccineDao.getAllVaccinesForOwner(ownerUid)
            .map { list ->
                val now = System.currentTimeMillis()
                val sevenDaysLater = now + 7 * 24 * 60 * 60 * 1000L
                list.filter { !it.isApplied && it.dateMillis in now..sevenDaysLater }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
