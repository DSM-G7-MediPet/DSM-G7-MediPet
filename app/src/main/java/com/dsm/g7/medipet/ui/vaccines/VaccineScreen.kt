package com.dsm.g7.medipet.ui.vaccines

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.ArrowBack

// Modelo temporal visual (Luego lo reemplazaremos con tu Entidad de Room)
data class VaccineUI(val id: Int, val name: String, val type: String, val vetName: String, val date: String, val isApplied: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineScreen(viewModel: VaccineViewModel = viewModel(),
                  onNavigateBack: () -> Unit = {}) {
    // Estado temporal para simular las vacunas
    var vaccines by remember {
        mutableStateOf(
            listOf(
                VaccineUI(1, "Antirrábica", "Anual", "Dr. Pérez", "15/06/2026", false),
                VaccineUI(2, "Parvovirus", "Cachorro", "Dra. Gómez", "20/05/2026", true)
            )
        )
    }

    // Estado para mostrar u ocultar el diálogo de agregar vacuna
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendario de Vacunas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar Vacuna")
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (vaccines.isEmpty()) {
                Text("No hay vacunas registradas.", modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(vaccines) { vaccine ->
                        VaccineCard(
                            vaccine = vaccine,
                            onToggleApplied = {
                                // Lógica temporal para cambiar el estado visual
                                vaccines = vaccines.map {
                                    if (it.id == vaccine.id) it.copy(isApplied = !it.isApplied) else it
                                }
                            }
                        )
                    }
                }
            }
        }

        // Mostrar el diálogo si el estado es true
        if (showAddDialog) {
            AddVaccineDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { newVaccine ->
                    vaccines = vaccines + newVaccine
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun VaccineCard(vaccine: VaccineUI, onToggleApplied: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = vaccine.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Tipo: ${vaccine.type}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Veterinario: ${vaccine.vetName}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Fecha: ${vaccine.date}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aplicada", style = MaterialTheme.typography.labelSmall)
                Checkbox(
                    checked = vaccine.isApplied,
                    onCheckedChange = { onToggleApplied() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVaccineDialog(onDismiss: () -> Unit, onAdd: (VaccineUI) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var vetName by remember { mutableStateOf("") }

    // Variables para el control de la fecha
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var selectedDateText by remember { mutableStateOf("Seleccionar Fecha") }

    // Si showDatePicker es true, mostramos el calendario flotante
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Convertimos los milisegundos seleccionados a formato dd/MM/yyyy
                    datePickerState.selectedDateMillis?.let { millis ->
                        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        selectedDateText = formatter.format(Date(millis))
                    }
                    showDatePicker = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            // Este es el componente visual del calendario de Material 3
            DatePicker(state = datePickerState)
        }
    }

    // El diálogo original del formulario
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Vacuna") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre de vacuna") })
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Tipo (ej. Anual)") })
                OutlinedTextField(value = vetName, onValueChange = { vetName = it }, label = { Text("Veterinario") })

                // Botón que abre el calendario
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedDateText)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && selectedDateText != "Seleccionar Fecha") {
                    // LLamamos a la función del ViewModel en vez del método anterior
                    onAdd(VaccineUI(0, name, type, vetName, selectedDateText, false))
                }
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewVaccineScreen() {
    MaterialTheme {
        VaccineScreen()
    }
}