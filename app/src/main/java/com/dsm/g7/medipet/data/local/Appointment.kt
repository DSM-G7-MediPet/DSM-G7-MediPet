package com.dsm.g7.medipet.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AppointmentStatus { PENDING, CONFIRMED, ATTENDED, CANCELLED, EXPIRED }

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: String,
    val petName: String,
    val ownerUid: String,
    val dateMillis: Long,
    val reason: String,
    val vetName: String,
    val status: AppointmentStatus = AppointmentStatus.PENDING,
    val notes: String = "",
    val photoUri: String = "",
    val voiceNoteUrl: String = "",
    val firestoreId: String = ""
)
