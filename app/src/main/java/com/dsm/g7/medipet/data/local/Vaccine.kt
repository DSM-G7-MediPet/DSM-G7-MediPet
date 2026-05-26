package com.dsm.g7.medipet.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vaccines")
data class Vaccine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: String, // Para saber a qué mascota pertenece
    val name: String,
    val type: String,
    val vetName: String,
    val dateMillis: Long, // Guardamos la fecha en milisegundos (timestamp)
    val isApplied: Boolean = false
)
