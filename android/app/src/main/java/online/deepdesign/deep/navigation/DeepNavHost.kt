package online.deepdesign.deep.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.ui.auth.LoginScreen
import online.deepdesign.deep.ui.placeholder.PlaceholderScreen
import online.deepdesign.deep.ui.splash.SplashScreen

@Composable
fun DeepNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val sessionStore = DeepApp.instance.sessionStore
    val token by sessionStore.tokenFlow.collectAsState(initial = null)

    NavHost(
        navController = navController,
        startDestination = DeepRoutes.Splash,
        modifier = modifier
    ) {
        composable(DeepRoutes.Splash) {
            SplashScreen {
                scope.launch {
                    val jwt = sessionStore.tokenFlow.first()
                    val dest = if (jwt.isNullOrBlank()) DeepRoutes.Login else DeepRoutes.Chats
                    navController.navigate(dest) {
                        popUpTo(DeepRoutes.Splash) { inclusive = true }
                    }
                }
            }
        }

        composable(DeepRoutes.Login) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(DeepRoutes.Chats) {
                        popUpTo(DeepRoutes.Login) { inclusive = true }
                    }
                }
            )
        }

        composable(DeepRoutes.Chats) {
            PlaceholderScreen(
                title = "Чаты",
                subtitle = "Этап 3 — список переписок"
            )
        }

        composable(
            route = DeepRoutes.Chat,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) {
            PlaceholderScreen(
                title = "Чат",
                subtitle = "Этап 3 — переписка"
            )
        }
    }
}
