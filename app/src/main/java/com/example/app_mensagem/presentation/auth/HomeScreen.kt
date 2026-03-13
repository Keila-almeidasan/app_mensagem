package com.example.app_mensagem.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.app_mensagem.R
import com.example.app_mensagem.data.model.Conversation
import com.example.app_mensagem.presentation.common.LifecycleObserver
import com.example.app_mensagem.presentation.viewmodel.AuthViewModel
import com.example.app_mensagem.presentation.viewmodel.ConversationUiState
import com.example.app_mensagem.presentation.viewmodel.ConversationsViewModel
import com.example.app_mensagem.ui.theme.App_mensagemTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    conversationsViewModel: ConversationsViewModel = viewModel()
) {
    var showMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    val conversationState by conversationsViewModel.uiState.collectAsState()

    LifecycleObserver { event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            conversationsViewModel.resyncConversations()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        val searchQuery = (conversationState as? ConversationUiState.Success)?.searchQuery ?: ""
                        TextField(
                            value = searchQuery,
                            onValueChange = { conversationsViewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Buscar conversas...") },
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
                        Text("Conversas")
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            conversationsViewModel.onSearchQueryChanged("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fechar Busca", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu de opções", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Meu Perfil") },
                                leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                                onClick = {
                                    showMenu = false
                                    navController.navigate("profile")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sair") },
                                onClick = {
                                    showMenu = false
                                    authViewModel.logout()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate("contacts")
            }) {
                Icon(Icons.Default.Add, contentDescription = "Nova Conversa")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = conversationState) {
                is ConversationUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ConversationUiState.Success -> {
                    if (state.conversations.isEmpty()) {
                        Text(
                            text = if (state.searchQuery.isNotEmpty()) "Nenhum resultado encontrado." else "Nenhuma conversa encontrada.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.conversations.sortedByDescending { it.timestamp }) { conversation ->
                                ConversationItem(conversation = conversation) {
                                    navController.navigate("chat/${conversation.id}")
                                }
                            }
                        }
                    }
                }
                is ConversationUiState.Error -> {
                    Text(
                        text = "Erro: ${state.message}",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationItem(conversation: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = conversation.profilePictureUrl ?: R.drawable.ic_launcher_foreground,
            contentDescription = "Foto de Perfil de ${conversation.name}",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = conversation.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = formatTimestamp(conversation.timestamp),
            style = MaterialTheme.typography.bodySmall
        )
    }
    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
}

private fun formatTimestamp(timestamp: Long): String {
    val messageDate = Date(timestamp)
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(messageDate)
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    App_mensagemTheme {
        val mockNavController = NavController(LocalContext.current)
        val mockAuthViewModel: AuthViewModel = viewModel()
        HomeScreen(navController = mockNavController, authViewModel = mockAuthViewModel)
    }
}