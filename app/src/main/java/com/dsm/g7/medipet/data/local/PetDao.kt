package com.dsm.g7.medipet.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {

    @Query("SELECT * FROM pets WHERE ownerId = :ownerId")
    fun getPetsForOwner(ownerId: String): Flow<List<Pet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPet(pet: Pet)

    @Update
    suspend fun updatePet(pet: Pet)

    @Delete
    suspend fun deletePet(pet: Pet)

    @Query("SELECT * FROM pets WHERE id = :petId")
    suspend fun getPetById(petId: String): Pet?

    @Query("SELECT * FROM pets WHERE id = :petId")
    fun observePetById(petId: String): Flow<Pet?>
}
