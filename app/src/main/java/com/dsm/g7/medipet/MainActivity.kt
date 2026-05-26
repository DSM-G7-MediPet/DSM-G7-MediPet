package com.dsm.g7.medipet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.dsm.g7.medipet.auth.AuthViewModel
import com.dsm.g7.medipet.auth.HomeScreen
import com.dsm.g7.medipet.auth.LoginScreen
import com.dsm.g7.medipet.auth.SignUpScreen
import com.dsm.g7.medipet.ui.theme.MediPetTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediPetTheme {
                val user by viewModel.user.collectAsState()
                var showSignUp by remember { mutableStateOf(false) }

                if (user != null) {
                    HomeScreen(viewModel = viewModel)
                } else {
                    if (showSignUp) {
                        SignUpScreen(
                            viewModel = viewModel,
                            onNavigateToLogin = { showSignUp = false }
                        )
                    } else {
                        LoginScreen(
                            viewModel = viewModel,
                            onNavigateToSignUp = { showSignUp = true }
                        )
                    }
                }
            }
        }
    }
}