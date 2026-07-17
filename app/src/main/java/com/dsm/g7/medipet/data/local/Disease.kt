package com.dsm.g7.medipet.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diseases")
data class Disease(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val remoteId: String,
    val especie: String,
    val nombre: String,
    val sintomas: String,   // comma-separated list
    val descripcion: String,
    val recomendacion: String
) {
    fun sintomasList(): List<String> = sintomas.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
