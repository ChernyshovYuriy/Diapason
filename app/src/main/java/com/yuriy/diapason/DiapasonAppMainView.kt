package com.yuriy.diapason

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuriy.diapason.comparison.WarmUpComparisonViewModel
import com.yuriy.diapason.ui.navigation.Screen
import com.yuriy.diapason.ui.navigation.bottomNavItems
import com.yuriy.diapason.ui.screens.about.AboutScreen
import com.yuriy.diapason.ui.screens.analyze.AnalyzeScreen
import com.yuriy.diapason.ui.screens.analyze.AnalyzeViewModel
import com.yuriy.diapason.ui.screens.comparison.WarmUpFlowScreen
import com.yuriy.diapason.ui.screens.guide.GuideScreen
import com.yuriy.diapason.ui.screens.history.HistoryScreen
import com.yuriy.diapason.ui.screens.history.HistoryViewModel
import com.yuriy.diapason.ui.screens.results.ResultsScreen
import com.yuriy.diapason.ui.screens.voicetypes.VoiceTypesScreen

@Composable
fun DiapasonAppMainView() {

    val navController = rememberNavController()
    // Activity-scoped so ResultsScreen can pull lastResult from the same instance
    val analyzeViewModel: AnalyzeViewModel = viewModel()
    // Determine if the bottom bar should be visible
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route !in setOf(
        Screen.Results.route,
        Screen.WarmUpComparison.route,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == item.screen.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    // Pop up to start so back stack doesn't grow unboundedly
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) {
                                        item.selectedIcon
                                    } else {
                                        item.unselectedIcon
                                    },
                                    contentDescription = stringResource(item.label)
                                )
                            },
                            label = { Text(stringResource(item.label)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Analyze.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Analyze ──────────────────────────────────────────────────────
            composable(Screen.Analyze.route) {
                AnalyzeScreen(
                    viewModel = analyzeViewModel,
                    onNavigateToResults = {
                        navController.navigate(Screen.Results.route)
                    },
                    onNavigateToComparison = {
                        navController.navigate(Screen.WarmUpComparison.route)
                    }
                )
            }

            // ── Guide ────────────────────────────────────────────────────────
            composable(Screen.Guide.route) {
                GuideScreen()
            }

            // ── Voice Types ──────────────────────────────────────────────────
            composable(Screen.VoiceTypes.route) {
                VoiceTypesScreen()
            }

            // ── History ──────────────────────────────────────────────────────
            composable(Screen.History.route) {
                val historyViewModel: HistoryViewModel = viewModel()
                HistoryScreen(viewModel = historyViewModel)
            }

            // ── About ────────────────────────────────────────────────────────
            composable(Screen.About.route) {
                AboutScreen()
            }

            // ── Warm-up comparison (full-screen, no bottom bar) ──────────────
            composable(Screen.WarmUpComparison.route) {
                val comparisonViewModel: WarmUpComparisonViewModel = viewModel()
                WarmUpFlowScreen(
                    viewModel = comparisonViewModel,
                    onExit = { navController.popBackStack() }
                )
            }

            // ── Results (full-screen, no bottom bar) ─────────────────────────
            composable(Screen.Results.route) {
                val result = analyzeViewModel.lastResult

                if (result == null) {
                    // Guard: no data — silently go back rather than crash
                    navController.popBackStack()
                    return@composable
                }

                ResultsScreen(
                    profile = result.profile,
                    matches = result.matches,
                    onBack = {
                        // Reset state BEFORE popping so AnalyzeScreen's LaunchedEffect
                        // does not see ResultReady and immediately re-navigate to Results.
                        analyzeViewModel.resetToIdle()
                        navController.popBackStack()
                    },
                    onAnalyzeAgain = {
                        analyzeViewModel.resetToIdle()
                        navController.navigate(Screen.Analyze.route) {
                            popUpTo(Screen.Analyze.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
