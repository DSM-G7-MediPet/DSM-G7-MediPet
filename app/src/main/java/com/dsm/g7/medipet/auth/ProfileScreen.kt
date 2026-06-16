package com.dsm.g7.medipet.auth

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dsm.g7.medipet.data.local.UserRole
import com.dsm.g7.medipet.ui.medical.CameraCapture
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val profilePhotoUrl by viewModel.profilePhotoUrl.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val specialty by viewModel.specialty.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(errorMessage!!)
            viewModel.clearError()
        }
    }

    var isEditing by remember { mutableStateOf(false) }
    var editName by remember(user?.displayName) { mutableStateOf(user?.displayName ?: "") }
    var editPhone by remember(phoneNumber) { mutableStateOf(phoneNumber) }
    var editSpecialty by remember(specialty) { mutableStateOf(specialty) }

    var showCamera by remember { mutableStateOf(false) }
    var capturedPhotoFile by remember { mutableStateOf<File?>(null) }
    var capturedGalleryUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> capturedGalleryUri = uri }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showCamera = true }

    val memberSince = remember(user) {
        val ts = user?.metadata?.creationTimestamp ?: return@remember "Desconocido"
        SimpleDateFormat("MMMM yyyy", Locale("es")).format(Date(ts))
            .replaceFirstChar { it.uppercase() }
    }

    val roleLabel = when (userRole) {
        UserRole.VET -> "Veterinario"
        UserRole.OWNER -> "Dueño de mascota"
        UserRole.UNKNOWN -> "Detectando..."
    }
    val roleBadgeColor = when (userRole) {
        UserRole.VET -> MaterialTheme.colorScheme.tertiary
        UserRole.OWNER -> MaterialTheme.colorScheme.primary
        UserRole.UNKNOWN -> MaterialTheme.colorScheme.outline
    }

    // Pending photo to upload
    val pendingPhoto = capturedPhotoFile != null || capturedGalleryUri != null

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Mi Perfil", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Regresar")
                        }
                    },
                    actions = {
                        if (isEditing) {
                            TextButton(onClick = {
                                isEditing = false
                                editName = user?.displayName ?: ""
                                editPhone = phoneNumber
                                editSpecialty = specialty
                                capturedPhotoFile = null
                                capturedGalleryUri = null
                            }) { Text("Cancelar") }
                        } else {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Editar")
                            }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Profile photo ──
                Box(contentAlignment = Alignment.BottomEnd) {
                    val photoToShow = when {
                        capturedGalleryUri != null -> capturedGalleryUri.toString()
                        capturedPhotoFile != null -> capturedPhotoFile!!.absolutePath
                        profilePhotoUrl.isNotBlank() -> profilePhotoUrl
                        else -> null
                    }
                    if (photoToShow != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photoToShow)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Foto de perfil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (isEditing) {
                        SmallFloatingActionButton(
                            onClick = {},
                            containerColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null,
                                modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isEditing) {
                    // View mode: name + email + badge
                    Text(
                        text = user?.displayName?.takeIf { it.isNotBlank() } ?: "Sin nombre",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = user?.email ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(roleLabel, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                if (userRole == UserRole.VET) Icons.Filled.MedicalInformation else Icons.Filled.Pets,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = roleBadgeColor.copy(alpha = 0.15f),
                            labelColor = roleBadgeColor
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Personal info card
                    ProfileInfoCard(title = "Información personal") {
                        ProfileInfoRow(
                            icon = Icons.Filled.Phone,
                            label = "Teléfono",
                            value = phoneNumber.ifBlank { "No especificado" }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ProfileInfoRow(
                            icon = Icons.Filled.CalendarMonth,
                            label = "Miembro desde",
                            value = memberSince
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ProfileInfoRow(
                            icon = Icons.Filled.Email,
                            label = "Correo electrónico",
                            value = user?.email ?: ""
                        )
                    }

                    // VET: professional info card
                    if (userRole == UserRole.VET) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoCard(title = "Información profesional") {
                            ProfileInfoRow(
                                icon = Icons.Filled.MedicalInformation,
                                label = "Especialidad",
                                value = specialty.ifBlank { "No especificada" }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            ProfileInfoRow(
                                icon = Icons.Filled.WorkOutline,
                                label = "Tipo de cuenta",
                                value = "Veterinario"
                            )
                        }
                    }

                    // OWNER: info card
                    if (userRole == UserRole.OWNER) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoCard(title = "Mi cuenta") {
                            ProfileInfoRow(
                                icon = Icons.Filled.Pets,
                                label = "Tipo de cuenta",
                                value = "Dueño de mascota"
                            )
                        }
                    }

                } else {
                    // Edit mode: photo pickers + fields
                    Text(
                        "Cambiar foto de perfil",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_GRANTED
                                ) showCamera = true
                                else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cámara", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Galería", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (pendingPhoto) {
                        Text(
                            "Nueva foto seleccionada ✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre completo") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Teléfono") },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (userRole == UserRole.VET) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editSpecialty,
                            onValueChange = { editSpecialty = it },
                            label = { Text("Especialidad") },
                            leadingIcon = { Icon(Icons.Filled.MedicalInformation, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            viewModel.updateProfile(editName, editPhone, editSpecialty)
                            if (pendingPhoto) {
                                viewModel.uploadProfilePhoto(context, capturedPhotoFile, capturedGalleryUri)
                                capturedPhotoFile = null
                                capturedGalleryUri = null
                            }
                            isEditing = false
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Guardar cambios")
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerrar sesión")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showCamera) {
            CameraCapture(
                onImageFile = { file ->
                    capturedPhotoFile = file
                    capturedGalleryUri = null
                    showCamera = false
                },
                onClose = { showCamera = false }
            )
        }
    }
}

@Composable
private fun ProfileInfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
