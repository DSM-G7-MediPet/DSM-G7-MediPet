package com.dsm.g7.medipet.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiseaseDao {

    @Query("SELECT * FROM diseases WHERE especie = :especie ORDER BY nombre ASC")
    fun getDiseasesByEspecie(especie: String): Flow<List<Disease>>

    @Query("SELECT * FROM diseases WHERE especie = :especie ORDER BY nombre ASC")
    suspend fun getDiseasesByEspecieOnce(especie: String): List<Disease>

    @Query("SELECT * FROM diseases WHERE especie = :especie LIMIT :limit")
    suspend fun getDiseasesForSpeciesLimit(especie: String, limit: Int): List<Disease>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(diseases: List<Disease>)

    @Query("DELETE FROM diseases WHERE especie = :especie")
    suspend fun clearByEspecie(especie: String)

    @Query("SELECT COUNT(*) FROM diseases WHERE especie = :especie")
    suspend fun countByEspecie(especie: String): Int
}
