package com.example.app_mensagem.presentation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mensagem.MyApplication
import com.example.app_mensagem.data.ChatRepository
import com.example.app_mensagem.data.model.Conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed class ConversationUiState {
    object Loading : ConversationUiState()
    data class Success(
        val conversations: List<Conversation> = emptyList(),
        val searchQuery: String = ""
    ) : ConversationUiState()
    data class Error(val message: String) : ConversationUiState()
}

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository

    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Loading)
    val uiState: StateFlow<ConversationUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")

    init {
        val db = (application as MyApplication).database
        repository = ChatRepository(db.conversationDao(), db.messageDao(), application)

        repository.startConversationListener()

        observeConversations()
    }

    private fun observeConversations() {
        viewModelScope.launch {
            repository.getConversations()
                .catch { exception ->
                    _uiState.value = ConversationUiState.Error(exception.message ?: "Erro desconhecido")
                }
                .combine(_searchQuery) { conversations, query ->
                    if (query.isBlank()) {
                        conversations
                    } else {
                        conversations.filter {
                            it.name.contains(query, ignoreCase = true)
                        }
                    }
                }
                .collect { filteredList ->
                    _uiState.value = ConversationUiState.Success(
                        conversations = filteredList,
                        searchQuery = _searchQuery.value
                    )
                }
        }
    }

    // **** NOVA FUNÇÃO PÚBLICA ****
    fun resyncConversations() {
        viewModelScope.launch {
            repository.syncUserConversations()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopConversationListener()
    }
}