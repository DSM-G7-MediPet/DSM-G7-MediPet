package com.dsm.g7.medipet.ui.breed

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

private val TealPrimary = Color(0xFF0D6E6E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreedClassifierScreen(
    petId: String,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val factory = remember(petId) {
        BreedClassifierViewModelFactory(context.applicationContext as Application, petId)
    }
    val vm: BreedClassifierViewModel = viewModel(factory = factory)

    val results by vm.results.collectAsState()
    val isClassifying by vm.isClassifying.collectAsState()
    val error by vm.error.collectAsState()
    val breedSaved by vm.breedSaved.collectAsState()

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        capturedBitmap = bmp
        vm.classify(bmp)
    }

    LaunchedEffect(breedSaved) {
        if (breedSaved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identificar Raza", fontWeight = FontWeight.Bold) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!hasCameraPermission) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Se necesita permiso de cámara para identificar la raza.", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (capturedBitmap == null) {
                // Camera view
                CameraBreedCapture(
                    onCaptureReady = { imageCaptureRef.value = it },
                    onCaptureBitmap = { bmp ->
                        capturedBitmap = bmp
                        vm.classify(bmp)
                    },
                    onOpenGallery = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                // Results view
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Captured image
                    Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Foto capturada",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Razas detectadas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (isClassifying) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = TealPrimary)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Analizando imagen...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else if (error != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Text(
                                    error!!,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        } else if (results.isEmpty()) {
                            Text(
                                "No se detectaron razas con suficiente confianza.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            results.forEachIndexed { index, result ->
                                BreedResultCard(
                                    rank = index + 1,
                                    breed = result.label,
                                    confidence = result.confidence,
                                    onAccept = { vm.acceptBreed(result.label) }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                capturedBitmap = null
                                vm.clearResults()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tomar otra foto")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreedResultCard(rank: Int, breed: String, confidence: Float, onAccept: () -> Unit) {
    val pct = (confidence * 100).roundToInt()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(TealPrimary, shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("#$rank", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(breed, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { confidence },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = TealPrimary
                )
                Text("$pct% de confianza", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onAccept, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Aceptar", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CameraBreedCapture(
    onCaptureReady: (ImageCapture) -> Unit,
    onCaptureBitmap: (Bitmap) -> Unit,
    onOpenGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture
                    onCaptureReady(capture)
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón galería
            FilledTonalButton(
                onClick = onOpenGallery,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "Galería", modifier = Modifier.size(24.dp))
            }

            // Botón cámara (principal)
            Button(
                onClick = {
                    imageCapture?.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = image.toBitmap()
                                image.close()
                                onCaptureBitmap(bmp)
                            }
                            override fun onError(exc: ImageCaptureException) {}
                        }
                    )
                },
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Capturar", modifier = Modifier.size(32.dp))
            }
        }
    }
}
