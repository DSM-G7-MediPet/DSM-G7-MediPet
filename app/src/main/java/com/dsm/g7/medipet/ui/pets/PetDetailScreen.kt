package com.dsm.g7.medipet.ui.pets

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dsm.g7.medipet.data.local.Appointment
import com.dsm.g7.medipet.data.local.AppointmentStatus
import com.dsm.g7.medipet.data.local.MedicalRecord
import com.dsm.g7.medipet.data.local.Vaccine
import com.dsm.g7.medipet.data.local.WeightRecord
import com.dsm.g7.medipet.util.PdfExporter
import com.github.tehras.charts.bar.BarChart
import com.github.tehras.charts.bar.BarChartData
import com.github.tehras.charts.bar.renderer.bar.SimpleBarDrawer
import com.github.tehras.charts.bar.renderer.label.SimpleValueDrawer
import com.github.tehras.charts.bar.renderer.xaxis.SimpleXAxisDrawer
import com.github.tehras.charts.bar.renderer.yaxis.SimpleYAxisDrawer
import com.github.tehras.charts.line.LineChart
import com.github.tehras.charts.line.LineChartData
import com.github.tehras.charts.line.renderer.line.SolidLineDrawer
import com.github.tehras.charts.line.renderer.point.FilledCircularPointDrawer
import com.github.tehras.charts.line.renderer.xaxis.SimpleXAxisDrawer as LineXAxisDrawer
import com.github.tehras.charts.line.renderer.yaxis.SimpleYAxisDrawer as LineYAxisDrawer
import com.github.tehras.charts.piechart.PieChart
import com.github.tehras.charts.piechart.PieChartData
import com.github.tehras.charts.piechart.renderer.SimpleSliceDrawer
import java.text.SimpleDateFormat
import java.util.*

private val TealPrimary = Color(0xFF0D6E6E)
private val TealLight   = Color(0xFF14A9A9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetDetailScreen(
    petId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToVaccines: (petId: String) -> Unit = {},
    onNavigateToMedical: (petId: String) -> Unit = {},
    onNavigateToAppointments: (petId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val factory = remember(petId) {
        PetDetailViewModelFactory(context.applicationContext as Application, petId)
    }
    val vm: PetDetailViewModel = viewModel(factory = factory)

    val pet             by vm.pet.collectAsState()
    val weightRecords   by vm.weightRecords.collectAsState()
    val latestRecord    by vm.latestRecord.collectAsState()
    val nextVaccine     by vm.nextVaccine.collectAsState()
    val allRecords      by vm.allRecords.collectAsState()
    val allVaccines     by vm.allVaccines.collectAsState()
    val allAppointments by vm.allAppointments.collectAsState()

    var selectedTab     by remember { mutableIntStateOf(0) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput     by remember { mutableStateOf("") }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val visitsPerMonth = remember(allAppointments) {
        val cal = Calendar.getInstance()
        val counts = MutableList(6) { 0 }
        allAppointments.filter {
            it.status == AppointmentStatus.ATTENDED || it.status == AppointmentStatus.CONFIRMED
        }.forEach { appt ->
            val apptCal = Calendar.getInstance().also { c -> c.timeInMillis = appt.dateMillis }
            val monthsAgo = (cal.get(Calendar.YEAR) - apptCal.get(Calendar.YEAR)) * 12 +
                (cal.get(Calendar.MONTH) - apptCal.get(Calendar.MONTH))
            if (monthsAgo in 0..5) counts[5 - monthsAgo]++
        }
        counts
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pet?.name ?: "Detalle", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val currentPet = pet ?: return@IconButton
                        val file = PdfExporter.exportPdf(context, currentPet, allRecords, allVaccines)
                        PdfExporter.sharePdf(context, file)
                    }) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Exportar PDF", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TealPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Cover photo
            Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                if (pet?.photoUrl?.isNotBlank() == true) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(pet!!.photoUrl).crossfade(true).build(),
                        contentDescription = pet!!.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(TealPrimary, TealLight))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Pets, null, modifier = Modifier.size(80.dp), tint = Color.White.copy(alpha = 0.8f))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                )
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(pet?.name ?: "", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        if (pet?.species?.isNotBlank() == true) PetChip(pet!!.species)
                        if (pet?.breed?.isNotBlank() == true)   PetChip(pet!!.breed)
                    }
                }
            }

            // Tabs
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Perfil") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Estadísticas") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Historial") })
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> ProfileTab(
                        pet = pet,
                        latestRecord = latestRecord,
                        nextVaccine = nextVaccine,
                        allVaccines = allVaccines,
                        dateFormatter = dateFormatter,
                        onNavigateToVaccines = { onNavigateToVaccines(petId) },
                        onNavigateToMedical = { onNavigateToMedical(petId) },
                        onNavigateToAppointments = { onNavigateToAppointments(petId) }
                    )
                    1 -> StatsTab(
                        weightRecords = weightRecords,
                        allVaccines = allVaccines,
                        visitsPerMonth = visitsPerMonth,
                        onAddWeight = { showWeightDialog = true }
                    )
                    2 -> HealthTimelineTab(
                        vaccines = allVaccines,
                        appointments = allAppointments,
                        records = allRecords,
                        weightRecords = weightRecords,
                        dateFormatter = dateFormatter
                    )
                }
            }
        }
    }

    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text("Registrar peso") },
            text = {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Peso (kg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val kg = weightInput.toFloatOrNull()
                    if (kg != null && kg > 0f) {
                        vm.addWeightRecord(kg)
                        weightInput = ""
                        showWeightDialog = false
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ProfileTab(
    pet: com.dsm.g7.medipet.data.local.Pet?,
    latestRecord: com.dsm.g7.medipet.data.local.MedicalRecord?,
    nextVaccine: com.dsm.g7.medipet.data.local.Vaccine?,
    allVaccines: List<com.dsm.g7.medipet.data.local.Vaccine>,
    dateFormatter: SimpleDateFormat,
    onNavigateToVaccines: () -> Unit,
    onNavigateToMedical: () -> Unit,
    onNavigateToAppointments: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PetStatChip(Modifier.weight(1f), "Edad",   "${pet?.ageYears ?: 0} años", Icons.Filled.Cake)
        PetStatChip(Modifier.weight(1f), "Peso",   "${pet?.weightKg ?: 0f} kg",  Icons.Filled.MonitorWeight)
        PetStatChip(Modifier.weight(1f), "Vacunas","${allVaccines.count { it.isApplied }}/${allVaccines.size}", Icons.Filled.Vaccines)
    }

    val healthColor = when {
        nextVaccine != null && nextVaccine.dateMillis < System.currentTimeMillis() -> Color(0xFFB71C1C)
        latestRecord == null && allVaccines.isEmpty() -> Color(0xFFF57F17)
        else -> Color(0xFF2E7D32)
    }
    val healthLabel = when {
        nextVaccine != null && nextVaccine.dateMillis < System.currentTimeMillis() -> "Requiere atención — vacuna vencida"
        latestRecord == null && allVaccines.isEmpty() -> "Sin historial registrado"
        else -> "Salud al día"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = healthColor.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(healthColor))
            Text(healthLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = healthColor)
        }
    }

    InfoCard("Última consulta",
        latestRecord?.let { "${it.diagnosis}\n${dateFormatter.format(Date(it.dateMillis))}" } ?: "Sin consultas registradas")

    InfoCard("Próxima vacuna",
        nextVaccine?.let { "${it.name}\n${dateFormatter.format(Date(it.dateMillis))}" } ?: "Vacunas al día")

    Text("Acciones rápidas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onNavigateToVaccines, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Vaccines, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Vacunas")
        }
        FilledTonalButton(onClick = onNavigateToMedical, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.MedicalInformation, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Historial")
        }
        FilledTonalButton(onClick = onNavigateToAppointments, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Citas")
        }
    }
}

