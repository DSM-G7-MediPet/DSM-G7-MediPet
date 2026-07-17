package com.dsm.g7.medipet.data.repository

import com.dsm.g7.medipet.data.local.Disease
import com.dsm.g7.medipet.data.local.DiseaseDao
import com.dsm.g7.medipet.data.remote.RetrofitClient
import kotlinx.coroutines.flow.Flow

class DiseaseRepository(private val dao: DiseaseDao) {

    fun getDiseases(especie: String): Flow<List<Disease>> = dao.getDiseasesByEspecie(especie)

    suspend fun refreshIfNeeded(especie: String) {
        if (dao.countByEspecie(especie) > 0) return
        try {
            val dtos = RetrofitClient.diseaseApi.getEnfermedades(especie)
            val entities = dtos.map { it.toEntity() }
            dao.clearByEspecie(especie)
            dao.insertAll(entities)
        } catch (_: Exception) {
            // Offline or backend not deployed — silently ignore
        }
    }
}
