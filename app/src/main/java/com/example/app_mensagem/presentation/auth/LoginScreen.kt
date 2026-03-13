package com.example.app_mensagem.presentation.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.app_mensagem.presentation.viewmodel.AuthUiState
import com.example.app_mensagem.presentation.viewmodel.AuthViewModel
import com.example.app_mensagem.ui.theme.App_mensagemTheme

@Composable
fun LoginScreen(navController: NavController, viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authState by viewModel.uiState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthUiState.Success) {
            navController.navigate("home") {
                popUpTo("login") { inclusive = true }
            }
            viewModel.resetState()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubble,
                contentDescription = "App Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.login(email, password) },
                enabled = authState != AuthUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (authState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("Entrar")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Não tem uma conta? Cadastre-se",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    navController.navigate("signup")
                    viewModel.resetState()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Esqueceu a senha?",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    navController.navigate("forgot_password")
                    viewModel.resetState()
                }
            )

            if (authState is AuthUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (authState as AuthUiState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    App_mensagemTheme {
        val mockNavController = NavController(LocalContext.current)
        val mockViewModel: AuthViewModel = viewModel()
        LoginScreen(navController = mockNavController, viewModel = mockViewModel)
    }
}