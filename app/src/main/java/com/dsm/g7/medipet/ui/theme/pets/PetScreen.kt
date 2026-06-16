package com.dsm.g7.medipet.ui.pets

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dsm.g7.medipet.data.local.Pet
import com.dsm.g7.medipet.ui.medical.CameraCapture
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetScreen(
    viewModel: PetViewModel = viewModel(),
    onNavigateToVaccines: (petId: String) -> Unit = {},
    onNavigateToMedical: (petId: String) -> Unit = {},
    onNavigateToPetDetail: (petId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val pets by viewModel.pets.collectAsState()
    val petBadges by viewModel.petBadges.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showPetCamera by remember { mutableStateOf(false) }

    // Form state lifted so it survives the camera overlay
    var formName by remember { mutableStateOf("") }
    var formSpecies by remember { mutableStateOf("") }
    var formBreed by remember { mutableStateOf("") }
    var formAge by remember { mutableStateOf("") }
    var formWeight by remember { mutableStateOf("") }
    var capturedPetPhotoFile by remember { mutableStateOf<File?>(null) }
    var capturedPetGalleryUri by remember { mutableStateOf<Uri?>(null) }

    fun resetForm() {
        formName = ""; formSpecies = ""; formBreed = ""
        formAge = ""; formWeight = ""
        capturedPetPhotoFile = null; capturedPetGalleryUri = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> capturedPetGalleryUri = uri }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showPetCamera = true }

    val filteredPets = remember(pets, searchQuery) {
        if (searchQuery.isBlank()) pets
        else pets.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.species.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Mis Mascotas", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = {
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        }) {
                            Icon(Icons.Filled.Logout, contentDescription = "Cerrar sesión")
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
                    Icon(Icons.Filled.Add, contentDescription = "Agregar mascota")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar mascota...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                if (filteredPets.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Pets,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No tienes mascotas registradas"
                                       else "Sin resultados para \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Toca el botón + para agregar una",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filteredPets, key = { it.id }) { pet ->
                            PetCard(
                                pet = pet,
                                badge = petBadges[pet.id] ?: PetBadge.UP_TO_DATE,
                                onDelete = { viewModel.deletePet(pet) },
                                onViewVaccines = { onNavigateToVaccines(pet.id) },
                                onViewMedical = { onNavigateToMedical(pet.id) },
                                onNavigateToDetail = { onNavigateToPetDetail(pet.id) }
                            )
                        }
                    }
                }
            }

            if (showAddDialog && !showPetCamera) {
                AddPetDialog(
                    name = formName, onNameChange = { formName = it },
                    species = formSpecies, onSpeciesChange = { formSpecies = it },
                    breed = formBreed, onBreedChange = { formBreed = it },
                    age = formAge, onAgeChange = { formAge = it },
                    weight = formWeight, onWeightChange = { formWeight = it },
                    capturedPhotoFile = capturedPetPhotoFile,
                    capturedPhotoUri = capturedPetGalleryUri,
                    onRequestCamera = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) showPetCamera = true
                        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onRequestGallery = { galleryLauncher.launch("image/*") },
                    onDismiss = {
                        viewModel.closeAddDialog()
                        resetForm()
                    },
                    onAdd = { name, species, breed, age, weight ->
                        viewModel.addPet(name, species, breed, age, weight,
                            capturedPetPhotoFile, capturedPetGalleryUri)
                        resetForm()
                    }
                )
            }

            errorMessage?.let { msg ->
                LaunchedEffect(msg) { viewModel.clearError() }
            }
        }

        if (showPetCamera) {
            CameraCapture(
                onImageFile = { file ->
                    capturedPetPhotoFile = file
                    capturedPetGalleryUri = null
                    showPetCamera = false
                },
                onClose = { showPetCamera = false }
            )
        }
    }
}

@Composable
fun PetCard(
    pet: Pet,
    badge: PetBadge = PetBadge.UP_TO_DATE,
    onDelete: () -> Unit,
    onViewVaccines: () -> Unit,
    onViewMedical: () -> Unit,
    onNavigateToDetail: () -> Unit = {}
) {
    val context = LocalContext.current

    val badgeLabel = when (badge) {
        PetBadge.UP_TO_DATE -> "Al dia"
        PetBadge.VACCINE_SOON -> "Proxima"
        PetBadge.VACCINE_OVERDUE -> "Vencida"
    }
    val badgeColor = when (badge) {
        PetBadge.UP_TO_DATE -> Color(0xFF388E3C)
        PetBadge.VACCINE_SOON -> Color(0xFFF9A825)
        PetBadge.VACCINE_OVERDUE -> Color(0xFFD32F2F)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToDetail() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pet.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(pet.photoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Foto de ${pet.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Pets,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pet.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text(badgeLabel, style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = badgeColor.copy(alpha = 0.15f),
                                    labelColor = badgeColor
                                )
                            )
                        }
                        Text(
                            text = "${pet.species} · ${pet.breed}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${pet.ageYears} años · ${pet.weightKg} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onViewVaccines) {
                    Icon(Icons.Filled.Vaccines, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Vacunas")
                }
                TextButton(onClick = onViewMedical) {
                    Icon(Icons.Filled.MedicalInformation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Historial")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPetDialog(
    name: String, onNameChange: (String) -> Unit,
    species: String, onSpeciesChange: (String) -> Unit,
    breed: String, onBreedChange: (String) -> Unit,
    age: String, onAgeChange: (String) -> Unit,
    weight: String, onWeightChange: (String) -> Unit,
    capturedPhotoFile: File?,
    capturedPhotoUri: Uri?,
    onRequestCamera: () -> Unit,
    onRequestGallery: () -> Unit,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Int, Float) -> Unit
) {
    val hasPhoto = capturedPhotoFile != null || capturedPhotoUri != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Mascota") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = onNameChange,
                    label = { Text("Nombre") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = species, onValueChange = onSpeciesChange,
                    label = { Text("Especie (Perro, Gato...)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = breed, onValueChange = onBreedChange,
                    label = { Text("Raza") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = age, onValueChange = onAgeChange,
                    label = { Text("Edad (años)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weight, onValueChange = onWeightChange,
                    label = { Text("Peso (kg)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onRequestCamera,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (capturedPhotoFile != null) "Foto tomada ✓" else "Tomar foto")
                }
                OutlinedButton(
                    onClick = onRequestGallery,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (capturedPhotoUri != null) "Foto seleccionada ✓" else "Seleccionar de galería")
                }
                if (hasPhoto) {
                    Text(
                        "Foto lista para subir",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && species.isNotBlank()) {
                    onAdd(
                        name, species, breed,
                        age.toIntOrNull() ?: 0,
                        weight.toFloatOrNull() ?: 0f
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
