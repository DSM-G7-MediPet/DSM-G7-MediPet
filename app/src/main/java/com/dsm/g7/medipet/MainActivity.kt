package com.dsm.g7.medipet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dsm.g7.medipet.ui.theme.MediPetTheme
import com.dsm.g7.medipet.ui.vaccines.VaccineScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediPetTheme {
                // Un contenedor de superficie que usa el color de fondo del tema
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // ¡Aquí llamamos a tu pantalla!
                    VaccineScreen()
                }
            }
        }
    }
}