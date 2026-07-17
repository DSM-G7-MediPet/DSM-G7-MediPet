package com.dsm.g7.medipet.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://medipet-api-383450712485.us-central1.run.app/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val diseaseApi: DiseaseApiService by lazy {
        retrofit.create(DiseaseApiService::class.java)
    }
}
