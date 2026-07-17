package com.dsm.g7.medipet.ui.vet

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import kotlin.math.roundToInt

private val TealPrimary = Color(0xFF0D6E6E)
private val TealLight   = Color(0xFF14A9A9)
private val StatusColors = listOf(
    Color(0xFF0D6E6E),
    Color(0xFF14A9A9),
    Color(0xFF2E7D32),
    Color(0xFFB71C1C),
    Color(0xFF757575)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetDashboardScreen(
    viewModel: VetDashboardViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val stats by viewModel.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard Veterinario", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Tarjetas Métricas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashMetricCard(Modifier.weight(1f), "Hoy",       stats.todayCount.toString(),                                       TealPrimary)
                DashMetricCard(Modifier.weight(1f), "Total",      stats.total.toString(),                                            MaterialTheme.colorScheme.outline)
                DashMetricCard(Modifier.weight(1f), "Atendidas",  stats.attended.toString(),                                         Color(0xFF2E7D32))
                DashMetricCard(Modifier.weight(1f), "Confirm.",   "${(stats.confirmationRate * 100).roundToInt()}%",                  TealLight)
            }

            // Bar chart — citas por día de semana
            ChartCard("Citas por día de semana") {
                val dayLabels = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
                if (stats.byDayOfWeek.any { it > 0 }) {
                    BarChart(
                        barChartData = BarChartData(
                            bars = stats.byDayOfWeek.mapIndexed { i, v ->
                                BarChartData.Bar(
                                    label = dayLabels[i],
                                    value = v.toFloat().coerceAtLeast(0.01f),
                                    color = TealPrimary
                                )
                            }
                        ),
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        barDrawer = SimpleBarDrawer(),
                        xAxisDrawer = SimpleXAxisDrawer(),
                        yAxisDrawer = SimpleYAxisDrawer(),
                        labelDrawer = SimpleValueDrawer(
                            drawLocation = SimpleValueDrawer.DrawLocation.XAxis
                        )
                    )
                } else {
                    EmptyPlaceholder("Sin datos de citas")
                }
            }

            // Pie chart — distribución por estado
            ChartCard("Distribución por estado") {
                val labels = listOf("Pendiente", "Confirmada", "Atendida", "Cancelada", "Vencida")
                val values = listOf(
                    stats.pending.toFloat(), stats.confirmed.toFloat(),
                    stats.attended.toFloat(), stats.cancelled.toFloat(), stats.expired.toFloat()
                )
                val nonZero = labels.zip(values).zip(StatusColors).filter { it.first.second > 0f }

                if (nonZero.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().height(180.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PieChart(
                            pieChartData = PieChartData(
                                slices = nonZero.map { (labelValue, color) ->
                                    PieChartData.Slice(value = labelValue.second, color = color)
                                }
                            ),
                            modifier = Modifier.size(150.dp),
                            sliceDrawer = SimpleSliceDrawer(sliceThickness = 50f)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            nonZero.forEach { (labelValue, color) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Canvas(Modifier.size(10.dp)) { drawCircle(color) }
                                    Text(
                                        "${labelValue.first}: ${labelValue.second.toInt()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                } else {
                    EmptyPlaceholder("Sin datos")
                }
            }

            // Line chart — tendencia semanal (últimas 8 semanas)
            ChartCard("Atenciones por semana (últimas 8 semanas)") {
                val activeWeeks = stats.weeklyTrend.mapIndexed { i, v -> i to v }.filter { it.second > 0 }
                if (activeWeeks.size >= 2) {
                    LineChart(
                        linesChartData = listOf(
                            LineChartData(
                                points = stats.weeklyTrend.mapIndexed { i, v ->
                                    LineChartData.Point(
                                        value = v.toFloat().coerceAtLeast(0f),
                                        label = "S${i + 1}"
                                    )
                                },
                                lineDrawer = SolidLineDrawer(color = TealLight)
                            )
                        ),
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        pointDrawer = FilledCircularPointDrawer(color = TealPrimary),
                        xAxisDrawer = LineXAxisDrawer(),
                        yAxisDrawer = LineYAxisDrawer()
                    )
                } else {
                    EmptyPlaceholder("Sin historial de atenciones")
                }
            }
        }
    }
}

@Composable
private fun DashMetricCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun EmptyPlaceholder(message: String) {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AnimatedDonutChart(
    segments: List<Pair<String, Float>>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val total = segments.sumOf { it.second.toDouble() }.toFloat()
    val anim by animateFloatAsState(1f, tween(900, easing = FastOutSlowInEasing), label = "donut")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            var start = -90f
            val sw  = size.minDimension * 0.18f
            val pad = sw / 2f + 4.dp.toPx()
            val arc = Size(size.width - pad * 2, size.height - pad * 2)
            segments.forEachIndexed { i, (_, v) ->
                val sweep = (v / total) * 360f * anim
                drawArc(colors[i % colors.size], start, sweep, false, Offset(pad, pad), arc, style = Stroke(sw, cap = StrokeCap.Butt))
                start += sweep
            }
        }
    }
}
