package com.dsm.g7.medipet.ui.vet

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class VetDashboardStats(
    val total: Int = 0,
    val pending: Int = 0,
    val confirmed: Int = 0,
    val attended: Int = 0,
    val cancelled: Int = 0,
    val expired: Int = 0,
    val todayCount: Int = 0,
    val byDayOfWeek: List<Int> = List(7) { 0 },   // 0=Lun … 6=Dom
    val weeklyTrend: List<Int> = List(8) { 0 },    // últimas 8 semanas (atendidas)
    val confirmationRate: Float = 0f               // (confirmed+attended) / total
)

class VetDashboardViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val _stats = MutableStateFlow(VetDashboardStats())
    val stats: StateFlow<VetDashboardStats> = _stats.asStateFlow()
    private var listener: ListenerRegistration? = null

    init {
        listener = firestore.collection("appointments")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val docs = snapshot.documents.mapNotNull { it.data }

                val cal = Calendar.getInstance()
                val now = cal.timeInMillis

                // Today (local calendar day)
                val todayYear = cal.get(Calendar.YEAR)
                val todayMonth = cal.get(Calendar.MONTH)
                val todayDay = cal.get(Calendar.DAY_OF_MONTH)
                val todayCount = docs.count { doc ->
                    val ms = (doc["dateMillis"] as? Long) ?: 0L
                    Calendar.getInstance().also { it.timeInMillis = ms }.let {
                        it.get(Calendar.YEAR) == todayYear &&
                        it.get(Calendar.MONTH) == todayMonth &&
                        it.get(Calendar.DAY_OF_MONTH) == todayDay
                    }
                }

                // By day of week (Mon=0 … Sun=6)
                val byDow = MutableList(7) { 0 }
                docs.forEach { doc ->
                    val ms = (doc["dateMillis"] as? Long) ?: 0L
                    val dow = Calendar.getInstance().also { it.timeInMillis = ms }
                        .get(Calendar.DAY_OF_WEEK)   // Sun=1, Mon=2 … Sat=7
                    val idx = (dow - 2 + 7) % 7        // → Mon=0 … Sun=6
                    byDow[idx]++
                }

                // Weekly attended trend (last 8 weeks)
                val weeklyMs = 7L * 24 * 3600 * 1000
                val weekTrend = MutableList(8) { 0 }
                docs.forEach { doc ->
                    if (doc["status"] == "ATTENDED") {
                        val ms = (doc["dateMillis"] as? Long) ?: 0L
                        val weeksAgo = ((now - ms) / weeklyMs).toInt()
                        if (weeksAgo in 0..7) weekTrend[7 - weeksAgo]++
                    }
                }

                val total = docs.size
                val pending = docs.count { it["status"] == "PENDING" }
                val confirmed = docs.count { it["status"] == "CONFIRMED" }
                val attended = docs.count { it["status"] == "ATTENDED" }
                val cancelled = docs.count { it["status"] == "CANCELLED" }
                val expired = docs.count { it["status"] == "EXPIRED" }
                val confirmRate = if (total > 0) (confirmed + attended).toFloat() / total else 0f

                _stats.value = VetDashboardStats(
                    total = total,
                    pending = pending,
                    confirmed = confirmed,
                    attended = attended,
                    cancelled = cancelled,
                    expired = expired,
                    todayCount = todayCount,
                    byDayOfWeek = byDow,
                    weeklyTrend = weekTrend,
                    confirmationRate = confirmRate
                )
            }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
