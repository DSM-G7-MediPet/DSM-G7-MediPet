package com.dsm.g7.medipet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dsm.g7.medipet.auth.AuthViewModel
import com.dsm.g7.medipet.ui.theme.MediPetTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediPetTheme {
                MediPetNavigation(authViewModel = authViewModel)
            }
        }
    }
}