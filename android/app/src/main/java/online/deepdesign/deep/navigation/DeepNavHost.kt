package online.deepdesign.deep.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
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
import online.deepdesign.deep.data.AuthEvents
import online.deepdesign.deep.ui.auth.LoginScreen
import online.deepdesign.deep.ui.chat.ChatScreen
import online.deepdesign.deep.ui.chats.ChatsScreen
import online.deepdesign.deep.ui.splash.SplashScreen
import java.net.URLDecoder

@Composable
fun DeepNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val sessionStore = DeepApp.instance.sessionStore

    androidx.compose.runtime.LaunchedEffect(navController) {
        AuthEvents.sessionExpired.collect {
            sessionStore.clear()
            DeepApp.instance.setAuthSession(null, null)
            navController.navigate(DeepRoutes.Login) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = DeepRoutes.Splash,
        modifier = modifier
    ) {
        composable(DeepRoutes.Splash) {
            SplashScreen {
                scope.launch {
                    val jwt = sessionStore.tokenFlow.first()
                    val userId = sessionStore.userIdFlow.first()
                    DeepApp.instance.setAuthSession(jwt, userId)
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
            ChatsScreen(
                onOpenChat = { id, title ->
                    navController.navigate(DeepRoutes.chat(id, title))
                }
            )
        }

        composable(
            route = DeepRoutes.Chat,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = "Чат"
                }
            ),
            enterTransition = {
                slideInHorizontally(tween(280)) { it } + fadeIn(tween(220))
            },
            exitTransition = {
                slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(180))
            },
            popEnterTransition = {
                slideInHorizontally(tween(280)) { -it } + fadeIn(tween(220))
            },
            popExitTransition = {
                slideOutHorizontally(tween(220)) { it } + fadeOut(tween(180))
            }
        ) { entry ->
            val conversationId = entry.arguments?.getString("conversationId").orEmpty()
            val title = URLDecoder.decode(
                entry.arguments?.getString("title") ?: "Чат",
                Charsets.UTF_8.name()
            )
            ChatScreen(
                conversationId = conversationId,
                title = title,
                onBack = { navController.popBackStack() },
                onStartCall = {
                    DeepApp.instance.callManager.startOutgoing(conversationId, title, video = false)
                },
                onStartVideoCall = {
                    DeepApp.instance.callManager.startOutgoing(conversationId, title, video = true)
                }
            )
        }
    }
}
