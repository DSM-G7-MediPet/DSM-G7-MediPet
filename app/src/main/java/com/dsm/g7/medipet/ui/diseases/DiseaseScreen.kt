package com.dsm.g7.medipet.ui.diseases

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsm.g7.medipet.data.local.Disease

private val TealPrimary = Color(0xFF0D6E6E)

private val ESPECIES = listOf("perro" to "🐶 Perro", "gato" to "🐱 Gato", "conejo" to "🐰 Conejo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseScreen(
    viewModel: DiseaseViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val diseases by viewModel.diseases.collectAsState()
    val selectedEspecie by viewModel.selectedEspecie.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedDisease by remember { mutableStateOf<Disease?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catálogo de Enfermedades", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TealPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Species filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ESPECIES) { (key, label) ->
                    FilterChip(
                        selected = selectedEspecie == key,
                        onClick = { viewModel.selectEspecie(key) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (isLoading && diseases.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TealPrimary)
                }
            } else if (diseases.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin datos. Verifica tu conexión.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(diseases, key = { it.id }) { disease ->
                        DiseaseCard(disease = disease, onClick = { selectedDisease = disease })
                    }
                }
            }
        }
    }

    selectedDisease?.let { disease ->
        DiseaseDetailDialog(disease = disease, onDismiss = { selectedDisease = null })
    }
}

@Composable
private fun DiseaseCard(disease: Disease, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.LocalHospital, null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                Text(disease.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            // Symptom chips (first 3)
            val sintomas = disease.sintomasList().take(3)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sintomas) { s ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(s, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Text(
                "Ver detalles →",
                style = MaterialTheme.typography.labelSmall,
                color = TealPrimary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun DiseaseDetailDialog(disease: Disease, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(disease.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Síntomas")
                disease.sintomasList().forEach { s ->
                    Text("• $s", style = MaterialTheme.typography.bodySmall)
                }
                SectionLabel("Descripción")
                Text(disease.descripcion, style = MaterialTheme.typography.bodySmall)
                SectionLabel("Recomendación")
                Text(disease.recomendacion, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TealPrimary)
}
