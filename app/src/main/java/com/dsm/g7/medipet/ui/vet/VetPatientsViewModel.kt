package com.dsm.g7.medipet.ui.vet

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VetPatient(
    val petId: String,
    val petName: String,
    val ownerName: String,
    val ownerUid: String,
    val species: String,
    val photoUrl: String,
    val lastVisitMillis: Long,
    val totalVisits: Int
)

class VetPatientsViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    private val _patients = MutableStateFlow<List<VetPatient>>(emptyList())
    val patients: StateFlow<List<VetPatient>> = _patients.asStateFlow()

    private var listenerReg: ListenerRegistration? = null

    init {
        listenerReg = firestore.collection("appointments")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val grouped = mutableMapOf<String, MutableList<Map<String, Any>>>()
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val petId = data["petId"] as? String ?: continue
                    grouped.getOrPut(petId) { mutableListOf() }.add(data)
                }
                _patients.value = grouped.map { (petId, appts) ->
                    val sample = appts.first()
                    VetPatient(
                        petId = petId,
                        petName = sample["petName"] as? String ?: "Mascota",
                        ownerName = sample["ownerName"] as? String ?: "",
                        ownerUid = sample["ownerUid"] as? String ?: "",
                        species = sample["petSpecies"] as? String ?: "",
                        photoUrl = sample["petPhotoUrl"] as? String ?: "",
                        lastVisitMillis = appts.mapNotNull { it["dateMillis"] as? Long }.maxOrNull() ?: 0L,
                        totalVisits = appts.size
                    )
                }.sortedBy { it.petName }
            }
    }

    override fun onCleared() {
        super.onCleared()
        listenerReg?.remove()
    }
}
