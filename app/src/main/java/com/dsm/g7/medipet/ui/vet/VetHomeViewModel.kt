package com.dsm.g7.medipet.ui.vet

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class VetHomeViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    private val _pendingAppointments = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val pendingAppointments: StateFlow<List<Map<String, Any>>> = _pendingAppointments.asStateFlow()

    private val _historyAppointments = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val historyAppointments: StateFlow<List<Map<String, Any>>> = _historyAppointments.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null

    init {
        listenerRegistration = firestore.collection("appointments")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val active = mutableListOf<Map<String, Any>>()
                val history = mutableListOf<Map<String, Any>>()
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val withId = data + mapOf("firestoreDocId" to doc.id)
                    when (data["status"] as? String ?: "") {
                        "PENDING", "CONFIRMED" -> active.add(withId)
                        "ATTENDED", "CANCELLED", "EXPIRED" -> history.add(withId)
                    }
                }
                _pendingAppointments.value = active.sortedBy { (it["dateMillis"] as? Long) ?: 0L }
                _historyAppointments.value = history.sortedByDescending { (it["dateMillis"] as? Long) ?: 0L }
            }
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
                "petId" to petId,
                "ownerUid" to ownerUid,
                "name" to name,
                "type" to type,
                "vetName" to vetName,
                "dateMillis" to dateMillis,
                "applied" to applied
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
