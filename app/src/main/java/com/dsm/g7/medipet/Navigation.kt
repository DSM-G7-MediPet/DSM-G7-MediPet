package com.dsm.g7.medipet

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.dsm.g7.medipet.auth.AuthViewModel
import com.dsm.g7.medipet.auth.LoginScreen
import com.dsm.g7.medipet.auth.SignUpScreen
import com.dsm.g7.medipet.auth.ProfileScreen
import com.dsm.g7.medipet.data.local.UserRole
import com.dsm.g7.medipet.ui.appointments.AppointmentScreen
import com.dsm.g7.medipet.ui.home.HomeScreen
import com.dsm.g7.medipet.ui.medical.MedicalRecordScreen
import com.dsm.g7.medipet.ui.onboarding.OnboardingScreen
import com.dsm.g7.medipet.ui.pets.PetDetailScreen
import com.dsm.g7.medipet.ui.pets.PetScreen
import com.dsm.g7.medipet.ui.vaccines.VaccineScreen
import com.dsm.g7.medipet.ui.vet.VetDashboardScreen
import com.dsm.g7.medipet.ui.vet.VetHomeScreen
import com.dsm.g7.medipet.ui.vet.VetPatientsScreen
import kotlinx.coroutines.flow.combine

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val VET_HOME = "vet_home"
    const val PETS = "pets"
    const val PET_DETAIL = "pet_detail"
    const val VACCINES = "vaccines"
    const val MEDICAL = "medical"
    const val APPOINTMENTS = "appointments"
    const val PROFILE = "profile"
    const val VET_DASHBOARD = "vet_dashboard"
    const val VET_PATIENTS = "vet_patients"
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val ownerBottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "Inicio", Icons.Filled.Home),
    BottomNavItem(Routes.PETS, "Mascotas", Icons.Filled.Pets),
    BottomNavItem(Routes.APPOINTMENTS, "Citas", Icons.Filled.CalendarMonth),
    BottomNavItem(Routes.PROFILE, "Perfil", Icons.Filled.AccountCircle)
)

@Composable
private fun OwnerBottomNavBar(currentRoute: String?, navController: NavController) {
    NavigationBar {
        ownerBottomNavItems.forEach { item ->
            val selected = when (item.route) {
                Routes.APPOINTMENTS -> currentRoute?.startsWith(Routes.APPOINTMENTS) == true
                else -> currentRoute == item.route
            }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

@Composable
fun MediPetNavigation(authViewModel: AuthViewModel = viewModel()) {
    val navController = rememberNavController()
    val userRole by authViewModel.userRole.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        combine(authViewModel.user, authViewModel.userRole) { user, role -> user to role }
            .collect { (user, role) ->
                val current = navController.currentDestination?.route
                when {
                    user == null -> {
                        if (current != Routes.LOGIN && current != Routes.SIGNUP) {
                            navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                        }
                    }
                    role == UserRole.UNKNOWN -> { /* loading */ }
                    role == UserRole.VET -> {
                        if (current == Routes.LOGIN || current == Routes.SIGNUP || current == null) {
                            navController.navigate(Routes.VET_HOME) { popUpTo(0) { inclusive = true } }
                        }
                    }
                    else -> {
                        if (current == Routes.LOGIN || current == Routes.SIGNUP || current == null) {
                            val prefs = context.getSharedPreferences("medipet_prefs", Context.MODE_PRIVATE)
                            val onboardingDone = prefs.getBoolean("onboarding_complete", false)
                            val dest = if (onboardingDone) Routes.HOME else Routes.ONBOARDING
                            navController.navigate(dest) { popUpTo(0) { inclusive = true } }
                        }
                    }
                }
            }
    }

    val ownerTabRoutes = setOf(Routes.HOME, Routes.PETS, Routes.PROFILE)
    val showBottomBar = userRole == UserRole.OWNER && (
        currentRoute in ownerTabRoutes ||
        currentRoute?.startsWith(Routes.APPOINTMENTS) == true
    )

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                OwnerBottomNavBar(currentRoute = currentRoute, navController = navController)
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())
        ) {

            composable(Routes.LOGIN) {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToSignUp = { navController.navigate(Routes.SIGNUP) }
                )
            }

            composable(Routes.SIGNUP) {
                SignUpScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }

            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToPets = { navController.navigate(Routes.PETS) },
                    onNavigateToAppointments = { navController.navigate(Routes.APPOINTMENTS) },
                    onNavigateToVaccines = { navController.navigate("${Routes.VACCINES}/all") }
                )
            }

            composable(Routes.VET_HOME) {
                VetHomeScreen(
                    onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                    onNavigateToDashboard = { navController.navigate(Routes.VET_DASHBOARD) },
                    onNavigateToPatients = { navController.navigate(Routes.VET_PATIENTS) },
                    onNavigateToMedical = { petId, appointmentId ->
                        navController.navigate("${Routes.MEDICAL}/$petId?appointmentId=$appointmentId")
                    }
                )
            }

            composable(Routes.VET_DASHBOARD) {
                VetDashboardScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Routes.VET_PATIENTS) {
                VetPatientsScreen(
                    onNavigateToMedical = { petId ->
                        navController.navigate("${Routes.MEDICAL}/$petId")
                    }
                )
            }

            composable(Routes.PETS) {
                PetScreen(
                    onNavigateToVaccines = { petId ->
                        navController.navigate("${Routes.VACCINES}/$petId")
                    },
                    onNavigateToMedical = { petId ->
                        navController.navigate("${Routes.MEDICAL}/$petId")
                    },
                    onNavigateToPetDetail = { petId ->
                        navController.navigate("${Routes.PET_DETAIL}/$petId")
                    }
                )
            }

            composable(
                route = "${Routes.PET_DETAIL}/{petId}",
                arguments = listOf(navArgument("petId") { type = NavType.StringType })
            ) { backStackEntry ->
                val petId = backStackEntry.arguments?.getString("petId") ?: ""
                PetDetailScreen(
                    petId = petId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToVaccines = { id -> navController.navigate("${Routes.VACCINES}/$id") },
                    onNavigateToMedical = { id -> navController.navigate("${Routes.MEDICAL}/$id") },
                    onNavigateToAppointments = { id ->
                        navController.navigate("${Routes.APPOINTMENTS}?petId=$id")
                    }
                )
            }

            composable(
                route = "${Routes.VACCINES}/{petId}",
                arguments = listOf(navArgument("petId") { type = NavType.StringType })
            ) { backStackEntry ->
                val petId = backStackEntry.arguments?.getString("petId") ?: ""
                VaccineScreen(
                    petId = petId,
                    isReadOnly = userRole == UserRole.OWNER,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Routes.MEDICAL}/{petId}?appointmentId={appointmentId}",
                arguments = listOf(
                    navArgument("petId") { type = NavType.StringType },
                    navArgument("appointmentId") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val petId = backStackEntry.arguments?.getString("petId") ?: ""
                val appointmentId = backStackEntry.arguments?.getString("appointmentId") ?: ""
                MedicalRecordScreen(
                    petId = petId,
                    appointmentFirestoreId = appointmentId,
                    isReadOnly = userRole == UserRole.OWNER,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Routes.APPOINTMENTS}?petId={petId}",
                arguments = listOf(navArgument("petId") { type = NavType.StringType; defaultValue = "" })
            ) { backStackEntry ->
                val petId = backStackEntry.arguments?.getString("petId") ?: ""
                AppointmentScreen(
                    petIdFilter = petId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
