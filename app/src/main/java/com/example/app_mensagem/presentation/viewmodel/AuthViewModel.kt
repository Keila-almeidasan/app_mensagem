package com.example.app_mensagem.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mensagem.MyApplication
import com.example.app_mensagem.data.AuthRepository
import com.example.app_mensagem.data.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val uid: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    object SignedOut : AuthUiState()
    object PasswordResetSent : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val chatRepository: ChatRepository
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        val db = (application as MyApplication).database
        chatRepository = ChatRepository(db.conversationDao(), db.messageDao(), application)
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val result = authRepository.loginUser(email, pass)
                // **** CHAMA A SINCRONIZAÇÃO ATIVA AQUI ****
                chatRepository.syncUserConversations()
                _uiState.value = AuthUiState.Success(result.user?.uid ?: "")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Ocorreu um erro desconhecido.")
            }
        }
    }

    fun signUp(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val result = authRepository.createUser(email, pass)
                _uiState.value = AuthUiState.Success(result.user?.uid ?: "")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Ocorreu um erro ao criar a conta.")
            }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                authRepository.sendPasswordResetEmail(email)
                _uiState.value = AuthUiState.PasswordResetSent
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Falha ao enviar e-mail.")
            }
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.clearLocalCache()
            firebaseAuth.signOut()
            withContext(Dispatchers.Main) {
                _uiState.value = AuthUiState.SignedOut
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}