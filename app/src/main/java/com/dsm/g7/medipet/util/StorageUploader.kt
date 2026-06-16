package com.dsm.g7.medipet.util

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object StorageUploader {
    private val storage = FirebaseStorage.getInstance()

    // Upload a File, return download URL
    suspend fun uploadFile(file: File, storagePath: String): String {
        return suspendCancellableCoroutine { cont ->
            val ref = storage.reference.child(storagePath)
            ref.putFile(Uri.fromFile(file))
                .continueWithTask { ref.downloadUrl }
                .addOnSuccessListener { uri -> cont.resume(uri.toString()) {} }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    // Upload a Uri (from gallery)
    suspend fun uploadUri(context: Context, uri: Uri, storagePath: String): String {
        return suspendCancellableCoroutine { cont ->
            val ref = storage.reference.child(storagePath)
            ref.putFile(uri)
                .continueWithTask { ref.downloadUrl }
                .addOnSuccessListener { downloadUri -> cont.resume(downloadUri.toString()) {} }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }
}
