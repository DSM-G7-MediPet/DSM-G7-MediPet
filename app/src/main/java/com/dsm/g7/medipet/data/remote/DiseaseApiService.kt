package com.dsm.g7.medipet.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DiseaseApiService {

    @GET("enfermedades")
    suspend fun getEnfermedades(@Query("especie") especie: String): List<DiseaseDto>

    @GET("enfermedades/{id}")
    suspend fun getEnfermedad(@Path("id") id: String): DiseaseDto

    @GET("especies")
    suspend fun getEspecies(): List<String>
}
