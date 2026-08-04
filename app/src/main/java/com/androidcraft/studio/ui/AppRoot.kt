package com.androidcraft.studio.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.androidcraft.studio.ui.audio.AudioScreen
import com.androidcraft.studio.ui.chat.ChatScreen
import com.androidcraft.studio.ui.image.ImageScreen
import com.androidcraft.studio.ui.models.ModelsScreen
import com.androidcraft.studio.ui.video.VideoScreen

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    CHAT("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    IMAGE("image", "Image", Icons.Filled.Image),
    AUDIO("audio", "Voice", Icons.Filled.GraphicEq),
    VIDEO("video", "Video", Icons.Filled.Movie),
    MODELS("models", "Models", Icons.Filled.Storage),
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.CHAT.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.CHAT.route) { ChatScreen() }
            composable(Destination.IMAGE.route) { ImageScreen() }
            composable(Destination.AUDIO.route) { AudioScreen() }
            composable(Destination.VIDEO.route) { VideoScreen() }
            composable(Destination.MODELS.route) { ModelsScreen() }
        }
    }
}
