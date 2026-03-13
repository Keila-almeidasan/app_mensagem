package com.example.app_mensagem.presentation.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.app_mensagem.R
import com.example.app_mensagem.data.model.User
import com.example.app_mensagem.presentation.viewmodel.ContactNavigationState
import com.example.app_mensagem.presentation.viewmodel.ContactsUiState
import com.example.app_mensagem.presentation.viewmodel.ContactsViewModel
import com.example.app_mensagem.ui.theme.App_mensagemTheme
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    navController: NavController,
    contactsViewModel: ContactsViewModel = viewModel(),
    selectionMode: Boolean = false
) {
    val contactsState by contactsViewModel.uiState.collectAsState()
    val navigationState by contactsViewModel.navigationState.collectAsState()
    val selectedUsers = remember { mutableStateListOf<User>() }

    LaunchedEffect(navigationState) {
        if (navigationState is ContactNavigationState.NavigateToChat) {
            val conversationId = (navigationState as ContactNavigationState.NavigateToChat).conversationId
            navController.navigate("chat/$conversationId") {
                popUpTo("home")
            }
            contactsViewModel.onNavigated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "Adicionar Membro" else if (selectedUsers.isEmpty()) "Contatos" else "${selectedUsers.size} selecionados") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedUsers.isNotEmpty() && !selectionMode) {
                FloatingActionButton(onClick = {
                    if (selectedUsers.size == 1) {
                        contactsViewModel.onUserClicked(selectedUsers.first())
                    } else {
                        val userIdsJson = Gson().toJson(selectedUsers.map { it.uid })
                        navController.navigate("create_group/$userIdsJson")
                    }
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Confirmar seleção")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = contactsState) {
                is ContactsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ContactsUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(
                            onClick = { /* Lógica de importar contatos virá aqui */ },
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Text("Importar da Agenda do Celular")
                        }
                        Divider()
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(state.users) { user ->
                                val isSelected = selectedUsers.any { it.uid == user.uid }
                                UserItem(
                                    user = user,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (selectionMode) {
                                            navController.previousBackStackEntry
                                                ?.savedStateHandle
                                                ?.set("selectedUserId", user.uid)
                                            navController.popBackStack()
                                        } else {
                                            if (isSelected) {
                                                selectedUsers.removeAll { it.uid == user.uid }
                                            } else {
                                                selectedUsers.add(user)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                is ContactsUiState.Error -> {
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
fun UserItem(
    user: User,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.profilePictureUrl ?: R.drawable.ic_launcher_foreground,
            contentDescription = "Foto de Perfil de ${user.name}",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(user.name, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(user.email, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ContactsScreenPreview() {
    App_mensagemTheme {
        ContactsScreen(navController = rememberNavController())
    }
}