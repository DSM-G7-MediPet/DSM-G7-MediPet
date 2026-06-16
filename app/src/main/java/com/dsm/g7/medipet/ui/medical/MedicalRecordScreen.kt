package com.dsm.g7.medipet.ui.medical

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dsm.g7.medipet.data.local.MedicalRecord
import com.dsm.g7.medipet.util.PdfExporter
import com.dsm.g7.medipet.util.VoiceRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private fun combineMedicalDateAndTime(utcMidnightMillis: Long, hour: Int, minute: Int): Long {
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
fun MedicalRecordScreen(
    petId: String,
    petName: String = "",
    isReadOnly: Boolean = false,
    appointmentFirestoreId: String = "",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val factory = remember(petId) {
        MedicalRecordViewModelFactory(context.applicationContext as Application, petId)
    }
    val viewModel: MedicalRecordViewModel = viewModel(factory = factory)
    val records by viewModel.records.collectAsState()
    val pet by viewModel.pet.collectAsState()
    val vaccines by viewModel.vaccines.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    var showCamera by remember { mutableStateOf(false) }

    var capturedPhotoUri by remember { mutableStateOf("") }
    var formDiagnosis by remember { mutableStateOf("") }
    var formTreatment by remember { mutableStateOf("") }
    var formMedications by remember { mutableStateOf("") }
    var formVetName by remember { mutableStateOf("") }
    var formDateMillis by remember { mutableStateOf<Long?>(null) }
    var formVoiceFile by remember { mutableStateOf<File?>(null) }

    fun resetForm() {
        capturedPhotoUri = ""
        formDiagnosis = ""
        formTreatment = ""
        formMedications = ""
        formVetName = ""
        formDateMillis = null
        formVoiceFile = null
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showCamera = true }

    val canMarkAttended = !isReadOnly && appointmentFirestoreId.isNotBlank() && records.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (petName.isNotBlank()) "Historial de $petName" else "Historial Médico",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Regresar")
                        }
                    },
                    actions = {
                        // PDF export button
                        if (records.isNotEmpty() && pet != null) {
                            IconButton(onClick = {
                                val currentPet = pet ?: return@IconButton
                                val file = PdfExporter.exportPdf(context, currentPet, records, vaccines)
                                PdfExporter.sharePdf(context, file)
                            }) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = "Exportar PDF")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (canMarkAttended) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                viewModel.markAppointmentAttended(appointmentFirestoreId)
                                onNavigateBack()
                            },
                            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                            text = { Text("Marcar atendida") },
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (!isReadOnly) {
                        FloatingActionButton(
                            onClick = { viewModel.openAddDialog() },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Agregar registro")
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            ) {
                if (canMarkAttended) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Historial completado. Puedes marcar la cita como atendida.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (records.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No hay registros médicos.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!isReadOnly && appointmentFirestoreId.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Agrega un registro para poder marcar la cita como atendida.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(records, key = { it.id }) { record ->
                            MedicalRecordCard(
                                record = record,
                                onDelete = if (!isReadOnly) {
                                    { viewModel.deleteRecord(record) }
                                } else null
                            )
                        }
                    }
                }
            }

            if (showAddDialog && !showCamera && !isReadOnly) {
                AddMedicalRecordDialog(
                    diagnosis = formDiagnosis,
                    onDiagnosisChange = { formDiagnosis = it },
                    treatment = formTreatment,
                    onTreatmentChange = { formTreatment = it },
                    medications = formMedications,
                    onMedicationsChange = { formMedications = it },
                    vetName = formVetName,
                    onVetNameChange = { formVetName = it },
                    selectedDateMillis = formDateMillis,
                    onDateSelected = { formDateMillis = it },
                    capturedPhotoUri = capturedPhotoUri,
                    onRequestCamera = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) showCamera = true
                        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    },
                    voiceFile = formVoiceFile,
                    onVoiceFileChanged = { formVoiceFile = it },
                    onDismiss = {
                        viewModel.closeAddDialog()
                        resetForm()
                    },
                    onAdd = { diagnosis, treatment, medications, vetName, dateMillis ->
                        viewModel.addRecord(
                            diagnosis, treatment, medications, vetName, dateMillis,
                            capturedPhotoUri,
                            formVoiceFile?.absolutePath ?: ""
                        )
                        resetForm()
                    }
                )
            }
        }

        if (showCamera) {
            CameraCapture(
                onImageFile = { file ->
                    capturedPhotoUri = file.absolutePath
                    showCamera = false
                },
                onClose = { showCamera = false }
            )
        }
    }
}

