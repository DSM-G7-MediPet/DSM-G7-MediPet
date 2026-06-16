package com.dsm.g7.medipet.ui.vet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetHomeScreen(
    viewModel: VetHomeViewModel = viewModel(),
    onNavigateToProfile: () -> Unit = {},
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToPatients: () -> Unit = {},
    onNavigateToMedical: (petId: String, appointmentId: String) -> Unit = { _, _ -> }
) {
    val activeAppointments by viewModel.pendingAppointments.collectAsState()
    val historyAppointments by viewModel.historyAppointments.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    var vaccineTargetAppointment by remember { mutableStateOf<Map<String, Any>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Activas", "Historial")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Panel Veterinario", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToPatients) {
                        Icon(Icons.Filled.Pets, contentDescription = "Pacientes")
                    }
                    IconButton(onClick = onNavigateToDashboard) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Dashboard")
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Perfil")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title)
                                if (index == 0 && activeAppointments.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Badge { Text("${activeAppointments.size}") }
                                }
                            }
                        }
                    )
                }
            }

            val displayList = if (selectedTab == 0) activeAppointments else historyAppointments
            if (displayList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (selectedTab == 0) "No hay citas activas" else "Sin historial aún",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayList, key = { it["firestoreDocId"] as? String ?: it.hashCode().toString() }) { appointment ->
                        VetAppointmentCard(
                            appointment = appointment,
                            dateFormatter = dateFormatter,
                            isHistory = selectedTab == 1,
                            onStatusChange = { newStatus ->
                                val docId = appointment["firestoreDocId"] as? String
                                    ?: appointment["firestoreId"] as? String
                                    ?: return@VetAppointmentCard
                                viewModel.updateStatus(docId, newStatus)
                            },
                            onMarkAttended = {
                                val docId = appointment["firestoreDocId"] as? String
                                    ?: appointment["firestoreId"] as? String
                                    ?: return@VetAppointmentCard
                                val petId = appointment["petId"] as? String ?: ""
                                viewModel.tryMarkAttended(docId, petId) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Registra el historial médico antes de marcar como atendida"
                                        )
                                    }
                                }
                            },
                            onAddVaccine = { if (selectedTab == 0) vaccineTargetAppointment = appointment },
                            onNavigateToMedical = {
                                val petId = appointment["petId"] as? String ?: ""
                                val appointmentId = appointment["firestoreDocId"] as? String
                                    ?: appointment["firestoreId"] as? String
                                    ?: ""
                                if (petId.isNotBlank()) onNavigateToMedical(petId, appointmentId)
                            }
                        )
                    }
                }
            }
        }
    }

    vaccineTargetAppointment?.let { appt ->
        val petName = appt["petName"] as? String ?: "la mascota"
        AddVetVaccineDialog(
            petName = petName,
            onDismiss = { vaccineTargetAppointment = null },
            onAdd = { name, type, dateMillis, applied ->
                val petId = appt["petId"] as? String ?: return@AddVetVaccineDialog
                val ownerUid = appt["ownerUid"] as? String ?: return@AddVetVaccineDialog
                val vetName = FirebaseAuth.getInstance().currentUser?.displayName ?: ""
                viewModel.addVaccine(petId, ownerUid, name, type, vetName, dateMillis, applied)
                vaccineTargetAppointment = null
            }
        )
    }
}

@Composable
private fun VetAppointmentCard(
    appointment: Map<String, Any>,
    dateFormatter: SimpleDateFormat,
    isHistory: Boolean = false,
    onStatusChange: (String) -> Unit,
    onMarkAttended: () -> Unit,
    onAddVaccine: () -> Unit,
    onNavigateToMedical: () -> Unit = {}
) {
    val petName = appointment["petName"] as? String ?: "Mascota"
    val reason = appointment["reason"] as? String ?: ""
    val ownerName = appointment["ownerName"] as? String ?: ""
    val dateMillis = appointment["dateMillis"] as? Long ?: 0L
    val status = appointment["status"] as? String ?: "PENDING"

    val statusLabel = when (status) {
        "PENDING" -> "Pendiente"
        "CONFIRMED" -> "Confirmada"
        "ATTENDED" -> "Atendida"
        "CANCELLED" -> "Cancelada"
        "EXPIRED" -> "Vencida"
        else -> status
    }
    val statusColor = when (status) {
        "PENDING" -> MaterialTheme.colorScheme.tertiary
        "CONFIRMED" -> MaterialTheme.colorScheme.primary
        "ATTENDED" -> MaterialTheme.colorScheme.secondary
        "CANCELLED" -> MaterialTheme.colorScheme.error
        "EXPIRED" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = petName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = reason, style = MaterialTheme.typography.bodyMedium)
                    if (ownerName.isNotBlank()) {
                        Text(
                            text = "Dueño: $ownerName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (dateMillis > 0L) {
                        Text(
                            text = dateFormatter.format(java.util.Date(dateMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    AssistChip(
                        onClick = {},
                        label = { Text(statusLabel, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = statusColor.copy(alpha = 0.15f),
                            labelColor = statusColor
                        )
                    )
                    IconButton(onClick = onNavigateToMedical, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MedicalInformation, contentDescription = "Historial médico", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (!isHistory && status != "CANCELLED" && status != "ATTENDED") {
                        IconButton(onClick = onAddVaccine, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Vaccines, contentDescription = "Registrar vacuna", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            if (!isHistory && status != "CANCELLED" && status != "ATTENDED") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (status == "PENDING") {
                        TextButton(onClick = { onStatusChange("CONFIRMED") }) {
                            Text("Confirmar")
                        }
                    }
                    if (status == "CONFIRMED") {
                        TextButton(onClick = onMarkAttended) {
                            Text("Marcar atendida")
                        }
                    }
                    TextButton(onClick = { onStatusChange("CANCELLED") }) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVetVaccineDialog(
    petName: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, type: String, dateMillis: Long, applied: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar vacuna — $petName") },
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
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        datePickerState.selectedDateMillis
                            ?.let { dateFormatter.format(java.util.Date(it)) }
                            ?: "Seleccionar fecha"
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = applied, onCheckedChange = { applied = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ya fue aplicada", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val millis = datePickerState.selectedDateMillis
                if (name.isNotBlank() && millis != null) {
                    onAdd(name, type, millis, applied)
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
