package com.dsm.g7.medipet.ui.appointments

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dsm.g7.medipet.data.local.Appointment
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.AppointmentStatus
import com.dsm.g7.medipet.data.local.Pet
import com.dsm.g7.medipet.util.StorageUploader
import com.dsm.g7.medipet.worker.AppointmentReminderWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

class AppointmentViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getDatabase(app)
    private val appointmentDao = db.appointmentDao()
    private val petDao = db.petDao()
    private val firestore = FirebaseFirestore.getInstance()
    private val ownerUid: String get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val appointments: StateFlow<List<Appointment>> = appointmentDao.getAppointmentsForOwner(ownerUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pets: StateFlow<List<Pet>> = petDao.getPetsForOwner(ownerUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private var firestoreListener: ListenerRegistration? = null

    init {
        listenToFirestoreStatusChanges()
        markExpiredAppointments()
    }

    private fun markExpiredAppointments() {
        viewModelScope.launch {
            val uid = ownerUid
            if (uid.isBlank()) return@launch
            val todayUtcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val expired = appointmentDao.getPendingBeforeDate(uid, todayUtcMidnight)
            for (appt in expired) {
                val updated = appt.copy(status = AppointmentStatus.EXPIRED)
                appointmentDao.updateAppointment(updated)
                if (appt.firestoreId.isNotBlank()) {
                    firestore.collection("appointments").document(appt.firestoreId)
                        .update("status", AppointmentStatus.EXPIRED.name)
                }
            }
        }
    }

    private fun listenToFirestoreStatusChanges() {
        val uid = ownerUid
        if (uid.isBlank()) return
        firestoreListener = firestore.collection("appointments")
            .whereEqualTo("ownerUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                viewModelScope.launch {
                    for (change in snapshot.documentChanges) {
                        if (change.type == DocumentChange.Type.REMOVED) continue
                        val doc = change.document
                        val firestoreId = doc.getString("firestoreId") ?: doc.id
                        val statusStr = doc.getString("status") ?: continue
                        val newStatus = runCatching {
                            AppointmentStatus.valueOf(statusStr)
                        }.getOrNull() ?: continue
                        val local = appointmentDao.getAppointmentByFirestoreId(firestoreId)
                            ?: continue
                        if (local.status != newStatus) {
                            appointmentDao.updateAppointment(local.copy(status = newStatus))
                        }
                    }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        firestoreListener?.remove()
    }

    fun openAddDialog() { _showAddDialog.value = true }
    fun closeAddDialog() { _showAddDialog.value = false }

    fun addAppointment(
        petId: String,
        petName: String,
        dateMillis: Long,
        reason: String,
        vetName: String,
        photoUri: String = "",
        voiceNoteUrl: String = ""
    ) {
        if (petId.isBlank() || reason.isBlank()) return
        val firestoreId = UUID.randomUUID().toString()
        val appointment = Appointment(
            petId = petId,
            petName = petName,
            ownerUid = ownerUid,
            dateMillis = dateMillis,
            reason = reason,
            vetName = vetName,
            photoUri = photoUri,
            voiceNoteUrl = voiceNoteUrl,
            firestoreId = firestoreId
        )
        // Schedule 1-hour reminder notification
        val delay = dateMillis - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        if (delay > 0) {
            val workData = workDataOf("petName" to petName, "reason" to reason, "vetName" to vetName)
            val request = OneTimeWorkRequestBuilder<AppointmentReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workData)
                .build()
            WorkManager.getInstance(getApplication()).enqueue(request)
        }

        viewModelScope.launch {
            val roomId = appointmentDao.insertAppointment(appointment)
            pushToFirestore(appointment)
            val inserted = appointment.copy(id = roomId.toInt())
            var newPhotoUri = photoUri
            var newVoiceUrl = voiceNoteUrl
            if (photoUri.isNotBlank() && !photoUri.startsWith("https")) {
                runCatching {
                    val uri = android.net.Uri.parse(photoUri)
                    newPhotoUri = StorageUploader.uploadUri(getApplication(), uri, "appointments/$firestoreId/photo.jpg")
                }
            }
            if (voiceNoteUrl.isNotBlank() && !voiceNoteUrl.startsWith("https")) {
                val f = java.io.File(voiceNoteUrl)
                if (f.exists()) runCatching {
                    newVoiceUrl = StorageUploader.uploadFile(f, "appointments/$firestoreId/voice.m4a")
                }
            }
            if (newPhotoUri != photoUri || newVoiceUrl != voiceNoteUrl) {
                val updated = inserted.copy(photoUri = newPhotoUri, voiceNoteUrl = newVoiceUrl)
                appointmentDao.updateAppointment(updated)
                val patch = mutableMapOf<String, Any>()
                if (newPhotoUri != photoUri) patch["photoUri"] = newPhotoUri
                if (newVoiceUrl != voiceNoteUrl) patch["voiceNoteUrl"] = newVoiceUrl
                firestore.collection("appointments").document(firestoreId).update(patch)
            }
        }
        _showAddDialog.value = false
    }

    private fun pushToFirestore(appointment: Appointment) {
        val ownerName = FirebaseAuth.getInstance().currentUser?.displayName ?: ""
        val data = mapOf(
            "petId" to appointment.petId,
            "petName" to appointment.petName,
            "ownerUid" to appointment.ownerUid,
            "ownerName" to ownerName,
            "dateMillis" to appointment.dateMillis,
            "reason" to appointment.reason,
            "vetName" to appointment.vetName,
            "status" to appointment.status.name,
            "notes" to appointment.notes,
            "photoUri" to appointment.photoUri,
            "voiceNoteUrl" to appointment.voiceNoteUrl,
            "firestoreId" to appointment.firestoreId
        )
        firestore.collection("appointments").document(appointment.firestoreId)
            .set(data)
    }

    fun updateStatus(appointment: Appointment, newStatus: AppointmentStatus) {
        viewModelScope.launch {
            val updated = appointment.copy(status = newStatus)
            appointmentDao.updateAppointment(updated)
            if (appointment.firestoreId.isNotBlank()) {
                firestore.collection("appointments").document(appointment.firestoreId)
                    .update("status", newStatus.name)
            }
        }
    }

    fun updateVoiceNote(appointment: Appointment, voiceNoteUrl: String) {
        viewModelScope.launch {
            val updated = appointment.copy(voiceNoteUrl = voiceNoteUrl)
            appointmentDao.updateAppointment(updated)
            if (appointment.firestoreId.isNotBlank()) {
                firestore.collection("appointments").document(appointment.firestoreId)
                    .update("voiceNoteUrl", voiceNoteUrl)
            }
        }
    }

    fun updatePhotoUri(appointment: Appointment, photoUri: String) {
        viewModelScope.launch {
            val updated = appointment.copy(photoUri = photoUri)
            appointmentDao.updateAppointment(updated)
            if (appointment.firestoreId.isNotBlank()) {
                firestore.collection("appointments").document(appointment.firestoreId)
                    .update("photoUri", photoUri)
            }
        }
    }

    fun deleteAppointment(appointment: Appointment) {
        viewModelScope.launch { appointmentDao.deleteAppointment(appointment) }
    }
}
