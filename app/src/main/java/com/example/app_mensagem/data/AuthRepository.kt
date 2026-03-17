package com.example.app_mensagem.data

import android.net.Uri
import android.util.Log
import com.example.app_mensagem.data.model.User
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    suspend fun loginUser(email: String, pass: String): AuthResult {
        val result = auth.signInWithEmailAndPassword(email, pass).await()
        updateFcmToken(result.user?.uid)
        return result
    }

    suspend fun createUser(email: String, pass: String, name: String, status: String, imageUri: Uri?): AuthResult {
        val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
        val firebaseUser = authResult.user
        
        if (firebaseUser != null) {
            var imageUrl: String? = null
            
            if (imageUri != null) {
                try {
                    Log.d("AuthRepository", "Iniciando upload para Cloudinary...")
                    imageUrl = CloudinaryHelper.uploadFile(imageUri, "IMAGE")
                    Log.d("AuthRepository", "Upload concluído! URL: $imageUrl")
                } catch (e: Exception) {
                    Log.e("AuthRepository", "FALHA NO UPLOAD: ${e.message}")
                }
            }

            val token = try {
                FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) {
                ""
            }

            val user = User(
                uid = firebaseUser.uid,
                name = name.ifBlank { email.substringBefore('@') },
                email = email,
                fcmToken = token,
                status = status,
                profilePictureUrl = imageUrl
            )
            
            database.getReference("users").child(firebaseUser.uid).setValue(user).await()
        }
        return authResult
    }

    suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    private suspend fun updateFcmToken(userId: String?) {
        if (userId == null) return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            database.getReference("users").child(userId).child("fcmToken").setValue(token)
        } catch (e: Exception) {
        }
    }
}