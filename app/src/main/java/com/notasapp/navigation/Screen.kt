package com.notasapp.navigation

/**
 * Sealed class que define todas las rutas de navegación de la app.
 *
 * Centralizar las rutas aquí evita strings duplicados en el código
 * y proporciona type safety en la navegación.
 */
sealed class Screen(val route: String) {

    // ── Autenticación ──────────────────────────────────────────
    data object Login : Screen("login")

    // ── Home ───────────────────────────────────────────────────
    data object Home : Screen("home")

    // ── Crear Materia (Wizard de 3 pasos) ──────────────────────
    data object CreateMateriaBasicInfo : Screen("create_materia/basic_info")
    data object CreateMateriaScale : Screen("create_materia/scale")
    data object CreateMateriaComponents : Screen("create_materia/components")

    // ── Detalle de Materia ──────────────────────────────────────
    data object MateriaDetail : Screen("materia/{materiaId}") {
        const val ARG_MATERIA_ID = "materiaId"
        fun createRoute(materiaId: Long) = "materia/$materiaId"
    }

    // ── Editar Porcentajes ──────────────────────────────────────
    data object EditPorcentajes : Screen("materia/{materiaId}/edit_porcentajes") {
        const val ARG_MATERIA_ID = "materiaId"
        fun createRoute(materiaId: Long) = "materia/$materiaId/edit_porcentajes"
    }

    // ── Calculadora ────────────────────────────────────────────
    data object Calculator : Screen("materia/{materiaId}/calculator") {
        const val ARG_MATERIA_ID = "materiaId"
        fun createRoute(materiaId: Long) = "materia/$materiaId/calculator"
    }

    // ── Exportar / Sincronizar ──────────────────────────────────
    data object Export : Screen("materia/{materiaId}/export") {
        const val ARG_MATERIA_ID = "materiaId"
        fun createRoute(materiaId: Long) = "materia/$materiaId/export"
    }

    // ── Estadísticas del semestre ────────────────────────────────
    data object Estadisticas : Screen("estadisticas")

    // ── Recomendaciones de estudio con IA ──────────────────────
    data object Recomendaciones : Screen("recomendaciones")

    // ── Configuración global ────────────────────────────────────
    data object Settings : Screen("settings")
}
