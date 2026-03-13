package com.example.app_mensagem.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mensagem.data.ProfileRepository
import com.example.app_mensagem.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val user: User) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel : ViewModel() {

    private val repository = ProfileRepository()
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val user = repository.getUserProfile()
                if (user != null) {
                    _uiState.value = ProfileUiState.Success(user)
                } else {
                    _uiState.value = ProfileUiState.Error("Utilizador não encontrado.")
                }
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Erro ao carregar perfil.")
            }
        }
    }

    fun updateProfile(name: String, status: String, imageUri: Uri?) {
        viewModelScope.launch {
            try {
                repository.updateProfile(name, status, imageUri)
                loadUserProfile() // Recarrega para mostrar os dados atualizados
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Erro ao atualizar perfil.")
            }
        }
    }
}