package com.dsm.g7.medipet.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsm.g7.medipet.R
import com.dsm.g7.medipet.data.local.UserRole
import com.dsm.g7.medipet.util.StorageUploader
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userRole = MutableStateFlow(UserRole.UNKNOWN)
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    private val _profilePhotoUrl = MutableStateFlow("")
    val profilePhotoUrl: StateFlow<String> = _profilePhotoUrl.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _specialty = MutableStateFlow("")
    val specialty: StateFlow<String> = _specialty.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            _user.value = currentUser
            if (currentUser != null) {
                detectRole(currentUser.uid, currentUser.email ?: "")
            } else {
                _userRole.value = UserRole.UNKNOWN
            }
        }
    }

    fun detectRole(uid: String, email: String) {
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val roleStr = doc.getString("role") ?: ""
                    _userRole.value = when (roleStr) {
                        "VET" -> UserRole.VET
                        "OWNER" -> UserRole.OWNER
                        else -> determineRoleFromEmail(email)
                    }
                    _profilePhotoUrl.value = doc.getString("photoUrl")
                        ?: auth.currentUser?.photoUrl?.toString() ?: ""
                    _phoneNumber.value = doc.getString("phone") ?: ""
                    _specialty.value = doc.getString("specialty") ?: ""
                } else {
                    val role = determineRoleFromEmail(email)
                    _userRole.value = role
                    saveRoleToFirestore(uid, email, role)
                    _profilePhotoUrl.value = auth.currentUser?.photoUrl?.toString() ?: ""
                }
            }
            .addOnFailureListener {
                _userRole.value = determineRoleFromEmail(email)
                _profilePhotoUrl.value = auth.currentUser?.photoUrl?.toString() ?: ""
            }
    }

    fun updateProfile(name: String, phone: String, specialty: String) {
        val currentUser = auth.currentUser ?: return
        val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
            displayName = name
        }
        currentUser.updateProfile(profileUpdates)
        firestore.collection("users").document(currentUser.uid)
            .set(mapOf("phone" to phone, "specialty" to specialty), SetOptions.merge())
        _phoneNumber.value = phone
        _specialty.value = specialty
    }

    fun updateProfilePhotoUrl(url: String) {
        val currentUser = auth.currentUser ?: return
        val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
            photoUri = android.net.Uri.parse(url)
        }
        currentUser.updateProfile(profileUpdates)
        firestore.collection("users").document(currentUser.uid)
            .set(mapOf("photoUrl" to url), SetOptions.merge())
        _profilePhotoUrl.value = url
    }

    fun uploadProfilePhoto(context: Context, photoFile: File? = null, photoUri: android.net.Uri? = null) {
        val uid = auth.currentUser?.uid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            val result = runCatching {
                val storagePath = "users/$uid/profile.jpg"
                val url = when {
                    photoFile != null -> StorageUploader.uploadFile(photoFile, storagePath)
                    photoUri != null -> StorageUploader.uploadUri(context, photoUri, storagePath)
                    else -> return@runCatching
                }
                updateProfilePhotoUrl(url)
            }
            _isLoading.value = false
            if (result.isFailure) {
                _errorMessage.value = "No se pudo subir la foto. Verifica que Firebase Storage esté habilitado."
            }
        }
    }

    private fun determineRoleFromEmail(email: String): UserRole {
        return if (email.endsWith("@unmsm.edu.pe")) UserRole.VET else UserRole.OWNER
    }

    private fun saveRoleToFirestore(uid: String, email: String, role: UserRole) {
        firestore.collection("users").document(uid).set(
            mapOf("role" to role.name, "email" to email)
        )
    }

    fun saveRoleOverride(role: UserRole) {
        val uid = auth.currentUser?.uid ?: return
        val email = auth.currentUser?.email ?: ""
        _userRole.value = role
        firestore.collection("users").document(uid).update(
            mapOf("role" to role.name, "email" to email)
        )
    }

    fun saveFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).update("fcmToken", token)
            .addOnFailureListener {
                firestore.collection("users").document(uid).set(
                    mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge()
                )
            }
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
                    .setServerClientId(context.getString(R.string.default_web_client_id))
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

    fun updateDisplayName(name: String) {
        val user = auth.currentUser ?: return
        val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
            displayName = name
        }
        user.updateProfile(profileUpdates)
    }

    fun logout() {
        auth.signOut()
        _user.value = null
        _userRole.value = UserRole.UNKNOWN
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
