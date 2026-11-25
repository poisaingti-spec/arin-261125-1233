package com.aibuilt.arin.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aibuilt.arin.ArinApplication
import com.aibuilt.arin.data.AppContainer
import com.aibuilt.arin.model.Tab

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Chat("chat", "Chat", Icons.Filled.Chat),
    Rea("rea", "REA", Icons.Filled.Build),
    Update("update", "Update Arin", Icons.Filled.SystemUpdate),
    Settings("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun ArinApp() {
    val context = LocalContext.current
    val container: AppContainer = (context.applicationContext as ArinApplication).container
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Dest.entries.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                nav.navigate(dest.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(dest.icon, dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Chat.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Chat.route) { ChatScreen(container, Tab.CHAT) }
            composable(Dest.Rea.route) { ChatScreen(container, Tab.REA) }
            composable(Dest.Update.route) { ChatScreen(container, Tab.UPDATE) }
            composable(Dest.Settings.route) { SettingsScreen(container) }
        }
    }
}
