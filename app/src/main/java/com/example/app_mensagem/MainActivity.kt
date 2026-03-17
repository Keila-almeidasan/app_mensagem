package com.example.app_mensagem

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.app_mensagem.presentation.auth.*
import com.example.app_mensagem.presentation.chat.ChatScreen
import com.example.app_mensagem.presentation.contacts.ContactsScreen
import com.example.app_mensagem.presentation.group.*
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
            val systemInDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemInDark) }
            val toggleTheme = { isDarkMode = !isDarkMode }

            MaterialTheme(colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel by viewModels()
                    val conversationsViewModel: ConversationsViewModel by viewModels()
                    val contactsViewModel: ContactsViewModel by viewModels()
                    val profileViewModel: ProfileViewModel by viewModels()
                    val groupInfoViewModel: GroupInfoViewModel by viewModels()

                    val authState by authViewModel.uiState.collectAsState()

                    LaunchedEffect(authState) {
                        if (authState is AuthUiState.SignedOut) {
                            navController.navigate("login") { popUpTo(0) { inclusive = true } }
                            authViewModel.resetState()
                        }
                    }

                    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) "home" else "login"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") { LoginScreen(navController, authViewModel) }
                        composable("signup") { SignUpScreen(navController, authViewModel) }
                        composable("home") { 
                            HomeScreen(navController, authViewModel, conversationsViewModel, isDarkMode, toggleTheme) 
                        }
                        composable("profile") { ProfileScreen(navController, profileViewModel) }
                        
                        // CORREÇÃO: Rota de contatos agora suporta o parâmetro opcional selectionMode
                        composable(
                            route = "contacts?selectionMode={selectionMode}",
                            arguments = listOf(navArgument("selectionMode") { 
                                type = NavType.BoolType
                                defaultValue = false 
                            })
                        ) { backStackEntry ->
                            val selectionMode = backStackEntry.arguments?.getBoolean("selectionMode") ?: false
                            ContactsScreen(navController, contactsViewModel, selectionMode)
                        }

                        composable(
                            route = "chat/{conversationId}",
                            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("conversationId")
                            ChatScreen(navController, id, isDarkMode, toggleTheme)
                        }
                        composable(
                            route = "group_info/{groupId}",
                            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            GroupInfoScreen(navController, backStackEntry.arguments?.getString("groupId"), groupInfoViewModel)
                        }
                    }
                }
            }
        }
    }
}