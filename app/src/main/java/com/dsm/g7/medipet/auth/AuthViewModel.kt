package com.dsm.g7.medipet.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dsm.g7.medipet.R

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _user.value = firebaseAuth.currentUser
        }
        // Cerrar sesión al iniciar la app
        auth.signOut()
    }

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Completa todos los campos"
            return
        }
        _isLoading.value = true
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (!task.isSuccessful) {
                    _errorMessage.value = task.exception?.message
                }
            }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Completa todos los campos"
            return
        }
        _isLoading.value = true
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (!task.isSuccessful) {
                    _errorMessage.value = task.exception?.message
                }
            }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(context.getString(   R.string.default_web_client_id))
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context, request)
                val googleIdToken = GoogleIdTokenCredential
                    .createFrom(result.credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                auth.signInWithCredential(firebaseCredential)
                    .addOnCompleteListener { task ->
                        _isLoading.value = false
                        if (!task.isSuccessful) {
                            _errorMessage.value = task.exception?.message
                        }
                    }
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message
            }
        }
    }

    fun logout() {
        auth.signOut()
        _user.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}