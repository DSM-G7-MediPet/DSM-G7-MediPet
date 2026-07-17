package com.dsm.g7.medipet.ui.breed

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.BuildConfig
import com.dsm.g7.medipet.data.local.AppDatabase
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BreedResult(val label: String, val confidence: Float)

class BreedClassifierViewModel(app: Application, private val petId: String) : AndroidViewModel(app) {

    private val petDao = AppDatabase.getDatabase(app).petDao()

    private val _results = MutableStateFlow<List<BreedResult>>(emptyList())
    val results: StateFlow<List<BreedResult>> = _results.asStateFlow()

    private val _isClassifying = MutableStateFlow(false)
    val isClassifying: StateFlow<Boolean> = _isClassifying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _breedSaved = MutableStateFlow(false)
    val breedSaved: StateFlow<Boolean> = _breedSaved.asStateFlow()

    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    fun classify(bitmap: Bitmap) {
        viewModelScope.launch {
            _isClassifying.value = true
            _results.value = emptyList()
            _error.value = null
            try {
                val inputContent = content {
                    image(bitmap)
                    text(
                        "Analiza esta imagen e identifica la raza de la mascota. " +
                        "Responde ÚNICAMENTE con una lista de hasta 5 razas probables, en este formato exacto (una por línea):\n" +
                        "Raza: <nombre>, Confianza: <número entero del 0 al 100>\n" +
                        "Ejemplo:\nRaza: Siamés, Confianza: 87\nRaza: Balinés, Confianza: 9\n" +
                        "Si la imagen no contiene un animal responde exactamente: NO_ANIMAL\n" +
                        "Sin texto adicional, sin explicaciones."
                    )
                }
                val response = model.generateContent(inputContent)
                val text = response.text?.trim() ?: ""
                when {
                    text == "NO_ANIMAL" ->
                        _error.value = "No se detectó un animal en la imagen."
                    text.isBlank() ->
                        _error.value = "No se recibió respuesta del modelo."
                    else -> {
                        val parsed = parseBreedResponse(text)
                        if (parsed.isEmpty()) _error.value = "No se pudo identificar la raza."
                        else _results.value = parsed
                    }
                }
            } catch (e: Exception) {
                _error.value = "Error al analizar la imagen: ${e.message}"
            } finally {
                _isClassifying.value = false
            }
        }
    }

    private fun parseBreedResponse(text: String): List<BreedResult> =
        text.lines().mapNotNull { line ->
            val raza = Regex("Raza:\\s*([^,]+)").find(line)?.groupValues?.get(1)?.trim()
            val conf = Regex("Confianza:\\s*(\\d+(?:\\.\\d+)?)").find(line)?.groupValues?.get(1)?.toFloatOrNull()
            if (raza != null && conf != null) BreedResult(raza, conf / 100f) else null
        }.sortedByDescending { it.confidence }

    fun acceptBreed(breed: String) {
        viewModelScope.launch {
            val pet = petDao.getPetById(petId) ?: return@launch
            petDao.updatePet(pet.copy(breed = breed))
            _breedSaved.value = true
        }
    }

    fun clearResults() {
        _results.value = emptyList()
        _error.value = null
    }
}

class BreedClassifierViewModelFactory(
    private val app: Application,
    private val petId: String
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BreedClassifierViewModel(app, petId) as T
    }
}
