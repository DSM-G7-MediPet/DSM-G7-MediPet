package com.dsm.g7.medipet.ui.vet

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class VetHomeViewModel(app: Application) : AndroidViewModel(app) {

    private val firestore = FirebaseFirestore.getInstance()

    private val _pendingAppointments = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val pendingAppointments: StateFlow<List<Map<String, Any>>> = _pendingAppointments.asStateFlow()

    private val _historyAppointments = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val historyAppointments: StateFlow<List<Map<String, Any>>> = _historyAppointments.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var isFirstLoad = true

    init {
        createNotificationChannel()
        listenerRegistration = firestore.collection("appointments")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                if (!isFirstLoad) {
                    for (change in snapshot.documentChanges) {
                        if (change.type == DocumentChange.Type.ADDED) {
                            val doc = change.document
                            if ((doc.getString("status") ?: "") == "PENDING") {
                                notifyNewAppointment(
                                    petName   = doc.getString("petName")   ?: "Mascota",
                                    ownerName = doc.getString("ownerName") ?: "Dueño",
                                    reason    = doc.getString("reason")    ?: ""
                                )
                            }
                        }
                    }
                }
                isFirstLoad = false

                val active  = mutableListOf<Map<String, Any>>()
                val history = mutableListOf<Map<String, Any>>()
                for (doc in snapshot.documents) {
                    val data   = doc.data ?: continue
                    val withId = data + mapOf("firestoreDocId" to doc.id)
                    when (data["status"] as? String ?: "") {
                        "PENDING", "CONFIRMED"                -> active.add(withId)
                        "ATTENDED", "CANCELLED", "EXPIRED"   -> history.add(withId)
                    }
                }
                _pendingAppointments.value = active.sortedBy  { (it["dateMillis"] as? Long) ?: 0L }
                _historyAppointments.value = history.sortedByDescending { (it["dateMillis"] as? Long) ?: 0L }
            }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getApplication<Application>()
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    "vet_appointments_channel",
                    "Nuevas Citas Veterinarias",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Notificaciones de citas recién agendadas por dueños" }
            )
        }
    }

    private fun notifyNewAppointment(petName: String, ownerName: String, reason: String) {
        val manager = getApplication<Application>()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val body = "Nueva cita: $petName — $ownerName" + if (reason.isNotBlank()) " ($reason)" else ""
        val notification = NotificationCompat.Builder(getApplication(), "vet_appointments_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nueva cita agendada — MediPet")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun updateStatus(firestoreId: String, newStatus: String) {
        firestore.collection("appointments").document(firestoreId)
            .update("status", newStatus)
    }

    fun tryMarkAttended(firestoreId: String, petId: String, onNoRecords: () -> Unit) {
        firestore.collection("medical_records")
            .whereEqualTo("petId", petId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) onNoRecords()
                else updateStatus(firestoreId, "ATTENDED")
            }
            .addOnFailureListener { onNoRecords() }
    }

    fun addVaccine(
        petId: String,
        ownerUid: String,
        name: String,
        type: String,
        vetName: String,
        dateMillis: Long,
        applied: Boolean
    ) {
        val docId = UUID.randomUUID().toString()
        firestore.collection("vet_vaccines").document(docId).set(
            mapOf(
                "petId"      to petId,
                "ownerUid"   to ownerUid,
                "name"       to name,
                "type"       to type,
                "vetName"    to vetName,
                "dateMillis" to dateMillis,
                "applied"    to applied
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
