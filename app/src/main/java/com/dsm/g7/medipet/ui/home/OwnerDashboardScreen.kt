package com.dsm.g7.medipet.ui.home

import android.app.Application
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsm.g7.medipet.data.local.WeightRecord
import java.text.SimpleDateFormat
import java.util.*

private val Teal    = Color(0xFF0D6E6E)
private val TealLt  = Color(0xFF14A9A9)
private val GreenOk = Color(0xFF2E7D32)
private val OrangeW = Color(0xFFF57F17)
private val RedDng  = Color(0xFFB71C1C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm: OwnerDashboardViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OwnerDashboardViewModel(context.applicationContext as Application) as T
            }
        }
    )

    val pets          by vm.pets.collectAsState()
    val selectedPetId by vm.selectedPetId.collectAsState()
    val selectedPet   by vm.selectedPet.collectAsState()
    val weightRecords by vm.weightRecords.collectAsState()
    val vaccineStats  by vm.vaccineStats.collectAsState()
    val events        by vm.upcomingEvents.collectAsState()
    val health        by vm.healthStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard de Salud", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Semáforo de salud ─────────────────────────────────────────────
            HealthStoplightCard(health)

            // ── Selector de mascota ────────────────────────────────────────────
            if (pets.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    pets.forEach { pet ->
                        FilterChip(
                            selected = pet.id == selectedPetId,
                            onClick  = { vm.selectPet(pet.id) },
                            label    = { Text(pet.name) }
                        )
                    }
                }
            }

            // ── Evolución de peso (área con gradiente) ────────────────────────
            DashCard(title = "Evolución de peso — ${selectedPet?.name ?: ""}") {
                if (weightRecords.size >= 2) {
                    WeightAreaChart(records = weightRecords, modifier = Modifier.fillMaxWidth())
                    Text(
                        "— — Línea de referencia (promedio)",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrangeW,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    EmptyState("Registra al menos 2 pesajes para ver la gráfica.\nUsa el botón '+' en Detalle de mascota.")
                }
            }

            // ── Anillo de vacunas + estadísticas ──────────────────────────────
            DashCard(title = "Vacunas") {
                val (applied, total) = vaccineStats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    AnimatedVaccineRing(
                        applied  = applied,
                        total    = total,
                        modifier = Modifier.size(120.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VaccineStatRow("Aplicadas",  applied.toString(),           GreenOk)
                        VaccineStatRow("Pendientes", (total - applied).toString(), OrangeW)
                        VaccineStatRow("Total",      total.toString(),             Teal)
                        if (total > 0) {
                            val pct = (applied * 100f / total).toInt()
                            LinearProgressIndicator(
                                progress = { applied.toFloat() / total },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color    = if (pct >= 80) GreenOk else if (pct >= 50) OrangeW else RedDng,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text("$pct% completadas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Próximos 3 eventos (timeline horizontal) ──────────────────────
            DashCard(title = "Próximos eventos") {
                if (events.isEmpty()) {
                    EmptyState("Sin citas ni vacunas próximas agendadas.")
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        events.forEach { event ->
                            UpcomingEventCard(event)
                        }
                    }
                }
            }
        }
    }
}

// ── Semáforo ──────────────────────────────────────────────────────────────────

@Composable
private fun HealthStoplightCard(status: HealthStatus) {
    val (color, label, detail) = when (status) {
        HealthStatus.GREEN  -> Triple(GreenOk, "Salud al día",      "Todas las vacunas están al corriente.")
        HealthStatus.YELLOW -> Triple(OrangeW, "Atención requerida","Hay una vacuna por aplicar esta semana.")
        HealthStatus.RED    -> Triple(RedDng,  "Requiere atención", "Hay vacunas vencidas. Visita al veterinario.")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Semáforo visual
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                StoplightDot(RedDng,   lit = status == HealthStatus.RED)
                StoplightDot(OrangeW,  lit = status == HealthStatus.YELLOW)
                StoplightDot(GreenOk,  lit = status == HealthStatus.GREEN)
            }
            Column {
                Text(label,  style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                Text(detail, style = MaterialTheme.typography.bodySmall,   color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StoplightDot(color: Color, lit: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(if (lit) color else color.copy(alpha = 0.2f), CircleShape)
    )
}

// ── Area chart (peso) ─────────────────────────────────────────────────────────

@Composable
private fun WeightAreaChart(records: List<WeightRecord>, modifier: Modifier = Modifier) {
    val shortFmt = remember { SimpleDateFormat("dd/MM", Locale.getDefault()) }
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "areaAlpha"
    )

    val minW   = records.minOf { it.weightKg }
    val maxW   = records.maxOf { it.weightKg }
    val range  = (maxW - minW).coerceAtLeast(0.5f)
    val avgW   = records.map { it.weightKg }.average().toFloat()

    Box(modifier = modifier.height(200.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padL = 16.dp.toPx()
            val padR = 16.dp.toPx()
            val padT = 16.dp.toPx()
            val padB = 32.dp.toPx()
            val chartW = size.width - padL - padR
            val chartH = size.height - padT - padB
            val n = records.size

            fun xOf(i: Int)    = padL + i.toFloat() / (n - 1).coerceAtLeast(1) * chartW
            fun yOf(w: Float)  = padT + chartH - ((w - minW) / range) * chartH

            // Gradient fill
            val pts = records.mapIndexed { i, r -> Offset(xOf(i), yOf(r.weightKg)) }
            val fillPath = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, padT + chartH)
                lineTo(pts.first().x, padT + chartH)
                close()
            }
            drawPath(
                fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Teal.copy(alpha = 0.35f * alpha), Teal.copy(alpha = 0.02f)),
                    startY = padT,
                    endY   = padT + chartH
                )
            )

            // Line
            val linePath = Path().apply {
                pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            drawPath(linePath, Teal.copy(alpha = alpha),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Reference line (average)
            val refY = yOf(avgW)
            drawLine(
                color       = OrangeW.copy(alpha = 0.8f * alpha),
                start       = Offset(padL, refY),
                end         = Offset(size.width - padR, refY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
            )

            // Dots
            pts.forEach { p ->
                drawCircle(Teal.copy(alpha = alpha),  4.dp.toPx(), p)
                drawCircle(Color.White.copy(alpha = alpha), 2.dp.toPx(), p)
            }
        }

        // X-axis labels
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            records.forEach { r ->
                Text(
                    text  = shortFmt.format(Date(r.dateMillis)),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Anillo animado de vacunas ─────────────────────────────────────────────────

@Composable
private fun AnimatedVaccineRing(applied: Int, total: Int, modifier: Modifier = Modifier) {
    val fraction = if (total > 0) applied.toFloat() / total else 0f
    val sweep by animateFloatAsState(
        targetValue    = fraction * 360f,
        animationSpec  = tween(1000, easing = FastOutSlowInEasing),
        label          = "vaccineRing"
    )
    val ringColor = when {
        fraction >= 0.8f -> GreenOk
        fraction >= 0.5f -> OrangeW
        else             -> RedDng
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.14f
            val inset  = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

            drawArc(Color(0xFFE0E0E0), 0f, 360f, false,
                Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

            if (sweep > 0f) {
                drawArc(ringColor, -90f, sweep, false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$applied/$total",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = ringColor
            )
            Text("vacunas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Card de próximo evento ────────────────────────────────────────────────────

@Composable
private fun UpcomingEventCard(event: UpcomingEvent) {
    val fmt = remember { SimpleDateFormat("dd MMM\nHH:mm", Locale.getDefault()) }
    val (icon, color) = when (event.type) {
        UpcomingEventType.APPOINTMENT -> Icons.Filled.CalendarMonth to Teal
        UpcomingEventType.VACCINE     -> Icons.Filled.Vaccines      to GreenOk
    }
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.width(140.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Text(event.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 2)
            if (event.subtitle.isNotBlank()) {
                Text(event.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text(
                fmt.format(Date(event.dateMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun DashCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun VaccineStatRow(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text("$label: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun EmptyState(msg: String) {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
