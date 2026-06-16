package com.dsm.g7.medipet.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicalRecordDao {
    @Query("SELECT * FROM medical_records WHERE petId = :petId ORDER BY dateMillis DESC")
    fun getRecordsForPet(petId: String): Flow<List<MedicalRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MedicalRecord)

    @Delete
    suspend fun deleteRecord(record: MedicalRecord)

    @Query("SELECT * FROM medical_records WHERE petId = :petId AND dateMillis = :dateMillis AND diagnosis = :diagnosis LIMIT 1")
    suspend fun findByDetails(petId: String, dateMillis: Long, diagnosis: String): MedicalRecord?
}
