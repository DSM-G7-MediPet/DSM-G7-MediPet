package com.dsm.g7.medipet.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pets")
data class Pet(
    @PrimaryKey
    val id: String, // Usamos el UID de Firebase como ID
    val ownerId: String, // UID del dueño en Firebase Auth
    val name: String,
    val species: String, // Perro, Gato, Conejo, etc.
    val breed: String,
    val ageYears: Int,
    val weightKg: Float,
    val photoUrl: String = "" // URL de Firebase Storage
)