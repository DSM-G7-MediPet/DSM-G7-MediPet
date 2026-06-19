package com.dsm.g7.medipet.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.BuildConfig
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.ChatMessage
import com.dsm.g7.medipet.data.local.Pet
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(app: Application, initialPetId: String) : AndroidViewModel(app) {

    private val db         = AppDatabase.getDatabase(app)
    private val chatDao    = db.chatMessageDao()
    private val petDao     = db.petDao()
    private val vaccineDao = db.vaccineDao()
    private val recordDao  = db.medicalRecordDao()
    private val ownerUid: String get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ── Selected pet ──────────────────────────────────────────────────────────
    private val _currentPetId = MutableStateFlow(initialPetId)
    val currentPetId: StateFlow<String> = _currentPetId.asStateFlow()

    val allPets: StateFlow<List<Pet>> = petDao.getPetsForOwner(ownerUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pet: StateFlow<Pet?> = _currentPetId.flatMapLatest { id ->
        petDao.observePetById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<ChatMessage>> = _currentPetId.flatMapLatest { id ->
        chatDao.getMessages(id, ownerUid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── UI state ──────────────────────────────────────────────────────────────
    private val _isTyping      = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    // Keep for ChatScreen compatibility — always empty (no streaming in REST mode)
    val streamingText: StateFlow<String> = MutableStateFlow("").asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Cached prompts (rebuilt when pet changes) ─────────────────────────────
    private var cachedSystemPrompt = ""
    private var cachedGreeting     = ""

    init {
        viewModelScope.launch { initSession() }
    }

    // ── Pet switching ─────────────────────────────────────────────────────────
    fun selectPet(petId: String) {
        if (petId == _currentPetId.value) return
        _currentPetId.value = petId
        viewModelScope.launch { initSession() }
    }

    private suspend fun initSession() {
        cachedSystemPrompt = buildSystemPrompt(_currentPetId.value)
        cachedGreeting     = buildGreeting(_currentPetId.value)
    }

    // ── Send message via REST API (free tier, no Firebase billing) ────────────
    fun sendMessage(text: String) {
        if (text.isBlank() || _isTyping.value) return
        val petId = _currentPetId.value

        viewModelScope.launch {
            chatDao.insert(ChatMessage(petId = petId, ownerUid = ownerUid, role = "user", content = text))
            _isTyping.value = true

            try {
                // Build full conversation: system context + history + new user message
                val history = chatDao.getMessagesOnce(petId, ownerUid)
                val contents = buildList {
                    add("user"  to cachedSystemPrompt)
                    add("model" to cachedGreeting)
                    history.forEach { add(it.role to it.content) }
                }

                val response = GeminiRestClient.chat(BuildConfig.GEMINI_API_KEY, contents)

                if (response.isNotBlank()) {
                    chatDao.insert(ChatMessage(petId = petId, ownerUid = ownerUid, role = "model", content = response))
                }
            } catch (e: Exception) {
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            chatDao.deleteForPet(_currentPetId.value, ownerUid)
            initSession()
        }
    }

    fun clearError() { _error.value = null }

    // ── Prompts ───────────────────────────────────────────────────────────────
    private suspend fun buildSystemPrompt(petId: String): String {
        val dateFmt    = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val currentPet = petDao.getPetById(petId) ?: return defaultPrompt()
        val vaccines   = vaccineDao.getVaccinesForPet(petId).first()
        val lastRecord = recordDao.getRecordsForPet(petId).first().firstOrNull()
        val now        = System.currentTimeMillis()

        val applied = vaccines.filter { it.isApplied }.joinToString { it.name }.ifBlank { "ninguna registrada" }
        val pending = vaccines.filter { !it.isApplied }.joinToString {
            "${it.name} (${dateFmt.format(Date(it.dateMillis))})"
        }.ifBlank { "al día" }
        val overdue = vaccines.any { !it.isApplied && it.dateMillis < now }

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

    private suspend fun buildGreeting(petId: String): String {
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
