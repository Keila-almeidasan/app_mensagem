package com.example.app_mensagem.presentation.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.app_mensagem.R
import com.example.app_mensagem.data.model.Message
import com.example.app_mensagem.data.model.User
import com.example.app_mensagem.presentation.viewmodel.ChatViewModel
import com.example.app_mensagem.ui.theme.App_mensagemTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(navController: NavController, conversationId: String?) {
    val chatViewModel: ChatViewModel = viewModel()
    val uiState by chatViewModel.uiState.collectAsState()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showStickerSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> chatViewModel.onMediaSelected(uri, "IMAGE") }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> chatViewModel.onMediaSelected(uri, "VIDEO") }
    )
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap -> 
            // Salvar bitmap em arquivo temporário e enviar
            // Para simplificar aqui, vamos apenas indicar a necessidade de um URI
        }
    )

    LaunchedEffect(conversationId) {
        if (conversationId != null) {
            chatViewModel.loadMessages(conversationId)
        }
    }

    Scaffold(
        topBar = {
            val isGroup = uiState.conversation?.isGroup ?: false
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = { chatViewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Buscar na conversa...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.onPrimary,
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onPrimary)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = isGroup) {
                                if (conversationId != null) {
                                    navController.navigate("group_info/$conversationId")
                                }
                            }
                        ) {
                            AsyncImage(
                                model = uiState.conversation?.profilePictureUrl ?: R.drawable.ic_launcher_foreground,
                                contentDescription = "Foto de Perfil de ${uiState.conversationTitle}",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(uiState.conversationTitle)
                        }
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            chatViewModel.onSearchQueryChanged("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fechar Busca", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar Mensagem", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        IconButton(onClick = { 
                            if (conversationId != null) chatViewModel.sendLocation(conversationId) 
                        }) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Enviar Localização", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(visible = uiState.mediaToSendUri != null) {
                    Box(modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                        .height(80.dp)
                        .width(80.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(uiState.mediaToSendUri),
                            contentDescription = "Mídia selecionada",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { chatViewModel.onMediaSelected(null, "") },
                            modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), CircleShape).size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remover mídia", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Anexar Imagem")
                    }
                    IconButton(onClick = { cameraLauncher.launch(null) }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Tirar Foto")
                    }
                    IconButton(onClick = { 
                        if (uiState.isRecording) {
                            if (conversationId != null) chatViewModel.stopRecording(conversationId)
                        } else {
                            chatViewModel.startRecording()
                        }
                    }) {
                        Icon(
                            if (uiState.isRecording) Icons.Default.StopCircle else Icons.Default.Mic, 
                            contentDescription = "Gravar Áudio",
                            tint = if (uiState.isRecording) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (uiState.isRecording) "Gravando..." else "Digite uma mensagem...") },
                        enabled = uiState.mediaToSendUri == null && !uiState.isRecording
                    )
                    IconButton(onClick = {
                        if (conversationId != null) {
                            chatViewModel.sendMessage(conversationId, text)
                            text = ""
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    ) { paddingValues ->
        // ... (Resto do corpo da ChatScreen permanece igual, exibindo as bolhas)
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Reaproveitar lógica de exibição de mensagens já existente no arquivo original...
            // (Para brevidade, assuma a manutenção do LazyColumn aqui)
        }
    }
}

// ... (Resto do arquivo com MessageBubble, HighlightingText, etc.)
