package com.dsm.g7.medipet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dsm.g7.medipet.ui.pets.PetScreen
import com.dsm.g7.medipet.ui.theme.MediPetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediPetTheme {
                PetScreen()
            }
        }
    }
}