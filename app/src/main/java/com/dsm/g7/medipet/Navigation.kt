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

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val PETS = "pets"
    const val VACCINES = "vaccines"
}

@Composable
fun MediPetNavigation(authViewModel: AuthViewModel = viewModel()) {
    val navController = rememberNavController()
    val user by authViewModel.user.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {
            LaunchedEffect(user) {
                if (user != null) {
                    navController.navigate(Routes.PETS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            }
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
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Pantalla de mascotas")
                Button(onClick = {
                    android.util.Log.d("MediPet", "Boton tocado")
                    navController.navigate(Routes.VACCINES)
                }) {
                    Text("Ir a vacunas")
                }
            }
        }
        composable(Routes.VACCINES) {
            android.util.Log.d("MediPet", "Mostrando VaccineScreen")
            VaccineScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}