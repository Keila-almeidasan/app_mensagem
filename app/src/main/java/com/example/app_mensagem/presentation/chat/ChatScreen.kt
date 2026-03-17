package com.example.app_mensagem.presentation.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(navController: NavController, conversationId: String?) {
    val chatViewModel: ChatViewModel = viewModel()
    val uiState by chatViewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    var showMenu by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var pickerTab by remember { mutableIntStateOf(0) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    val emojis = listOf("😀", "😂", "😍", "🤣", "😊", "🙏", "👍", "❤️", "🔥", "✨", "🚀", "🎉", "😎", "🤔", "😢", "🙌", "👏", "✔️")
    val stickerUrls = listOf(
        "https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg",
        "https://www.gstatic.com/webp/gallery/1.sm.webp",
        "https://www.gstatic.com/webp/gallery/2.sm.webp"
    )

    val tempUri = remember {
        val directory = File(context.cacheDir, "images")
        directory.mkdirs()
        val file = File(directory, "camera_capture.jpg")
        FileProvider.getUriForFile(context, "com.example.app_mensagem.fileprovider", file)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && conversationId != null) chatViewModel.sendMediaMessage(conversationId, uri, "IMAGE", uiState.conversation?.isGroup ?: false)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && conversationId != null) {
            chatViewModel.sendMediaMessage(conversationId, tempUri, "IMAGE", uiState.conversation?.isGroup ?: false)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) cameraLauncher.launch(tempUri)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) chatViewModel.startRecording()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
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
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
            // --- CABEÇALHO ---
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp, color = Color.White) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color(0xFF1E293B))
                        }
                        AsyncImage(
                            model = uiState.conversation?.profilePictureUrl ?: R.drawable.ic_launcher_foreground,
                            contentDescription = "Avatar",
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFE2E8F0)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(uiState.conversationTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1E293B))
                            Text(if (uiState.isUserBlocked) "Bloqueado" else uiState.contactPresence, 
                                fontSize = 12.sp, color = if (uiState.isUserBlocked) Color.Red else Color(0xFF10B981))
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:999999999") }
                            context.startActivity(intent)
                        }) { Icon(Icons.Default.Phone, null, tint = Color(0xFF64748B)) }

                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:999999999") }
                            context.startActivity(intent)
                        }) { Icon(Icons.Default.VideoCall, null, tint = Color(0xFF64748B)) }
                        
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Mais opções", tint = Color(0xFF64748B))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (uiState.conversation?.isGroup == false) {
                                    DropdownMenuItem(
                                        text = { Text(if (uiState.isUserBlocked) "Desbloquear" else "Bloquear") },
                                        leadingIcon = { Icon(if (uiState.isUserBlocked) Icons.Default.LockOpen else Icons.Default.Block, null) },
                                        onClick = { 
                                            showMenu = false
                                            if (conversationId != null) chatViewModel.toggleBlockUser(conversationId) 
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Limpar conversa") },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                    onClick = { showMenu = false }
                                )
                            }
                        }
                    }
                }
            }

            // --- BARRA DE MENSAGEM FIXADA ---
            if (uiState.pinnedMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { },
                    color = Color.White,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PushPin, null, tint = Color(0xFF9333EA), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mensagem Fixada", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9333EA))
                            Text(
                                text = uiState.pinnedMessage?.content ?: "",
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.Gray
                            )
                        }
                        IconButton(onClick = { 
                            if (conversationId != null) chatViewModel.onPinMessageClick(conversationId, uiState.pinnedMessage!!) 
                        }) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // --- MENSAGENS ---
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.messages) { message ->
                    val isMine = message.senderId == currentUserId
                    MessageBubbleDesign(
                        message = message, 
                        isMine = isMine,
                        onImageClick = { url -> fullscreenImageUrl = url },
                        onPinClick = { 
                            if (conversationId != null) chatViewModel.onPinMessageClick(conversationId, it) 
                        }
                    )
                }
            }

            // --- SELETOR DE EMOJI/FIGURINHA ---
            if (showEmojiPicker) {
                Surface(modifier = Modifier.fillMaxWidth().height(280.dp), color = Color.White, tonalElevation = 8.dp) {
                    Column {
                        TabRow(selectedTabIndex = pickerTab) {
                            Tab(selected = pickerTab == 0, onClick = { pickerTab = 0 }) { Text("Emojis", modifier = Modifier.padding(12.dp)) }
                            Tab(selected = pickerTab == 1, onClick = { pickerTab = 1 }) { Text("Figurinhas", modifier = Modifier.padding(12.dp)) }
                        }
                        if (pickerTab == 0) {
                            LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                items(emojis) { emoji ->
                                    Box(modifier = Modifier.size(50.dp).clickable { messageText += emoji }, contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
                                }
                            }
                        } else {
                            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                items(stickerUrls) { url ->
                                    AsyncImage(
                                        model = url, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(80.dp).padding(8.dp).clickable {
                                            if (conversationId != null) { chatViewModel.sendSticker(conversationId, url); showEmojiPicker = false }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- BARRA DE DIGITAÇÃO ---
            Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 8.dp, color = Color.White) {
                if (uiState.isUserBlocked) {
                    Text("Você bloqueou este contato.", modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center, color = Color.Gray)
                } else {
                    Column {
                        if (uiState.isContactTyping) {
                            Text("Digitando...", fontSize = 12.sp, color = Color(0xFF9333EA), modifier = Modifier.padding(start = 64.dp, bottom = 4.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) { Icon(Icons.Default.Add, null, tint = Color(0xFF64748B)) }
                            IconButton(onClick = { showEmojiPicker = !showEmojiPicker }) { Icon(if (showEmojiPicker) Icons.Default.Keyboard else Icons.Default.EmojiEmotions, null, tint = Color(0xFF64748B)) }
                            
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { 
                                    messageText = it
                                    if (conversationId != null) chatViewModel.onTyping(conversationId, it.isNotBlank())
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                placeholder = { Text(if (uiState.isRecording) "Gravando áudio..." else "Mensagem...", color = Color(0xFF94A3B8)) },
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, focusedContainerColor = Color(0xFFF1F5F9), unfocusedContainerColor = Color(0xFFF1F5F9)),
                                maxLines = 4,
                                enabled = !uiState.isRecording
                            )
                            
                            if (messageText.isNotBlank()) {
                                IconButton(onClick = { if (conversationId != null) { chatViewModel.sendMessage(conversationId, messageText); messageText = "" } }, modifier = Modifier.size(44.dp).background(chatGradient, CircleShape)) {
                                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                IconButton(onClick = { 
                                    if (uiState.isRecording) {
                                        if (conversationId != null) chatViewModel.stopRecording(conversationId)
                                    } else {
                                        val res = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                        if (res == PackageManager.PERMISSION_GRANTED) chatViewModel.startRecording() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }) { 
                                    Icon(if (uiState.isRecording) Icons.Default.StopCircle else Icons.Default.Mic, null, tint = if (uiState.isRecording) Color.Red else Color(0xFF64748B)) 
                                }
                            }
                        }
                    }
                }
            }
            
            if (showAttachmentMenu) {
                Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, tonalElevation = 16.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        AttachmentOption(Icons.Default.PhotoLibrary, "Galeria", Color(0xFF9333EA)) { 
                            showAttachmentMenu = false
                            galleryLauncher.launch("image/*") 
                        }
                        AttachmentOption(Icons.Default.PhotoCamera, "Câmera", Color(0xFFEC4899)) { 
                            showAttachmentMenu = false
                            val res = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            if (res == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(tempUri) else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                IconButton(onClick = { fullscreenImageUrl = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).statusBarsPadding()) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
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
        Text(label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleDesign(
    message: Message, 
    isMine: Boolean, 
    onImageClick: (String) -> Unit,
    onPinClick: (Message) -> Unit
) {
    val context = LocalContext.current
    val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    var showMessageMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isMine) 16.dp else 2.dp, bottomEnd = if (isMine) 2.dp else 16.dp),
                color = if (isMine) Color(0xFF9333EA) else Color.White,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = { if (message.type == "IMAGE") onImageClick(message.content) },
                        onLongClick = { showMessageMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    when (message.type) {
                        "IMAGE", "STICKER" -> {
                            AsyncImage(model = message.content, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        }
                        "VIDEO" -> {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(model = message.content, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable {
                                    val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(message.content), "video/*") }
                                    context.startActivity(intent)
                                })
                            }
                        }
                        "AUDIO" -> {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                                val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(message.content), "audio/*") }
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.PlayArrow, null, tint = if (isMine) Color.White else Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mensagem de voz", color = if (isMine) Color.White else Color.Black)
                            }
                        }
                        "LOCATION" -> {
                            Text("📍 Minha Localização", color = if (isMine) Color.White else Color.Blue, modifier = Modifier.clickable {
                                val gmmIntentUri = Uri.parse(message.content)
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                context.startActivity(mapIntent)
                            })
                        }
                        else -> {
                            Text(message.content, color = if (isMine) Color.White else Color(0xFF1E293B), fontSize = 15.sp)
                        }
                    }
                    Text(timeString, fontSize = 10.sp, color = if (isMine) Color.White.copy(0.7f) else Color(0xFF94A3B8), modifier = Modifier.align(Alignment.End))
                }
            }

            DropdownMenu(
                expanded = showMessageMenu,
                onDismissRequest = { showMessageMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Fixar Mensagem") },
                    leadingIcon = { Icon(Icons.Default.PushPin, null) },
                    onClick = {
                        onPinClick(message)
                        showMessageMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reagir") },
                    leadingIcon = { Icon(Icons.Default.AddReaction, null) },
                    onClick = { showMessageMenu = false }
                )
            }
        }
    }
}