@Composable
private fun StatsTab(
    weightRecords: List<WeightRecord>,
    allVaccines: List<com.dsm.g7.medipet.data.local.Vaccine>,
    visitsPerMonth: List<Int>,
    onAddWeight: () -> Unit
) {
    val appliedVaccines = allVaccines.count { it.isApplied }
    val totalVaccines   = allVaccines.size

    // Vaccine completion — Tehras PieChart (donut style)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Vacunas completadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            if (totalVaccines > 0) {
                val pending = totalVaccines - appliedVaccines
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PieChart(
                        pieChartData = PieChartData(
                            slices = buildList {
                                if (appliedVaccines > 0) add(PieChartData.Slice(appliedVaccines.toFloat(), TealPrimary))
                                if (pending > 0) add(PieChartData.Slice(pending.toFloat(), Color(0xFFE0E0E0)))
                            }
                        ),
                        modifier = Modifier.size(100.dp),
                        sliceDrawer = SimpleSliceDrawer(sliceThickness = 55f)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$appliedVaccines aplicadas", style = MaterialTheme.typography.bodyMedium, color = TealPrimary, fontWeight = FontWeight.SemiBold)
                        Text("${totalVaccines - appliedVaccines} pendientes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text("Sin vacunas registradas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Weight history — Tehras LineChart
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Evolución de peso", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            if (weightRecords.size >= 2) {
                val shortFmt = remember { SimpleDateFormat("dd/MM", Locale.getDefault()) }
                LineChart(
                    linesChartData = listOf(
                        LineChartData(
                            points = weightRecords.map { r ->
                                LineChartData.Point(
                                    value = r.weightKg,
                                    label = shortFmt.format(Date(r.dateMillis))
                                )
                            },
                            lineDrawer = SolidLineDrawer(color = TealLight)
                        )
                    ),
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    pointDrawer = FilledCircularPointDrawer(color = TealPrimary),
                    xAxisDrawer = LineXAxisDrawer(),
                    yAxisDrawer = LineYAxisDrawer()
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Registra al menos 2 pesos para ver la gráfica",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAddWeight, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Registrar peso")
            }
        }
    }

    // Visits per month — Tehras BarChart
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Visitas al veterinario (últimos 6 meses)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            val monthLabels = remember {
                (5 downTo 0).map { offset ->
                    Calendar.getInstance().also { it.add(Calendar.MONTH, -offset) }
                        .let { c -> SimpleDateFormat("MMM", Locale.getDefault()).format(c.time) }
                }
            }
            if (visitsPerMonth.any { it > 0 }) {
                BarChart(
                    barChartData = BarChartData(
                        bars = visitsPerMonth.mapIndexed { i, v ->
                            BarChartData.Bar(
                                label = monthLabels[i],
                                value = v.toFloat().coerceAtLeast(0.01f),
                                color = TealPrimary
                            )
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    barDrawer = SimpleBarDrawer(),
                    xAxisDrawer = SimpleXAxisDrawer(),
                    yAxisDrawer = SimpleYAxisDrawer(),
                    labelDrawer = SimpleValueDrawer(
                        drawLocation = SimpleValueDrawer.DrawLocation.XAxis
                    )
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    Text("Sin visitas registradas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


@Composable
private fun PetChip(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.25f)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun PetStatChip(modifier: Modifier = Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = TealPrimary.copy(alpha = 0.08f))) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = TealPrimary)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TealPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(title: String, content: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = TealPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── Health Timeline Tab ───────────────────────────────────────────────────────

private sealed interface TimelineEvent {
    val dateMillis: Long
    val title: String
    val subtitle: String
    val iconTint: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
}

private data class VaccineEvent(
    override val dateMillis: Long,
    override val title: String,
    override val subtitle: String,
    override val iconTint: Color,
    override val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Vaccines
) : TimelineEvent

private data class AppointmentEvent(
    override val dateMillis: Long,
    override val title: String,
    override val subtitle: String,
    override val iconTint: Color,
    override val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.CalendarMonth
) : TimelineEvent

private data class MedicalEvent(
    override val dateMillis: Long,
    override val title: String,
    override val subtitle: String,
    override val iconTint: Color,
    override val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.MedicalInformation
) : TimelineEvent

private data class WeightEvent(
    override val dateMillis: Long,
    override val title: String,
    override val subtitle: String,
    override val iconTint: Color,
    override val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.MonitorWeight
) : TimelineEvent

@Composable
private fun HealthTimelineTab(
    vaccines: List<Vaccine>,
    appointments: List<Appointment>,
    records: List<MedicalRecord>,
    weightRecords: List<WeightRecord>,
    dateFormatter: SimpleDateFormat
) {
    val weightTint = MaterialTheme.colorScheme.outline
    val events = remember(vaccines, appointments, records, weightRecords) {
        buildList<TimelineEvent> {
            vaccines.forEach { v ->
                add(VaccineEvent(
                    dateMillis = v.dateMillis,
                    title = "Vacuna: ${v.name}",
                    subtitle = if (v.isApplied) "Aplicada" else "Pendiente",
                    iconTint = if (v.isApplied) Color(0xFF2E7D32) else Color(0xFFF57F17)
                ))
            }
            appointments.forEach { a ->
                val statusLabel = when (a.status) {
                    AppointmentStatus.PENDING   -> "Pendiente"
                    AppointmentStatus.CONFIRMED -> "Confirmada"
                    AppointmentStatus.ATTENDED  -> "Atendida"
                    AppointmentStatus.CANCELLED -> "Cancelada"
                    AppointmentStatus.EXPIRED   -> "Vencida"
                }
                val tint = when (a.status) {
                    AppointmentStatus.ATTENDED  -> Color(0xFF2E7D32)
                    AppointmentStatus.CANCELLED -> Color(0xFFB71C1C)
                    AppointmentStatus.CONFIRMED -> TealPrimary
                    else                         -> Color(0xFFF57F17)
                }
                add(AppointmentEvent(
                    dateMillis = a.dateMillis,
                    title = "Cita: ${a.vetName}",
                    subtitle = statusLabel,
                    iconTint = tint
                ))
            }
            records.forEach { r ->
                add(MedicalEvent(
                    dateMillis = r.dateMillis,
                    title = "Consulta: ${r.diagnosis}",
                    subtitle = "Dr. ${r.vetName}",
                    iconTint = TealLight
                ))
            }
            weightRecords.forEach { w ->
                add(WeightEvent(
                    dateMillis = w.dateMillis,
                    title = "Peso registrado",
                    subtitle = "${w.weightKg} kg",
                    iconTint = weightTint
                ))
            }
        }.sortedByDescending { it.dateMillis }
    }

    if (events.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text("Sin eventos registrados", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    events.forEachIndexed { index, event ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Timeline line + dot
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(event.iconTint.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(event.icon, null, tint = event.iconTint, modifier = Modifier.size(18.dp))
                }
                if (index < events.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
            // Event content
            Column(modifier = Modifier.weight(1f).padding(bottom = if (index < events.lastIndex) 4.dp else 0.dp)) {
                Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(event.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dateFormatter.format(Date(event.dateMillis)), style = MaterialTheme.typography.labelSmall, color = event.iconTint)
            }
        }
    }
}
