package com.example.app_mensagem.presentation.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.app_mensagem.R
import com.example.app_mensagem.data.model.User
import com.example.app_mensagem.presentation.viewmodel.ContactNavigationState
import com.example.app_mensagem.presentation.viewmodel.ContactsUiState
import com.example.app_mensagem.presentation.viewmodel.ContactsViewModel
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
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactsViewModel.importContacts()
        }
    }

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
                title = { Text(if (selectionMode) "Selecionar Participante" else "Contatos") },
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
                    Icon(Icons.Default.Check, contentDescription = "Confirmar")
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
                        if (!selectionMode) {
                            TextButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                        contactsViewModel.importContacts()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            ) {
                                Text("Importar da Agenda")
                            }
                            HorizontalDivider()
                        }
                        
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(state.users) { user ->
                                val isSelected = selectedUsers.any { it.uid == user.uid }
                                UserItem(
                                    user = user,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (selectionMode) {
                                            // Se for modo de seleção (adicionando participante), volta com o ID
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
                    Text(text = "Erro: ${state.message}", modifier = Modifier.align(Alignment.Center))
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
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(user.name, fontWeight = FontWeight.Bold)
            Text(user.status, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
