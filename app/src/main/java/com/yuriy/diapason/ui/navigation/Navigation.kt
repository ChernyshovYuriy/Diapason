package com.yuriy.diapason.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.ui.graphics.vector.ImageVector
import com.yuriy.diapason.R

sealed class Screen(val route: String) {
    // Bottom nav destinations
    object Analyze : Screen("analyze")
    object Guide : Screen("guide")
    object VoiceTypes : Screen("voice_types")
    object History : Screen("history")
    object About : Screen("about")

    // Full-screen destinations (pushed on top)
    object Results : Screen("results")
    object WarmUpComparison : Screen("warm_up_comparison")
}

data class BottomNavItem(
    val screen: Screen,
    @StringRes
    val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Analyze,
        label = R.string.nav_analyze,
        selectedIcon = Icons.Filled.GraphicEq,
        unselectedIcon = Icons.Outlined.GraphicEq
    ),
    BottomNavItem(
        screen = Screen.Guide,
        label = R.string.nav_guide,
        selectedIcon = Icons.Filled.MenuBook,
        unselectedIcon = Icons.Outlined.MenuBook
    ),
    BottomNavItem(
        screen = Screen.VoiceTypes,
        label = R.string.nav_voice_types,
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic
    ),
    BottomNavItem(
        screen = Screen.History,
        label = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History
    ),
    BottomNavItem(
        screen = Screen.About,
        label = R.string.nav_about,
        selectedIcon = Icons.Filled.Info,
        unselectedIcon = Icons.Outlined.Info
    )
)
