package com.dsm.g7.medipet.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsm.g7.medipet.data.local.Appointment
import com.dsm.g7.medipet.data.local.AppointmentStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToPets: () -> Unit = {},
    onNavigateToAppointments: () -> Unit = {},
    onNavigateToVaccines: () -> Unit = {},
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToChat: (petId: String) -> Unit = {},
    onNavigateToDiseases: () -> Unit = {}
) {
    val pets by viewModel.pets.collectAsState()
    val todayAppointments by viewModel.todayAppointments.collectAsState()
    val upcomingVaccines by viewModel.upcomingVaccines.collectAsState()
    val userName = viewModel.userName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inicio", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToDashboard) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Dashboard de salud")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Greeting
            item {
                Text(
                    text = "Hola, $userName! 🐾",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Summary row: 3 stat cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Mascotas",
                        value = pets.size.toString(),
                        icon = Icons.Filled.Pets
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Citas hoy",
                        value = todayAppointments.size.toString(),
                        icon = Icons.Filled.CalendarMonth
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Vacunas próx.",
                        value = upcomingVaccines.size.toString(),
                        icon = Icons.Filled.Vaccines
                    )
                }
            }

            // Quick access row
            item {
                Text(
                    "Acceso rápido",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickAccessCard(
                        modifier = Modifier.weight(1f),
                        label = "Mascotas",
                        icon = Icons.Filled.Pets,
                        onClick = onNavigateToPets
                    )
                    QuickAccessCard(
                        modifier = Modifier.weight(1f),
                        label = "Citas",
                        icon = Icons.Filled.CalendarMonth,
                        onClick = onNavigateToAppointments
                    )
                    QuickAccessCard(
                        modifier = Modifier.weight(1f),
                        label = "Vacunas",
                        icon = Icons.Filled.Vaccines,
                        onClick = onNavigateToVaccines
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Chat IA — full width card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick  = { onNavigateToChat(pets.firstOrNull()?.id ?: "") },
                    colors   = CardDefaults.cardColors(containerColor = Color(0xFF0D6E6E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Column {
                            Text("Consultar al Veterinario IA", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Gemini 2.5 Flash · disponible 24/7", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Disease catalog card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToDiseases,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.LocalHospital, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
                        Column {
                            Text("Catálogo de Enfermedades", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("Perro · Gato · Conejo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            // Today appointments section
            if (todayAppointments.isNotEmpty()) {
                item {
                    Text(
                        "Citas de hoy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(todayAppointments, key = { it.id }) { appointment ->
                    TodayAppointmentCard(appointment = appointment)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickAccessCard(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TodayAppointmentCard(appointment: Appointment) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val statusLabel = when (appointment.status) {
        AppointmentStatus.PENDING -> "Pendiente"
        AppointmentStatus.CONFIRMED -> "Confirmada"
        AppointmentStatus.ATTENDED -> "Atendida"
        AppointmentStatus.CANCELLED -> "Cancelada"
        AppointmentStatus.EXPIRED -> "Vencida"
    }
    val statusColor = when (appointment.status) {
        AppointmentStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        AppointmentStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        AppointmentStatus.ATTENDED -> MaterialTheme.colorScheme.secondary
        AppointmentStatus.CANCELLED -> MaterialTheme.colorScheme.error
        AppointmentStatus.EXPIRED -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appointment.petName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = appointment.reason,
                    style = MaterialTheme.typography.bodySmall
                )
                if (appointment.vetName.isNotBlank()) {
                    Text(
                        text = "Vet: ${appointment.vetName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatter.format(Date(appointment.dateMillis)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(statusLabel, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = statusColor.copy(alpha = 0.15f),
                        labelColor = statusColor
                    )
                )
            }
        }
    }
}