@Composable
fun MedicalRecordCard(record: MedicalRecord, onDelete: (() -> Unit)? = null) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    var showPhotoDialog by remember { mutableStateOf(false) }

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
                        text = "Diagnóstico: ${record.diagnosis}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (record.treatment.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tratamiento: ${record.treatment}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (record.medications.isNotBlank()) {
                        Text("Medicamentos: ${record.medications}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (record.vetName.isNotBlank()) {
                        Text(
                            "Vet: ${record.vetName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatter.format(Date(record.dateMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (record.voiceNoteUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                try {
                                    MediaPlayer.create(context, Uri.parse(record.voiceNoteUrl))?.start()
                                } catch (_: Exception) {}
                            }
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reproducir nota de voz", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (record.photoUri.isNotBlank() && record.photoUri.startsWith("https")) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(record.photoUri).crossfade(true).build(),
                            contentDescription = "Foto del registro",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(80.dp).clickable { showPhotoDialog = true }
                        )
                    } else if (record.photoUri.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Foto adjunta", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showPhotoDialog && record.photoUri.startsWith("https")) {
        Dialog(onDismissRequest = { showPhotoDialog = false }) {
            Box(modifier = Modifier.fillMaxWidth().background(Color.Black)) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(record.photoUri).crossfade(true).build(),
                    contentDescription = "Foto completa",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
                IconButton(onClick = { showPhotoDialog = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicalRecordDialog(
    diagnosis: String,
    onDiagnosisChange: (String) -> Unit,
    treatment: String,
    onTreatmentChange: (String) -> Unit,
    medications: String,
    onMedicationsChange: (String) -> Unit,
    vetName: String,
    onVetNameChange: (String) -> Unit,
    selectedDateMillis: Long?,
    onDateSelected: (Long?) -> Unit,
    capturedPhotoUri: String,
    onRequestCamera: () -> Unit,
    voiceFile: File?,
    onVoiceFileChanged: (File?) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (diagnosis: String, treatment: String, medications: String, vetName: String, dateMillis: Long) -> Unit
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDateMillis ?: System.currentTimeMillis()
    )
    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // Time picker
    val initialHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val timePickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = 0, is24Hour = true)
    var showTimePicker by remember { mutableStateOf(false) }

    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.start()
            isRecording = true
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateSelected(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Seleccionar hora", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 20.dp))
                    TimePicker(state = timePickerState)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
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
        title = { Text("Nuevo Registro Médico") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = diagnosis, onValueChange = onDiagnosisChange,
                    label = { Text("Diagnóstico *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = treatment, onValueChange = onTreatmentChange,
                    label = { Text("Tratamiento") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = medications, onValueChange = onMedicationsChange,
                    label = { Text("Medicamentos") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vetName, onValueChange = onVetNameChange,
                    label = { Text("Veterinario") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date button
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        selectedDateMillis?.let { dateFormatter.format(Date(it)) }
                            ?: "Seleccionar Fecha"
                    )
                }

                // Time button
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(String.format(Locale.getDefault(), "Hora: %02d:%02d", timePickerState.hour, timePickerState.minute))
                }

                // Camera button
                OutlinedButton(onClick = onRequestCamera, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (capturedPhotoUri.isNotBlank()) "Foto capturada ✓" else "Tomar foto")
                }

                // Voice note button
                OutlinedButton(
                    onClick = {
                        if (isRecording) {
                            val file = voiceRecorder.stop()
                            onVoiceFileChanged(file)
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
                            voiceFile != null -> "Nota grabada ✓"
                            else -> "Grabar nota de voz"
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (diagnosis.isNotBlank()) {
                    val datePart = selectedDateMillis
                    val finalMillis = if (datePart != null) {
                        combineMedicalDateAndTime(datePart, timePickerState.hour, timePickerState.minute)
                    } else {
                        System.currentTimeMillis()
                    }
                    voiceRecorder.release()
                    onAdd(diagnosis, treatment, medications, vetName, finalMillis)
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = {
                voiceRecorder.release()
                onDismiss()
            }) { Text("Cancelar") }
        }
    )
}

@Composable
fun CameraCapture(
    onImageFile: (File) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
            }
            FloatingActionButton(onClick = {
                val file = File(context.filesDir, "photo_${System.currentTimeMillis()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture?.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) { onImageFile(file) }
                        override fun onError(exception: ImageCaptureException) {}
                    }
                )
            }) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Capturar")
            }
        }
    }
}
