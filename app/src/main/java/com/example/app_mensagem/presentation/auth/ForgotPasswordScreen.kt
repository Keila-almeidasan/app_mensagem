package com.example.app_mensagem.presentation.auth

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.app_mensagem.presentation.viewmodel.AuthUiState
import com.example.app_mensagem.presentation.viewmodel.AuthViewModel
import com.example.app_mensagem.ui.theme.App_mensagemTheme

@Composable
fun ForgotPasswordScreen(navController: NavController, viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    val authState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthUiState.PasswordResetSent -> {
                Toast.makeText(
                    context,
                    "E-mail de redefinição enviado com sucesso!",
                    Toast.LENGTH_LONG
                ).show()
                navController.popBackStack()
                viewModel.resetState()
            }
            is AuthUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
            }
            else -> {}
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
                imageVector = Icons.Default.Email,
                contentDescription = "Forgot Password Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 16.dp)
            )
            Text(
                "Recuperar Senha",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Insira seu e-mail para receber um link de redefinição.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.sendPasswordResetEmail(email) },
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
                    Text("Enviar E-mail")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Voltar para o Login",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    navController.popBackStack()
                    viewModel.resetState()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ForgotPasswordScreenPreview() {
    App_mensagemTheme {
        val mockNavController = NavController(LocalContext.current)
        val mockViewModel: AuthViewModel = viewModel()
        ForgotPasswordScreen(navController = mockNavController, viewModel = mockViewModel)
    }
}