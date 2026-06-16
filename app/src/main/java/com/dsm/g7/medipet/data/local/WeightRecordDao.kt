package com.dsm.g7.medipet.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightRecordDao {
    @Query("SELECT * FROM weight_records WHERE petId = :petId ORDER BY dateMillis ASC")
    fun getWeightRecordsForPet(petId: String): Flow<List<WeightRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(record: WeightRecord)
}
