package com.dsm.g7.medipet.ui.vaccines

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsm.g7.medipet.data.local.Vaccine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineScreen(
    petId: String = "",
    isReadOnly: Boolean = false,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val factory = remember(petId) {
        VaccineViewModelFactory(context.applicationContext as Application, petId)
    }
    val viewModel: VaccineViewModel = viewModel(factory = factory)

    val vaccines by viewModel.vaccines.collectAsState()
    val petNames by viewModel.petNames.collectAsState()
    val isAllMode = viewModel.isAllPetsMode

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isAllMode) "Todas las Vacunas" else "Calendario de Vacunas",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (!isAllMode && !isReadOnly) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar Vacuna")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (isAllMode) {
                Text(
                    "Vacunas de todas tus mascotas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (vaccines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Vaccines,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No hay vacunas registradas",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isAllMode && !isReadOnly) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Toca el botón + para agregar una",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vaccines, key = { it.id }) { vaccine ->
                        VaccineCard(
                            vaccine = vaccine,
                            petName = if (isAllMode) petNames[vaccine.petId] else null,
                            isReadOnly = isReadOnly,
                            onToggleApplied = { viewModel.toggleVaccineApplied(vaccine) },
                            onDelete = if (!isAllMode && !isReadOnly) {
                                { viewModel.deleteVaccine(vaccine) }
                            } else null
                        )
                    }
                }
            }
        }

        if (showAddDialog && !isReadOnly) {
            AddVaccineDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name, type, vetName, dateMillis ->
                    viewModel.addVaccine(name, type, vetName, dateMillis)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun VaccineCard(
    vaccine: Vaccine,
    petName: String? = null,
    isReadOnly: Boolean = false,
    onToggleApplied: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val formatter = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vaccine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (vaccine.type.isNotBlank()) {
                    Text("Tipo: ${vaccine.type}", style = MaterialTheme.typography.bodySmall)
                }
                if (vaccine.vetName.isNotBlank()) {
                    Text("Veterinario: ${vaccine.vetName}", style = MaterialTheme.typography.bodySmall)
                }
                // Compare calendar days (not raw timestamps) to avoid UTC-midnight vs local timezone bugs
                val todayLocalStart = remember {
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                val tomorrowLocalStart = todayLocalStart + 86_400_000L
                val vaccineDayLocalStart = remember(vaccine.dateMillis) {
                    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = vaccine.dateMillis
                    }
                    Calendar.getInstance().apply {
                        set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH),
                            utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                val dateColor = when {
                    !vaccine.isApplied && vaccineDayLocalStart < todayLocalStart -> MaterialTheme.colorScheme.error
                    !vaccine.isApplied && vaccineDayLocalStart <= tomorrowLocalStart -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                Text(
                    "Fecha: ${formatter.format(Date(vaccine.dateMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = dateColor
                )
                if (!vaccine.isApplied) {
                    val (badgeText, badgeColor) = when {
                        vaccineDayLocalStart < todayLocalStart -> "Vencida" to MaterialTheme.colorScheme.error
                        vaccineDayLocalStart <= tomorrowLocalStart -> "Próxima" to MaterialTheme.colorScheme.tertiary
                        else -> null to null
                    }
                    if (badgeText != null && badgeColor != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text(badgeText, style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = badgeColor.copy(alpha = 0.15f),
                                labelColor = badgeColor
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
                if (petName != null) {
                    Text(
                        "Mascota: $petName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aplicada", style = MaterialTheme.typography.labelSmall)
                Checkbox(
                    checked = vaccine.isApplied,
                    onCheckedChange = { onToggleApplied() },
                    enabled = !isReadOnly
                )
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVaccineDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, type: String, vetName: String, dateMillis: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var vetName by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Vacuna") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre de vacuna") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = type, onValueChange = { type = it },
                    label = { Text("Tipo (ej. Anual)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vetName, onValueChange = { vetName = it },
                    label = { Text("Veterinario") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        datePickerState.selectedDateMillis
                            ?.let { dateFormatter.format(Date(it)) }
                            ?: "Seleccionar Fecha"
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val millis = datePickerState.selectedDateMillis
                if (name.isNotBlank() && millis != null) {
                    onAdd(name, type, vetName, millis)
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
