package com.dsm.g7.medipet.ui.appointments

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsm.g7.medipet.data.local.Appointment
import com.dsm.g7.medipet.data.local.AppointmentStatus
import com.dsm.g7.medipet.data.local.Pet
import com.dsm.g7.medipet.util.VoiceRecorder
import java.text.SimpleDateFormat
import java.util.*

private fun combineDateAndTime(utcMidnightMillis: Long, hour: Int, minute: Int): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMidnightMillis
    }
    return Calendar.getInstance().apply {
        set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentScreen(
    viewModel: AppointmentViewModel = viewModel(),
    petIdFilter: String = "",
    onNavigateBack: () -> Unit
) {
    val appointments by viewModel.appointments.collectAsState()
    val pets by viewModel.pets.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()

    var selectedFilter by remember { mutableStateOf<AppointmentStatus?>(null) }

    val filteredAppointments by remember(appointments, selectedFilter, petIdFilter) {
        derivedStateOf {
            val base = if (petIdFilter.isNotBlank()) appointments.filter { it.petId == petIdFilter }
                       else appointments
            if (selectedFilter == null) base else base.filter { it.status == selectedFilter }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Citas Veterinarias", fontWeight = FontWeight.Bold) },
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
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Agendar cita")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                item {
                    FilterChip(selected = selectedFilter == null, onClick = { selectedFilter = null }, label = { Text("Todas") })
                }
                item {
                    FilterChip(selected = selectedFilter == AppointmentStatus.PENDING, onClick = { selectedFilter = AppointmentStatus.PENDING }, label = { Text("Pendiente") })
                }
                item {
                    FilterChip(selected = selectedFilter == AppointmentStatus.CONFIRMED, onClick = { selectedFilter = AppointmentStatus.CONFIRMED }, label = { Text("Confirmada") })
                }
                item {
                    FilterChip(selected = selectedFilter == AppointmentStatus.ATTENDED, onClick = { selectedFilter = AppointmentStatus.ATTENDED }, label = { Text("Atendida") })
                }
                item {
                    FilterChip(selected = selectedFilter == AppointmentStatus.CANCELLED, onClick = { selectedFilter = AppointmentStatus.CANCELLED }, label = { Text("Cancelada") })
                }
                item {
                    FilterChip(selected = selectedFilter == AppointmentStatus.EXPIRED, onClick = { selectedFilter = AppointmentStatus.EXPIRED }, label = { Text("Vencida") })
                }
            }

            if (filteredAppointments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay citas agendadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredAppointments, key = { it.id }) { appointment ->
                        AppointmentCard(
                            appointment = appointment,
                            onDelete = { viewModel.deleteAppointment(appointment) }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddAppointmentDialog(
                pets = pets,
                onDismiss = { viewModel.closeAddDialog() },
                onAdd = { petId, petName, dateMillis, reason, vetName, photoUri, voiceNoteUrl ->
                    viewModel.addAppointment(petId, petName, dateMillis, reason, vetName, photoUri, voiceNoteUrl)
                }
            )
        }
    }
}

@Composable
fun AppointmentCard(
    appointment: Appointment,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    val statusColor = when (appointment.status) {
        AppointmentStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        AppointmentStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        AppointmentStatus.ATTENDED -> MaterialTheme.colorScheme.secondary
        AppointmentStatus.CANCELLED -> MaterialTheme.colorScheme.error
        AppointmentStatus.EXPIRED -> MaterialTheme.colorScheme.outline
    }
    val statusLabel = when (appointment.status) {
        AppointmentStatus.PENDING -> "Pendiente"
        AppointmentStatus.CONFIRMED -> "Confirmada"
        AppointmentStatus.ATTENDED -> "Atendida"
        AppointmentStatus.CANCELLED -> "Cancelada"
        AppointmentStatus.EXPIRED -> "Vencida"
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
                    Text(
                        text = appointment.petName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = appointment.reason, style = MaterialTheme.typography.bodyMedium)
                    if (appointment.vetName.isNotBlank()) {
                        Text(
                            text = "Vet: ${appointment.vetName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatter.format(Date(appointment.dateMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (appointment.voiceNoteUrl.isNotBlank()) {
                        AppointmentAudioButton(url = appointment.voiceNoteUrl)
                    }
                    if (appointment.photoUri.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Foto adjunta", style = MaterialTheme.typography.labelSmall)
                        }
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
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppointmentAudioButton(url: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var player    by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(url) {
        onDispose {
            player?.stop()
            player?.release()
            player = null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            if (isPlaying) {
                player?.stop()
                player?.release()
                player = null
                isPlaying = false
            } else {
                try {
                    val mp = MediaPlayer.create(context, Uri.parse(url))
                    mp?.setOnCompletionListener {
                        isPlaying = false
                        it.release()
                        player = null
                    }
                    mp?.start()
                    player = mp
                    isPlaying = mp != null
                } catch (_: Exception) {
                    isPlaying = false
                }
            }
        }
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Detener" else "Reproducir",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isPlaying) "Reproduciendo..." else "Nota de voz",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppointmentDialog(
    pets: List<Pet>,
    onDismiss: () -> Unit,
    onAdd: (petId: String, petName: String, dateMillis: Long, reason: String, vetName: String, photoUri: String, voiceNoteUrl: String) -> Unit
) {
    val context = LocalContext.current
    var selectedPet by remember { mutableStateOf(pets.firstOrNull()) }
    var petMenuExpanded by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var vetName by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf<String?>(null) }

    // Date picker: only today and future
    val todayUtcMidnight = remember {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtcMidnight
        }
    )
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // Time picker
    val initialHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val timePickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = 0, is24Hour = true)
    var showTimePicker by remember { mutableStateOf(false) }

    // Voice recorder
    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordedVoiceFile by remember { mutableStateOf<java.io.File?>(null) }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.start()
            isRecording = true
        }
    }

    // Photo picker
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedPhotoUri = uri
    }

    // Date picker dialog
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

    // Time picker dialog
    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Seleccionar hora",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { showTimePicker = false }) { Text("Aceptar") }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agendar Cita") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = petMenuExpanded,
                    onExpandedChange = { petMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedPet?.name ?: "Sin mascotas",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mascota") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = petMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = petMenuExpanded,
                        onDismissRequest = { petMenuExpanded = false }
                    ) {
                        pets.forEach { pet ->
                            DropdownMenuItem(
                                text = { Text("${pet.name} (${pet.species})") },
                                onClick = { selectedPet = pet; petMenuExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Motivo de la cita") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vetName, onValueChange = { vetName = it },
                    label = { Text("Veterinario") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date button
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        datePickerState.selectedDateMillis?.let { dateFormatter.format(Date(it)) }
                            ?: "Seleccionar Fecha"
                    )
                }

                // Time button
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(String.format(Locale.getDefault(), "Hora: %02d:%02d", timePickerState.hour, timePickerState.minute))
                }

                if (dateError != null) {
                    Text(dateError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                // Voice note
                OutlinedButton(
                    onClick = {
                        if (isRecording) {
                            recordedVoiceFile = voiceRecorder.stop()
                            isRecording = false
                        } else {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                voiceRecorder.start()
                                isRecording = true
                            } else {
                                audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when {
                            isRecording -> "Detener grabación..."
                            recordedVoiceFile != null -> "Nota grabada ✓"
                            else -> "Grabar nota de voz"
                        }
                    )
                }

                // Photo attach
                OutlinedButton(onClick = { photoLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedPhotoUri != null) "Foto seleccionada ✓" else "Adjuntar foto")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val pet = selectedPet ?: return@Button
                val datePart = datePickerState.selectedDateMillis ?: run {
                    dateError = "Selecciona una fecha"
                    return@Button
                }
                if (reason.isBlank()) return@Button
                val combinedMillis = combineDateAndTime(datePart, timePickerState.hour, timePickerState.minute)
                if (combinedMillis <= System.currentTimeMillis()) {
                    dateError = "La fecha y hora deben ser futuras"
                    return@Button
                }
                dateError = null
                onAdd(
                    pet.id, pet.name, combinedMillis, reason, vetName,
                    selectedPhotoUri?.toString() ?: "",
                    recordedVoiceFile?.absolutePath ?: ""
                )
                voiceRecorder.release()
            }) { Text("Agendar") }
        },
        dismissButton = {
            TextButton(onClick = {
                voiceRecorder.release()
                onDismiss()
            }) { Text("Cancelar") }
        }
    )
}
