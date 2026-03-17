package com.example.app_mensagem.presentation.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.app_mensagem.R
import com.example.app_mensagem.presentation.viewmodel.ProfileUiState
import com.example.app_mensagem.presentation.viewmodel.ProfileViewModel
import com.example.app_mensagem.ui.theme.Purple600
import com.example.app_mensagem.ui.theme.chatGradient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(navController: NavController, profileViewModel: ProfileViewModel = viewModel()) {
    val uiState by profileViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var lastSeenVisible by remember { mutableStateOf(true) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    
    val statusOptions = listOf("Online", "Ocupado", "Estudando", "No Trabalho", "Não Perturbe")

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> imageUri = uri }
    )

    Box(modifier = Modifier.fillMaxSize().background(chatGradient)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Meu Perfil", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when (val state = uiState) {
                    is ProfileUiState.Success -> {
                        LaunchedEffect(state.user) {
                            if (name.isEmpty()) name = state.user.name
                            if (status.isEmpty()) status = state.user.status
                            if (about.isEmpty()) about = state.user.about
                            lastSeenVisible = state.user.lastSeenVisible
                        }

                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // FOTO DE PERFIL
                            Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.size(140.dp)) {
                                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                                    AsyncImage(
                                        model = imageUri ?: state.user.profilePictureUrl ?: R.drawable.ic_launcher_foreground,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape).border(4.dp, Color.White.copy(alpha = 0.4f), CircleShape).clickable { imagePickerLauncher.launch("image/*") },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Surface(modifier = Modifier.size(40.dp).clip(CircleShape).clickable { imagePickerLauncher.launch("image/*") }, color = Color.White) {
                                    Icon(Icons.Default.CameraAlt, null, tint = Purple600, modifier = Modifier.padding(10.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // CAMPOS DE EDIÇÃO
                            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.15f)) {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = { Text("Nome", color = Color.White.copy(0.8f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White.copy(0.4f), unfocusedBorderColor = Color.White.copy(0.2f))
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // ITEM 3: CAIXA DE STATUS GERAL (ABOUT)
                                    OutlinedTextField(
                                        value = about,
                                        onValueChange = { about = it },
                                        label = { Text("Recado / Bio", color = Color.White.copy(0.8f)) },
                                        placeholder = { Text("Fale um pouco sobre você...", color = Color.White.copy(0.4f)) },
                                        leadingIcon = { Icon(Icons.Default.Info, null, tint = Color.White.copy(0.6f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3,
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White.copy(0.4f), unfocusedBorderColor = Color.White.copy(0.2f))
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text("Disponibilidade:", color = Color.White, fontSize = 14.sp)
                                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        statusOptions.forEach { option ->
                                            FilterChip(
                                                selected = status == option,
                                                onClick = { status = option },
                                                label = { Text(option) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, labelColor = Color.White, selectedLabelColor = Purple600)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // ITEM 4: PRIVACIDADE FUNCIONAL
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Visto por Último", color = Color.White)
                                            Text("Quem pode ver quando estive online", fontSize = 10.sp, color = Color.White.copy(0.6f))
                                        }
                                        Switch(
                                            checked = lastSeenVisible,
                                            onCheckedChange = { lastSeenVisible = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Green.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = {
                                    isSaving = true
                                    profileViewModel.updateProfile(name, status, about, lastSeenVisible, imageUri)
                                    Toast.makeText(context, "Alterações salvas!", Toast.LENGTH_SHORT).show()
                                    isSaving = false
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = !isSaving,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                            ) {
                                if (isSaving) CircularProgressIndicator(color = Purple600, modifier = Modifier.size(24.dp))
                                else Text("SALVAR PERFIL", color = Purple600, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
