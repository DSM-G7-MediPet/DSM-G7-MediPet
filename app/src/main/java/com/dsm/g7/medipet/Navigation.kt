package com.dsm.g7.medipet

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dsm.g7.medipet.auth.AuthViewModel
import com.dsm.g7.medipet.auth.LoginScreen
import com.dsm.g7.medipet.auth.SignUpScreen
import com.dsm.g7.medipet.ui.pets.PetScreen
import com.dsm.g7.medipet.ui.vaccines.VaccineScreen
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dsm.g7.medipet.auth.ProfileScreen

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val PETS = "pets"
    const val VACCINES = "vaccines"
    const val PROFILE = "profile"
}

@Composable
fun MediPetNavigation(authViewModel: AuthViewModel = viewModel()) {
    val navController = rememberNavController()

    // Auth manejada aquí, SIN leer el estado en el cuerpo del NavHost
    LaunchedEffect(Unit) {
        authViewModel.user.collect { user ->
            val current = navController.currentDestination?.route
            if (user == null) {
                if (current != Routes.LOGIN && current != Routes.SIGNUP) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } else {
                if (current == Routes.LOGIN || current == Routes.SIGNUP || current == null) {
                    navController.navigate(Routes.PETS) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
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
        composable(Routes.PETS) {
            PetScreen(
                onNavigateToVaccines = { navController.navigate(Routes.VACCINES) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) }
            )
        }
        composable(Routes.VACCINES) {
            VaccineScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}