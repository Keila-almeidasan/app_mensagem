package com.example.app_mensagem

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.app_mensagem.presentation.auth.ForgotPasswordScreen
import com.example.app_mensagem.presentation.auth.HomeScreen
import com.example.app_mensagem.presentation.auth.LoginScreen
import com.example.app_mensagem.presentation.auth.SignUpScreen
import com.example.app_mensagem.presentation.chat.ChatScreen
import com.example.app_mensagem.presentation.contacts.ContactsScreen
import com.example.app_mensagem.presentation.group.CreateGroupScreen
import com.example.app_mensagem.presentation.group.GroupInfoScreen
import com.example.app_mensagem.presentation.profile.ProfileScreen
import com.example.app_mensagem.presentation.viewmodel.*
import com.example.app_mensagem.ui.theme.App_mensagemTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            App_mensagemTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel by viewModels()
                    val conversationsViewModel: ConversationsViewModel by viewModels()
                    val contactsViewModel: ContactsViewModel by viewModels()
                    val profileViewModel: ProfileViewModel by viewModels()
                    val groupInfoViewModel: GroupInfoViewModel by viewModels()

                    val authState by authViewModel.uiState.collectAsState()

                    // --- LOGICA DE SAÍDA (LOGOUT) ---
                    LaunchedEffect(authState) {
                        if (authState is AuthUiState.SignedOut) {
                            navController.navigate("login") {
                                // Limpa todo o histórico de telas para o usuário não conseguir "voltar" para as mensagens
                                popUpTo(0) { inclusive = true }
                            }
                            authViewModel.resetState()
                        }
                    }

                    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) "home" else "login"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") { LoginScreen(navController, authViewModel) }
                        composable("signup") { SignUpScreen(navController, authViewModel) }
                        composable("home") { HomeScreen(navController, authViewModel, conversationsViewModel) }
                        composable("forgot_password") { ForgotPasswordScreen(navController, authViewModel) }
                        composable("contacts") { ContactsScreen(navController, contactsViewModel) }
                        composable("profile") { ProfileScreen(navController, profileViewModel) }
                        composable(
                            route = "chat/{conversationId}",
                            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val conversationId = backStackEntry.arguments?.getString("conversationId")
                            ChatScreen(navController, conversationId)
                        }
                        composable(
                            route = "group_info/{groupId}",
                            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val groupId = backStackEntry.arguments?.getString("groupId")
                            GroupInfoScreen(navController, groupId, groupInfoViewModel)
                        }
                        composable(
                            route = "create_group/{memberIdsJson}",
                            arguments = listOf(navArgument("memberIdsJson") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val memberIdsJson = backStackEntry.arguments?.getString("memberIdsJson")
                            val type = object : TypeToken<List<String>>() {}.type
                            val memberIds: List<String> = Gson().fromJson(memberIdsJson, type) ?: emptyList()
                            CreateGroupScreen(navController, memberIds, contactsViewModel)
                        }
                    }
                }
            }
        }
    }
}