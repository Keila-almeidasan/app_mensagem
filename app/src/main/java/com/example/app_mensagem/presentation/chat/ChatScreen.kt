package com.example.app_mensagem.presentation.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.app_mensagem.R
import com.example.app_mensagem.data.model.Message
import com.example.app_mensagem.presentation.viewmodel.ChatViewModel
import com.example.app_mensagem.ui.theme.chatGradient
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class SearchMode { TEXT, DATE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    navController: NavController, 
    conversationId: String?,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    val chatViewModel: ChatViewModel = viewModel()
    val uiState by chatViewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    var showMenu by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(SearchMode.TEXT) }
    
    val context = LocalContext.current
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    val tempPhotoUri = remember {
        val directory = File(context.cacheDir, "media")
        directory.mkdirs()
        val file = File(directory, "camera_capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "com.example.app_mensagem.fileprovider", file)
    }
    val tempVideoUri = remember {
        val directory = File(context.cacheDir, "media")
        directory.mkdirs()
        val file = File(directory, "video_capture_${System.currentTimeMillis()}.mp4")
        FileProvider.getUriForFile(context, "com.example.app_mensagem.fileprovider", file)
    }

    // --- LAUNCHERS ---

    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && conversationId != null) {
            val type = context.contentResolver.getType(uri) ?: ""
            val mediaType = if (type.startsWith("video")) "VIDEO" else "IMAGE"
            chatViewModel.sendMediaMessage(conversationId, uri, mediaType, uiState.conversation?.isGroup ?: false)
        }
    }

    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && conversationId != null) {
            chatViewModel.sendMediaMessage(conversationId, uri, "DOCUMENT", uiState.conversation?.isGroup ?: false)
        }
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && conversationId != null) {
            chatViewModel.sendMediaMessage(conversationId, tempPhotoUri, "IMAGE", uiState.conversation?.isGroup ?: false)
        }
    }

    val cameraVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success && conversationId != null) {
            chatViewModel.sendMediaMessage(conversationId, tempVideoUri, "VIDEO", uiState.conversation?.isGroup ?: false)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            if (conversationId != null) chatViewModel.sendLocation(conversationId)
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId != null) chatViewModel.loadMessages(conversationId)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // --- CABEÇALHO ---
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                Column {
                    AnimatedVisibility(visible = isSearching) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { 
                                    isSearching = false
                                    searchQuery = ""
                                    if (conversationId != null) chatViewModel.searchMessages(conversationId, "")
                                }) { Icon(Icons.Default.Close, null) }
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { 
                                        searchQuery = it
                                        if (conversationId != null) chatViewModel.searchMessages(conversationId, it, searchMode == SearchMode.DATE) 
                                    },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { 
                                        Text(if (searchMode == SearchMode.TEXT) "Pesquisar mensagem..." else "Data (dd/mm/yyyy hh:mm)...") 
                                    },
                                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                                FilterChip(
                                    selected = searchMode == SearchMode.TEXT,
                                    onClick = { searchMode = SearchMode.TEXT; if (conversationId != null) chatViewModel.searchMessages(conversationId, searchQuery, false) },
                                    label = { Text("Texto") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilterChip(
                                    selected = searchMode == SearchMode.DATE,
                                    onClick = { searchMode = SearchMode.DATE; if (conversationId != null) chatViewModel.searchMessages(conversationId, searchQuery, true) },
                                    label = { Text("Data/Hora") }
                                )
                            }
                        }
                    }

                    if (!isSearching) {
                        Row(
                            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                                AsyncImage(
                                    model = uiState.conversation?.profilePictureUrl ?: R.drawable.ic_launcher_foreground,
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.LightGray).clickable { 
                                        if (uiState.conversation?.isGroup == true) navController.navigate("group_info/${uiState.conversation?.id}")
                                        else fullscreenImageUrl = uiState.conversation?.profilePictureUrl 
                                    },
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(uiState.conversationTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(uiState.contactPresence, fontSize = 12.sp, color = if (uiState.contactPresence == "Online") Color(0xFF10B981) else Color.Gray)
                                }
                            }
                            
                            Row {
                                IconButton(onClick = onToggleTheme) {
                                    Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null)
                                }
                                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Pesquisar") },
                                        leadingIcon = { Icon(Icons.Default.Search, null) },
                                        onClick = { showMenu = false; isSearching = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (uiState.conversation?.isMuted == true) "Ativar Som" else "Silenciar") },
                                        leadingIcon = { Icon(if (uiState.conversation?.isMuted == true) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null) },
                                        onClick = { 
                                            showMenu = false
                                            if (conversationId != null) chatViewModel.toggleMute(conversationId)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (uiState.isLastSeenVisible) "Ocultar Visto por Último" else "Mostrar Visto por Último") },
                                        leadingIcon = { Icon(if (uiState.isLastSeenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) },
                                        onClick = { 
                                            showMenu = false
                                            chatViewModel.toggleLastSeen()
                                        }
                                    )
                                    if (uiState.conversation?.isGroup == true) {
                                        DropdownMenuItem(
                                            text = { Text("Dados do Grupo") },
                                            leadingIcon = { Icon(Icons.Default.Info, null) },
                                            onClick = { 
                                                showMenu = false
                                                navController.navigate("group_info/${uiState.conversation?.id}")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- BARRA DE MENSAGEM FIXADA ---
            if (uiState.pinnedMessage != null) {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mensagem Fixada", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = uiState.pinnedMessage?.content ?: "", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { if (conversationId != null) chatViewModel.onPinMessageClick(conversationId, uiState.pinnedMessage!!) }) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // --- MENSAGENS COM AGRUPAMENTO POR DATA ---
            LazyColumn(
                state = listState, 
                modifier = Modifier.weight(1f).fillMaxWidth(), 
                contentPadding = PaddingValues(16.dp), 
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val messages = if (isSearching) uiState.filteredMessages else uiState.messages
                
                val groupedMessages = messages.groupBy { 
                    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")).format(Date(it.timestamp))
                }

                groupedMessages.forEach { (date, msgs) ->
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(date, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(msgs) { message ->
                        val isMine = message.senderId == currentUserId
                        MessageBubbleDesign(
                            message = message, 
                            isMine = isMine,
                            isPinned = uiState.pinnedMessage?.id == message.id,
                            onImageClick = { fullscreenImageUrl = it },
                            onPinClick = { if (conversationId != null) chatViewModel.onPinMessageClick(conversationId, it) }
                        )
                    }
                }
            }

            // --- BARRA DE DIGITAÇÃO ---
            Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 8.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) }
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it; if (conversationId != null) chatViewModel.onTyping(conversationId, it.isNotBlank()) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (uiState.isRecording) "Gravando..." else "Mensagem...") },
                        shape = RoundedCornerShape(24.dp),
                        enabled = !uiState.isRecording
                    )
                    if (messageText.isNotBlank()) {
                        IconButton(onClick = { if (conversationId != null) { chatViewModel.sendMessage(conversationId, messageText); messageText = "" } }) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = { 
                            if (uiState.isRecording) chatViewModel.stopRecording(conversationId!!) else chatViewModel.startRecording() 
                        }) {
                            Icon(if (uiState.isRecording) Icons.Default.StopCircle else Icons.Default.Mic, null, tint = if (uiState.isRecording) Color.Red else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // --- MENU DE ANEXOS ---
            if (showAttachmentMenu) {
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 16.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        AttachmentOption(Icons.Default.PhotoLibrary, "Galeria", Color(0xFF9333EA)) { 
                            showAttachmentMenu = false
                            visualMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                        AttachmentOption(Icons.Default.PhotoCamera, "Foto", Color(0xFFEC4899)) { 
                            showAttachmentMenu = false
                            cameraPhotoLauncher.launch(tempPhotoUri)
                        }
                        AttachmentOption(Icons.Default.VideoCameraBack, "Vídeo", Color(0xFFF59E0B)) { 
                            showAttachmentMenu = false
                            cameraVideoLauncher.launch(tempVideoUri)
                        }
                        AttachmentOption(Icons.Default.InsertDriveFile, "Doc", Color(0xFF3B82F6)) { 
                            showAttachmentMenu = false
                            docLauncher.launch("*/*") 
                        }
                        AttachmentOption(Icons.Default.LocationOn, "Local", Color(0xFF10B981)) { 
                            showAttachmentMenu = false
                            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    }
                }
            }
        }

        if (fullscreenImageUrl != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { fullscreenImageUrl = null }, contentAlignment = Alignment.Center) {
                AsyncImage(model = fullscreenImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}

@Composable
fun AttachmentOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = color.copy(alpha = 0.1f)) {
            Icon(icon, null, tint = color, modifier = Modifier.padding(14.dp))
        }
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleDesign(
    message: Message, 
    isMine: Boolean, 
    isPinned: Boolean,
    onImageClick: (String) -> Unit,
    onPinClick: (Message) -> Unit
) {
    val context = LocalContext.current
    var showMessageMenu by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {
                            when (message.type) {
                                "IMAGE" -> onImageClick(message.content)
                                "VIDEO", "DOCUMENT", "AUDIO", "LOCATION" -> {
                                    try {
                                        val uri = Uri.parse(message.content)
                                        val extension = MimeTypeMap.getFileExtensionFromUrl(message.content) ?: "pdf"
                                        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
                                        
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mimeType)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Abrir arquivo com..."))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro ao abrir arquivo", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onLongClick = { showMessageMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (isPinned) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            Icon(Icons.Default.PushPin, null, modifier = Modifier.size(12.dp), tint = if (isMine) Color.White.copy(0.7f) else Color.Gray)
                            Text(" Fixada", fontSize = 10.sp, color = if (isMine) Color.White.copy(0.7f) else Color.Gray)
                        }
                    }
                    
                    when (message.type) {
                        "IMAGE" -> AsyncImage(model = message.content, contentDescription = null, modifier = Modifier.clip(RoundedCornerShape(8.dp)))
                        "VIDEO" -> {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(model = message.content, contentDescription = null, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.3f)))
                                Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                        }
                        "DOCUMENT" -> Row(verticalAlignment = Alignment.CenterVertically) { 
                            Icon(Icons.Default.Description, null, tint = if (isMine) Color.White else Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(message.fileName ?: "Documento", color = if (isMine) Color.White else Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                        }
                        "AUDIO" -> Row { Icon(Icons.Default.PlayArrow, null); Text(" Áudio", color = if (isMine) Color.White else Color.Black) }
                        "LOCATION" -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Map, null, modifier = Modifier.size(80.dp), tint = if (isMine) Color.White else Color.Gray)
                                Text("📍 Ver Localização", color = if (isMine) Color.White else Color.Blue, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> Text(message.content, color = if (isMine) Color.White else MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    
                    Row(modifier = Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                            fontSize = 10.sp,
                            color = if (isMine) Color.White.copy(0.7f) else Color.Gray
                        )
                        if (isMine) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val (icon, tint) = when {
                                message.readTimestamp > 0L -> Icons.Default.DoneAll to Color(0xFF38BDF8)
                                message.deliveredTimestamp > 0L -> Icons.Default.DoneAll to Color.White.copy(0.7f)
                                else -> Icons.Default.Done to Color.White.copy(0.7f)
                            }
                            Icon(icon, null, modifier = Modifier.size(14.dp), tint = tint)
                        }
                    }
                }
            }

            DropdownMenu(expanded = showMessageMenu, onDismissRequest = { showMessageMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (isPinned) "Desafixar" else "Fixar Mensagem") },
                    leadingIcon = { Icon(Icons.Default.PushPin, null) },
                    onClick = { onPinClick(message); showMessageMenu = false }
                )
            }
        }
    }
}