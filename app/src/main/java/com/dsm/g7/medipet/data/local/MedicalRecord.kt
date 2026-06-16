package com.dsm.g7.medipet.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medical_records")
data class MedicalRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: String,
    val ownerUid: String,
    val dateMillis: Long,
    val diagnosis: String,
    val treatment: String,
    val medications: String,
    val vetName: String,
    val photoUri: String = "",
    val voiceNoteUrl: String = ""
)
