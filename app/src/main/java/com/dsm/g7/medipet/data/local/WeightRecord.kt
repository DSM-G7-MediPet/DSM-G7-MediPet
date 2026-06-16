package com.dsm.g7.medipet.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: String,
    val weightKg: Float,
    val dateMillis: Long
)
