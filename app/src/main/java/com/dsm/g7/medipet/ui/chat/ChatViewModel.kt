package com.dsm.g7.medipet.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.BuildConfig
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.ChatMessage
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ChatViewModel(app: Application, val petId: String) : AndroidViewModel(app) {

    private val db         = AppDatabase.getDatabase(app)
    private val chatDao    = db.chatMessageDao()
    private val petDao     = db.petDao()
    private val vaccineDao = db.vaccineDao()
    private val recordDao  = db.medicalRecordDao()
    private val ownerUid: String get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val messages: StateFlow<List<ChatMessage>> = chatDao.getMessages(petId, ownerUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pet = petDao.observePetById(petId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isTyping      = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey    = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature   = 0.7f
            topK          = 40
            topP          = 0.95f
            maxOutputTokens = 1024
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT,        BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.HATE_SPEECH,       BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE)
        )
    )

    private var chatSession: com.google.ai.client.generativeai.Chat? = null

    init {
        viewModelScope.launch { initSession() }
    }

    private suspend fun initSession() {
        val systemPrompt = buildSystemPrompt()
        val greeting     = buildGreeting()
        val history      = chatDao.getMessagesOnce(petId, ownerUid)

        val geminiHistory = buildList {
            add(content(role = "user")  { text(systemPrompt) })
            add(content(role = "model") { text(greeting) })
            history.forEach { msg ->
                add(content(role = msg.role) { text(msg.content) })
            }
        }

        chatSession = model.startChat(geminiHistory)
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isTyping.value) return
        val session = chatSession ?: return

        viewModelScope.launch {
            chatDao.insert(ChatMessage(petId = petId, ownerUid = ownerUid, role = "user", content = text))

            _isTyping.value      = true
            _streamingText.value = ""

            try {
                session.sendMessageStream(text).collect { chunk ->
                    _streamingText.value += chunk.text ?: ""
                }
                val finalText = _streamingText.value
                if (finalText.isNotBlank()) {
                    chatDao.insert(ChatMessage(petId = petId, ownerUid = ownerUid, role = "model", content = finalText))
                }
            } catch (e: Exception) {
                _error.value = "No se pudo conectar con el asistente. Verifica tu conexión."
            } finally {
                _streamingText.value = ""
                _isTyping.value      = false
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            chatDao.deleteForPet(petId, ownerUid)
            initSession()
        }
    }

    fun clearError() { _error.value = null }

    private suspend fun buildSystemPrompt(): String {
        val dateFmt     = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val currentPet  = petDao.getPetById(petId)
        val vaccines    = vaccineDao.getVaccinesForPet(petId).first()
        val lastRecord  = recordDao.getRecordsForPet(petId).first().firstOrNull()
        val now         = System.currentTimeMillis()

        if (currentPet == null) return defaultPrompt()

        val applied  = vaccines.filter { it.isApplied }.joinToString { it.name }.ifBlank { "ninguna registrada" }
        val pending  = vaccines.filter { !it.isApplied }.joinToString {
            "${it.name} (${dateFmt.format(Date(it.dateMillis))})"
        }.ifBlank { "al día" }
        val overdue  = vaccines.any { !it.isApplied && it.dateMillis < now }

        return """
Eres un asistente veterinario en la app MediPet, amable, claro y profesional.

**Mascota activa:** ${currentPet.name} — ${currentPet.species}, ${currentPet.breed.ifBlank { "raza no registrada" }}, ${currentPet.ageYears} años, ${currentPet.weightKg} kg.
**Vacunas aplicadas:** $applied
**Vacunas pendientes:** $pending${if (overdue) " ⚠️ (hay vacunas VENCIDAS)" else ""}
**Última consulta:** ${lastRecord?.let { "${dateFmt.format(Date(it.dateMillis))} — ${it.diagnosis}" } ?: "sin registro"}

Instrucciones:
- Responde SIEMPRE en español.
- Usa el nombre "${currentPet.name}" naturalmente en la conversación.
- Si detectas signos de emergencia (convulsiones, dificultad respiratoria, sangrado severo, intoxicación, pérdida de conciencia, trauma), indica en MAYÚSCULAS que debe ir al veterinario DE INMEDIATO.
- No diagnostiques enfermedades graves; orienta y recomienda visita cuando sea necesario.
- Respuestas concisas (máximo 3-4 párrafos). Puedes usar emojis ocasionalmente 🐾.
- Si te preguntan sobre vacunas o historial, usa los datos reales de ${currentPet.name} indicados arriba.
        """.trimIndent()
    }

    private suspend fun buildGreeting(): String {
        val name = petDao.getPetById(petId)?.name ?: "tu mascota"
        return "¡Hola! 🐾 Soy tu asistente veterinario en MediPet. Estoy aquí para ayudarte con preguntas sobre la salud de $name. ¿En qué puedo ayudarte hoy?"
    }

    private fun defaultPrompt() = """
Eres un asistente veterinario en la app MediPet, amable y profesional.
Responde siempre en español. Ante emergencias, indica que debe ir al veterinario de inmediato.
    """.trimIndent()
}

class ChatViewModelFactory(private val app: Application, private val petId: String) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(app, petId) as T
    }
}
