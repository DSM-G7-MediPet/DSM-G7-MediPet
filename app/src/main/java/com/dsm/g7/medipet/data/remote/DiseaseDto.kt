package com.dsm.g7.medipet.data.remote

import com.dsm.g7.medipet.data.local.Disease

data class DiseaseDto(
    val id: String,
    val nombre: String,
    val especie: String,
    val sintomas: List<String>,
    val descripcion: String,
    val recomendacion: String
) {
    fun toEntity() = Disease(
        remoteId = id,
        especie = especie,
        nombre = nombre,
        sintomas = sintomas.joinToString(","),
        descripcion = descripcion,
        recomendacion = recomendacion
    )
}
