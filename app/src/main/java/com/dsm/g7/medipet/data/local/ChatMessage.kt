package com.dsm.g7.medipet.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: String,
    val ownerUid: String,
    val role: String,           // "user" | "model"
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis()
)
