package com.dsm.g7.medipet.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    // Flow permite que la interfaz se actualice en tiempo real si hay cambios en Room
    @Query("SELECT * FROM vaccines WHERE petId = :petId ORDER BY dateMillis ASC")
    fun getVaccinesForPet(petId: String): Flow<List<Vaccine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccine(vaccine: Vaccine): Long

    @androidx.room.Delete
    suspend fun deleteVaccine(vaccine: Vaccine)

    @Query("SELECT * FROM vaccines WHERE petId = :petId AND name = :name AND dateMillis = :dateMillis LIMIT 1")
    suspend fun findByDetails(petId: String, name: String, dateMillis: Long): Vaccine?

    @Query("UPDATE vaccines SET isApplied = :isApplied WHERE id = :vaccineId")
    suspend fun updateVaccineStatus(vaccineId: Int, isApplied: Boolean)

    // Consulta que usará el Worker para buscar vacunas próximas a vencer
    @Query("SELECT * FROM vaccines WHERE isApplied = 0 AND dateMillis BETWEEN :startMillis AND :endMillis")
    suspend fun getUpcomingVaccines(startMillis: Long, endMillis: Long): List<Vaccine>

    @Query("SELECT v.* FROM vaccines v INNER JOIN pets p ON v.petId = p.id WHERE p.ownerId = :ownerUid")
    fun getAllVaccinesForOwner(ownerUid: String): Flow<List<Vaccine>>

    // Returns pending vaccines for a list of petIds (for badge computation)
    @Query("SELECT * FROM vaccines WHERE petId IN (:petIds) AND isApplied = 0")
    fun getPendingVaccinesForPets(petIds: List<String>): Flow<List<Vaccine>>
}
