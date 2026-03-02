package com.notasapp.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notasapp.navigation.Screen
import com.notasapp.R
import com.notasapp.ui.home.HomeScreen
import com.notasapp.ui.recomendaciones.RecomendacionesScreen
import com.notasapp.ui.calendar.CalendarScreen
import com.notasapp.ui.settings.SettingsScreen
import com.notasapp.ui.stats.EstadisticasScreen

private data class BottomNavItem(
    val route: String,
    val labelResId: Int,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, R.string.nav_home, Icons.Default.Home),
    BottomNavItem(Screen.Estadisticas.route, R.string.nav_stats, Icons.Default.BarChart),
    BottomNavItem(Screen.Recomendaciones.route, R.string.nav_resources, Icons.Default.Lightbulb),
    BottomNavItem(Screen.Calendar.route, R.string.nav_calendar, Icons.Default.CalendarMonth),
    BottomNavItem(Screen.Settings.route, R.string.nav_settings, Icons.Default.Settings),
)

/**
 * Contenedor principal de la app autenticada.
 *
 * Muestra una [NavigationBar] inferior para navegar entre las cuatro
 * secciones principales: Inicio, Estadísticas, Recursos y Ajustes.
 * El estado de cada pestaña se guarda y restaura al cambiar de tab.
 *
 * Las pantallas de detalle (materia, wizard de creación, exportar, etc.)
 * se navegan desde el NavGraph externo a través de los callbacks recibidos.
 *
 * @param onNavigateToCreateMateria  Abre el Wizard de nueva materia (NavGraph externo).
 * @param onNavigateToMateria        Abre el detalle de una materia (NavGraph externo).
 * @param onLogout                   Navega al Login cerrando la sesión.
 */
@Composable
fun MainScaffold(
    onNavigateToCreateMateria: () -> Unit,
    onNavigateToMateria: (Long) -> Unit,
    onLogout: () -> Unit,
) {
    val innerNavController = rememberNavController()
    val backStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val label = stringResource(item.labelResId)
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            innerNavController.navigate(item.route) {
                                // Al volver al inicio del back-stack se preserva el estado
                                popUpTo(innerNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Aplicar el padding del Scaffold (incluye altura de NavigationBar) y marcar
        // los insets como consumidos para que los Scaffolds internos no dupliquen el padding.
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            NavHost(
                navController = innerNavController,
                startDestination = Screen.Home.route,
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToCreateMateria = onNavigateToCreateMateria,
                        onNavigateToMateria = onNavigateToMateria,
                    )
                }

                composable(Screen.Estadisticas.route) {
                    EstadisticasScreen(
                        onNavigateBack = {
                            innerNavController.navigate(Screen.Home.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(Screen.Recomendaciones.route) {
                    RecomendacionesScreen(
                        onBack = {
                            innerNavController.navigate(Screen.Home.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(Screen.Calendar.route) {
                    CalendarScreen(
                        onBack = {
                            innerNavController.navigate(Screen.Home.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onBack = {
                            innerNavController.navigate(Screen.Home.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onLogout = onLogout,
                    )
                }
            }
        }
    }
}
