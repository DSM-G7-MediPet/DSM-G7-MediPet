 package com.dsm.g7.medipet.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.BuildConfig
import com.dsm.g7.medipet.data.local.AppDatabase
import com.dsm.g7.medipet.data.local.ChatMessage
import com.dsm.g7.medipet.data.local.Pet
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(app: Application, initialPetId: String) : AndroidViewModel(app) {

    private val db          = AppDatabase.getDatabase(app)
    private val chatDao     = db.chatMessageDao()
    private val petDao      = db.petDao()
    private val vaccineDao  = db.vaccineDao()
    private val recordDao   = db.medicalRecordDao()
    private val diseaseDao  = db.diseaseDao()
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

    // ── UI estados ──────────────────────────────────────────────────────────────
    private val _isTyping      = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Gemini SDK configuracion ────────────────────────────────────────────────────────────
    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey    = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature     = 0.7f
            topK            = 40
            topP            = 0.95f
            maxOutputTokens = 1024
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT,        BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.HATE_SPEECH,       BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE)
        )
    )

    // Inicialición de la sesión de chat
    private var chatSession: com.google.ai.client.generativeai.Chat? = null

    init {
        viewModelScope.launch { initSession() }
    }

    // ── Pet cambio ─────────────────────────────────────────────────────────
    fun selectPet(petId: String) {
        if (petId == _currentPetId.value) return
        _currentPetId.value = petId
        chatSession = null
        viewModelScope.launch { initSession() }
    }

    // ── Session init ──────────────────────────────────────────────────────────
    private suspend fun initSession() {
        val systemPrompt = buildSystemPrompt(_currentPetId.value)
        val greeting     = buildGreeting(_currentPetId.value)
        val history      = chatDao.getMessagesOnce(_currentPetId.value, ownerUid)

        val geminiHistory = buildList {
            add(content(role = "user")  { text(systemPrompt) })
            add(content(role = "model") { text(greeting) })
            history.forEach { msg ->
                add(content(role = msg.role) { text(msg.content) })
            }
        }
        chatSession = model.startChat(geminiHistory)
    }

    // ── Send message ──────────────────────────────────────────────────────────
    fun sendMessage(text: String) {
        if (text.isBlank() || _isTyping.value) return
        val session = chatSession ?: return
        val petId   = _currentPetId.value

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
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _streamingText.value = ""
                _isTyping.value      = false
            }
        }
    }
  //u6 --
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

        val diseaseContext = buildDiseaseContext(currentPet.species)

        return """
Eres un asistente veterinario en la app MediPet, amable, claro y profesional.

**Mascota activa:** ${currentPet.name} — ${currentPet.species}, ${currentPet.breed.ifBlank { "raza no registrada" }}, ${currentPet.ageYears} años, ${currentPet.weightKg} kg.
**Vacunas aplicadas:** $applied
**Vacunas pendientes:** $pending${if (overdue) " ⚠️ (hay vacunas VENCIDAS)" else ""}
**Última consulta:** ${lastRecord?.let { "${dateFmt.format(Date(it.dateMillis))} — ${it.diagnosis}" } ?: "sin registro"}$diseaseContext

Instrucciones:
- Responde SIEMPRE en español.
- Usa el nombre "${currentPet.name}" naturalmente en la conversación.
- Si detectas signos de emergencia (convulsiones, dificultad respiratoria, sangrado severo, intoxicación, pérdida de conciencia, trauma), indica en MAYÚSCULAS que debe ir al veterinario DE INMEDIATO.
- No diagnostiques enfermedades graves; orienta y recomienda visita cuando sea necesario.
- Respuestas concisas (máximo 3-4 párrafos). Puedes usar emojis ocasionalmente 🐾.
- Si te preguntan sobre vacunas o historial, usa los datos reales de ${currentPet.name} indicados arriba.
- Si mencionan síntomas, considera las enfermedades comunes de la especie listadas arriba.
        """.trimIndent()
    }

    private suspend fun buildGreeting(petId: String): String {
        val name = petDao.getPetById(petId)?.name ?: "tu mascota"
        return "¡Hola! 🐾 Soy tu asistente veterinario en MediPet. Estoy aquí para ayudarte con preguntas sobre la salud de $name. ¿En qué puedo ayudarte hoy?"
    }

    private suspend fun buildDiseaseContext(species: String): String {
        val especie = when {
            species.contains("perro", ignoreCase = true) || species.contains("dog", ignoreCase = true) -> "perro"
            species.contains("gato", ignoreCase = true) || species.contains("cat", ignoreCase = true) -> "gato"
            species.contains("conejo", ignoreCase = true) || species.contains("rabbit", ignoreCase = true) -> "conejo"
            else -> return ""
        }
        val diseases = diseaseDao.getDiseasesForSpeciesLimit(especie, 5)
        if (diseases.isEmpty()) return ""
        val list = diseases.joinToString("\n") { d -> "- ${d.nombre}: ${d.sintomas}" }
        return "\n**Enfermedades comunes en ${species}:**\n$list"
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
