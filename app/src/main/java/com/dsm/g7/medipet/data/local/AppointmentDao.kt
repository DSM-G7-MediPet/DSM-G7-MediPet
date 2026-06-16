package com.dsm.g7.medipet.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE ownerUid = :ownerUid ORDER BY dateMillis ASC")
    fun getAppointmentsForOwner(ownerUid: String): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE petId = :petId ORDER BY dateMillis ASC")
    fun getAppointmentsForPet(petId: String): Flow<List<Appointment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: Appointment): Long

    @Update
    suspend fun updateAppointment(appointment: Appointment)

    @Delete
    suspend fun deleteAppointment(appointment: Appointment)

    @Query("SELECT * FROM appointments WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getAppointmentByFirestoreId(firestoreId: String): Appointment?

    @Query("SELECT COUNT(*) FROM appointments WHERE ownerUid = :ownerUid AND status = :status")
    suspend fun countByStatus(ownerUid: String, status: AppointmentStatus): Int

    @Query("SELECT * FROM appointments WHERE ownerUid = :ownerUid AND status = 'PENDING' AND dateMillis < :beforeMillis")
    suspend fun getPendingBeforeDate(ownerUid: String, beforeMillis: Long): List<Appointment>
}
