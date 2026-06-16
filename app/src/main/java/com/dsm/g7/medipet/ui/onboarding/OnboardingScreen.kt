package com.dsm.g7.medipet.ui.onboarding

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val TealPrimary = Color(0xFF0D6E6E)
private val TealLight   = Color(0xFF14A9A9)

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val gradient: List<Color>
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Filled.Pets,
        title = "Bienvenido a MediPet",
        subtitle = "Gestiona la salud de tus mascotas en un solo lugar. Vacunas, citas y más — todo organizado.",
        gradient = listOf(Color(0xFF0D6E6E), Color(0xFF14A9A9))
    ),
    OnboardingPage(
        icon = Icons.Filled.Vaccines,
        title = "Tu mascota, protegida",
        subtitle = "Registra el historial de vacunas y recibe recordatorios para que no se pierda ninguna dosis.",
        gradient = listOf(Color(0xFF0D6E6E), Color(0xFF1B7B7B))
    ),
    OnboardingPage(
        icon = Icons.Filled.CalendarMonth,
        title = "Agenda tu primera cita",
        subtitle = "Solicita citas con el veterinario y lleva el historial médico completo de cada mascota.",
        gradient = listOf(Color(0xFF14A9A9), Color(0xFF0D6E6E))
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context   = LocalContext.current
    val pagerState = rememberPagerState { pages.size }
    val scope     = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val page = pages[index]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(page.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(Modifier.height(40.dp))
                    Text(
                        text = page.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = page.subtitle,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Dot indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { i ->
                    val width by animateDpAsState(
                        targetValue = if (pagerState.currentPage == i) 24.dp else 8.dp,
                        label = "dot_width"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .background(
                                color = if (pagerState.currentPage == i) Color.White else Color.White.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                    )
                }
            }

            // Action button
            Button(
                onClick = {
                    if (pagerState.currentPage < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        context.getSharedPreferences("medipet_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("onboarding_complete", true).apply()
                        onFinish()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = TealPrimary
                )
            ) {
                Text(
                    text = if (pagerState.currentPage < pages.lastIndex) "Siguiente" else "¡Empezar!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // Skip (not on last page)
            if (pagerState.currentPage < pages.lastIndex) {
                TextButton(onClick = {
                    context.getSharedPreferences("medipet_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("onboarding_complete", true).apply()
                    onFinish()
                }) {
                    Text("Saltar", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}
