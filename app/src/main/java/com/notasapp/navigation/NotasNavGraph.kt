package com.notasapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.notasapp.ui.auth.LoginScreen
import com.notasapp.ui.export.ExportScreen
import com.notasapp.ui.home.HomeScreen
import com.notasapp.ui.materia.create.CreateMateriaWizard
import com.notasapp.ui.materia.detail.MateriaDetailScreen
import com.notasapp.ui.materia.edit.EditPorcentajesScreen
import com.notasapp.ui.stats.EstadisticasScreen

/**
 * Grafo de navegación principal de NotasApp.
 *
 * Define todas las rutas y sus pantallas destino. La ruta inicial es
 * [Screen.Login]; una vez autenticado, el usuario navega a [Screen.Home].
 */
@Composable
fun NotasNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // ── Login ────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Home ─────────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCreateMateria = {
                    navController.navigate(Screen.CreateMateriaBasicInfo.route)
                },
                onNavigateToMateria = { materiaId ->
                    navController.navigate(Screen.MateriaDetail.createRoute(materiaId))
                },
                onNavigateToEstadisticas = {
                    navController.navigate(Screen.Estadisticas.route)
                }
            )
        }

        // ── Wizard: Crear Materia ─────────────────────────────────
        composable(Screen.CreateMateriaBasicInfo.route) {
            CreateMateriaWizard(
                onWizardComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Detalle de Materia ────────────────────────────────────
        composable(
            route = Screen.MateriaDetail.route,
            arguments = listOf(
                navArgument(Screen.MateriaDetail.ARG_MATERIA_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val materiaId = backStackEntry.arguments?.getLong(
                Screen.MateriaDetail.ARG_MATERIA_ID
            ) ?: return@composable

            MateriaDetailScreen(
                materiaId = materiaId,
                onBack = { navController.popBackStack() },
                onEditPorcentajes = {
                    navController.navigate(Screen.EditPorcentajes.createRoute(materiaId))
                },
                onExport = {
                    navController.navigate(Screen.Export.createRoute(materiaId))
                }
            )
        }

        // ── Editar Porcentajes ────────────────────────────────────
        composable(
            route = Screen.EditPorcentajes.route,
            arguments = listOf(
                navArgument(Screen.EditPorcentajes.ARG_MATERIA_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val materiaId = backStackEntry.arguments?.getLong(
                Screen.EditPorcentajes.ARG_MATERIA_ID
            ) ?: return@composable

            EditPorcentajesScreen(
                materiaId = materiaId,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Exportar ──────────────────────────────────────────────
        composable(
            route = Screen.Export.route,
            arguments = listOf(
                navArgument(Screen.Export.ARG_MATERIA_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val materiaId = backStackEntry.arguments?.getLong(
                Screen.Export.ARG_MATERIA_ID
            ) ?: return@composable

            ExportScreen(
                materiaId = materiaId,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Estadísticas ──────────────────────────────────────────
        composable(Screen.Estadisticas.route) {
            EstadisticasScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
