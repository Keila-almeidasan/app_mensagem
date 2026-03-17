package com.example.app_mensagem.presentation.viewmodel

import android.app.Application
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mensagem.MyApplication
import com.example.app_mensagem.data.ChatRepository
import com.example.app_mensagem.data.model.Conversation
import com.example.app_mensagem.data.model.Message
import com.example.app_mensagem.data.model.User
import com.example.app_mensagem.presentation.chat.ChatItem
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ChatUiState(
    val chatItems: List<ChatItem> = emptyList(),
    val messages: List<Message> = emptyList(),
    val filteredMessages: List<Message> = emptyList(),
    val searchQuery: String = "",
    val conversationTitle: String = "",
    val conversation: Conversation? = null,
    val pinnedMessage: Message? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val mediaToSendUri: Uri? = null,
    val mediaType: String? = null,
    val groupMembers: Map<String, User> = emptyMap(),
    val isRecording: Boolean = false,
    val isUserBlocked: Boolean = false,
    val isContactTyping: Boolean = false,
    val contactPresence: String = "Offline"
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        val db = (application as MyApplication).database
        repository = ChatRepository(db.conversationDao(), db.messageDao(), application)
        repository.setUserPresence()
    }

    fun onTyping(conversationId: String, isTyping: Boolean) {
        repository.setTypingStatus(conversationId, isTyping)
    }

    fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val conversation = repository.getConversationDetails(conversationId)
            if (conversation == null) {
                _uiState.value = _uiState.value.copy(error = "Conversa não encontrada.", isLoading = false)
                return@launch
            }

            viewModelScope.launch {
                repository.observeTypingStatus(conversationId).collect { typingUsers ->
                    _uiState.value = _uiState.value.copy(isContactTyping = typingUsers.isNotEmpty())
                }
            }

            if (!conversation.isGroup) {
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                val userIds = conversationId.split("-")
                if (userIds.size == 2 && currentUserId != null) {
                    val otherUserId = if (userIds[0] == currentUserId) userIds[1] else userIds[0]
                    
                    viewModelScope.launch {
                        repository.observeUserPresence(otherUserId).collect { presence ->
                            val (isOnline, lastSeen) = presence
                            val statusText = if (isOnline) "Online" 
                                            else "Visto por último às ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(lastSeen)}"
                            _uiState.value = _uiState.value.copy(
                                contactPresence = statusText,
                                isUserBlocked = repository.isUserBlocked(currentUserId, otherUserId)
                            )
                        }
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                conversationTitle = conversation.name,
                conversation = conversation
            )

            repository.getMessagesForConversation(conversationId, conversation.isGroup)
                .catch { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
                .collect { messages ->
                    _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
                }
        }
    }

    fun sendMessage(conversationId: String, text: String) {
        viewModelScope.launch {
            val isGroup = _uiState.value.conversation?.isGroup ?: false
            if (text.isNotBlank()) {
                repository.sendMessage(conversationId, text, isGroup)
                onTyping(conversationId, false)
            }
        }
    }

    fun sendMediaMessage(conversationId: String, uri: Uri, type: String, isGroup: Boolean) {
        viewModelScope.launch {
            try { repository.sendMediaMessage(conversationId, uri, type, isGroup) } 
            catch (e: Exception) { _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun sendLocation(conversationId: String) {
        viewModelScope.launch {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val locationString = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                        viewModelScope.launch {
                            repository.sendMessage(conversationId, locationString, _uiState.value.conversation?.isGroup ?: false, "LOCATION")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Falha ao obter localização")
            }
        }
    }

    fun startRecording() {
        try {
            audioFile = File(getApplication<Application>().cacheDir, "audio_record_${System.currentTimeMillis()}.m4a")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            _uiState.value = _uiState.value.copy(isRecording = true)
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Erro ao iniciar gravação", e)
        }
    }

    fun stopRecording(conversationId: String) {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _uiState.value = _uiState.value.copy(isRecording = false)

            audioFile?.let { file ->
                viewModelScope.launch {
                    repository.sendMediaMessage(conversationId, Uri.fromFile(file), "AUDIO", _uiState.value.conversation?.isGroup ?: false)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Erro ao parar gravação", e)
        }
    }

    fun sendSticker(conversationId: String, stickerId: String) {
        viewModelScope.launch {
            try { repository.sendStickerMessage(conversationId, stickerId, _uiState.value.conversation?.isGroup ?: false) }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun toggleBlockUser(conversationId: String) {
        viewModelScope.launch {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val userIds = conversationId.split("-")
            if (userIds.size == 2) {
                val otherUserId = if (userIds[0] == currentUserId) userIds[1] else userIds[0]
                repository.toggleBlockUser(otherUserId)
            }
        }
    }
}