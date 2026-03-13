package com.example.app_mensagem.data

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

    suspend fun createUser(email: String, pass: String): AuthResult {
        val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
        val firebaseUser = authResult.user
        if (firebaseUser != null) {
            val token = FirebaseMessaging.getInstance().token.await()
            val user = User(
                uid = firebaseUser.uid,
                name = email.substringBefore('@'),
                email = email,
                fcmToken = token
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
            // Lidar com a falha ao obter o token, se necessário
        }
    }
}