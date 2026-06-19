package com.dsm.g7.medipet.ui.chat

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsm.g7.medipet.data.local.ChatMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val Teal   = Color(0xFF0D6E6E)
private val TealLt = Color(0xFF14A9A9)

private val quickReplies = listOf(
    "¿Es urgente esto?",
    "¿Vacunas que le faltan?",
    "Síntomas de emergencia",
    "¿Qué debo darle de comer?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    petId: String,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val factory = remember(petId) { ChatViewModelFactory(context.applicationContext as Application, petId) }
    val vm: ChatViewModel = viewModel(factory = factory)

    val messages      by vm.messages.collectAsState()
    val isTyping      by vm.isTyping.collectAsState()
    val streamingText by vm.streamingText.collectAsState()
    val error         by vm.error.collectAsState()
    val pet           by vm.pet.collectAsState()

    var inputText  by remember { mutableStateOf("") }
    var ttsEnabled by remember { mutableStateOf(false) }
    var showClear  by remember { mutableStateOf(false) }

    val listState    = rememberLazyListState()
    val coroutine    = rememberCoroutineScope()
    val snackbar     = remember { SnackbarHostState() }

    // ── TTS ──────────────────────────────────────────────────────────────────
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale("es", "MX")
            }
        }
        tts.value = t
        onDispose { t.shutdown() }
    }

    // Speak last model message when TTS is on
    LaunchedEffect(messages.size) {
        if (ttsEnabled) {
            val last = messages.lastOrNull()
            if (last?.role == "model") {
                tts.value?.speak(last.content, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    // ── Speech recognition ───────────────────────────────────────────────────
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) inputText = text
        }
    }

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty() || isTyping) {
            listState.animateScrollToItem(
                if (isTyping) messages.size else (messages.size - 1).coerceAtLeast(0)
            )
        }
    }

    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Asistente Veterinario IA", fontWeight = FontWeight.Bold)
                        if (pet != null) {
                            Text(
                                "Consultando sobre ${pet!!.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(onClick = { ttsEnabled = !ttsEnabled }) {
                        Icon(
                            imageVector = if (ttsEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = "Voz",
                            tint = if (ttsEnabled) Teal else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(onClick = { showClear = true }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Limpiar chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            InputRow(
                text          = inputText,
                onTextChange  = { inputText = it },
                isTyping      = isTyping,
                onSend        = {
                    vm.sendMessage(inputText.trim())
                    inputText = ""
                },
                onMic         = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla tu pregunta sobre la mascota...")
                    }
                    runCatching { speechLauncher.launch(intent) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Quick replies — only when chat is empty
            if (messages.isEmpty() && !isTyping) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "¿Sobre qué quieres consultar?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    quickReplies.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = {
                                        vm.sendMessage(suggestion)
                                        coroutine.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    },
                                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                HorizontalDivider()
            }

            // Message list
            LazyColumn(
                state             = listState,
                modifier          = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding    = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }

                // Streaming bubble
                if (isTyping) {
                    item {
                        if (streamingText.isNotBlank()) {
                            StreamingBubble(text = streamingText)
                        } else {
                            TypingIndicator()
                        }
                    }
                }
            }
        }
    }

    // Confirm clear dialog
    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title   = { Text("Limpiar conversación") },
            text    = { Text("Se eliminará el historial del chat con ${pet?.name ?: "esta mascota"}. ¿Continuar?") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); showClear = false }) { Text("Limpiar") }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("Cancelar") }
            }
        )
    }
}

// ── Burbujas ──────────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    val fmt    = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Teal),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Pets, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart    = if (isUser) 16.dp else 4.dp,
                    topEnd      = if (isUser) 4.dp  else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd   = 16.dp
                ),
                color = if (isUser) Teal else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text  = msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Text(
                text  = fmt.format(Date(msg.timestampMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
            )
        }

        if (isUser) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Teal),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Pets, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text  = "$text▊",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1 by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse, StartOffset(0)),    label = "d1")
    val dot2 by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse, StartOffset(200)),  label = "d2")
    val dot3 by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse, StartOffset(400)),  label = "d3")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Teal),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Pets, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(dot1, dot2, dot3).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Teal.copy(alpha = 0.3f + alpha * 0.7f))
                    )
                }
            }
        }
    }
}

// ── Input row ─────────────────────────────────────────────────────────────────

@Composable
private fun InputRow(
    text: String,
    onTextChange: (String) -> Unit,
    isTyping: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mic
            IconButton(
                onClick  = onMic,
                enabled  = !isTyping,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Voz", tint = Teal)
            }

            // Text field
            OutlinedTextField(
                value         = text,
                onValueChange = onTextChange,
                placeholder   = { Text("Escribe tu pregunta...") },
                modifier      = Modifier.weight(1f),
                shape         = RoundedCornerShape(24.dp),
                maxLines      = 4,
                enabled       = !isTyping,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Teal,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Send
            IconButton(
                onClick  = onSend,
                enabled  = text.isNotBlank() && !isTyping,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank() && !isTyping) Teal else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = if (text.isNotBlank() && !isTyping) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
