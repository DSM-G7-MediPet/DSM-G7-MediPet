package com.dsm.g7.medipet.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE petId = :petId AND ownerUid = :ownerUid ORDER BY timestampMillis ASC")
    fun getMessages(petId: String, ownerUid: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE petId = :petId AND ownerUid = :ownerUid ORDER BY timestampMillis ASC")
    suspend fun getMessagesOnce(petId: String, ownerUid: String): List<ChatMessage>

    @Insert
    suspend fun insert(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE petId = :petId AND ownerUid = :ownerUid")
    suspend fun deleteForPet(petId: String, ownerUid: String)
}
