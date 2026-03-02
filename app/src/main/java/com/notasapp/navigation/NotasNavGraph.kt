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
import com.notasapp.ui.main.MainScaffold
import com.notasapp.ui.materia.create.CreateMateriaWizard
import com.notasapp.ui.materia.detail.MateriaDetailScreen
import com.notasapp.ui.materia.edit.EditPorcentajesScreen
import com.notasapp.ui.onboarding.OnboardingScreen
import com.notasapp.ui.recomendaciones.RecomendacionesScreen

/**
 * Grafo de navegación principal de NotasApp.
 *
 * Define todas las rutas y sus pantallas destino. La ruta inicial es
 * [Screen.Login]; una vez autenticado, el usuario navega a [Screen.Main],
 * que aloja la barra de navegación inferior con las cuatro secciones
 * principales (Inicio, Estadísticas, Recursos, Ajustes).
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
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Onboarding (tutorial de primer uso) ─────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Main (Bottom Nav: Home / Estadísticas / Recursos / Ajustes) ──
        composable(Screen.Main.route) {
            MainScaffold(
                onNavigateToCreateMateria = {
                    navController.navigate(Screen.CreateMateriaBasicInfo.route)
                },
                onNavigateToMateria = { materiaId ->
                    navController.navigate(Screen.MateriaDetail.createRoute(materiaId))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Wizard: Crear Materia ─────────────────────────────────
        composable(Screen.CreateMateriaBasicInfo.route) {
            CreateMateriaWizard(
                onWizardComplete = {
                    navController.popBackStack()
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
                },
                onNavigateToRecomendaciones = {
                    navController.navigate(Screen.RecomendacionesMateria.createRoute(materiaId))
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

        // ── Recomendaciones desde MateriaDetail (materia preseleccionada) ──────
        composable(
            route = Screen.RecomendacionesMateria.route,
            arguments = listOf(
                navArgument(Screen.RecomendacionesMateria.ARG_MATERIA_ID) {
                    type = NavType.LongType
                }
            )
        ) {
            RecomendacionesScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
