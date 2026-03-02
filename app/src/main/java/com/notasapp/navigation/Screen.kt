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

    // ── Contenedor principal (con Bottom Navigation) ──────────
    data object Main : Screen("main")

    // ── Onboarding (tutorial de primer uso) ──────────────────
    data object Onboarding : Screen("onboarding")

    // ── Home (pestaña dentro del Main) ─────────────────────────
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
    /** Pestaña de recomendaciones dentro del Bottom Nav (sin materia preseleccionada). */
    data object Recomendaciones : Screen("recomendaciones")

    /** Ruta de recomendaciones con materia preseleccionada (desde MateriaDetail). */
    data object RecomendacionesMateria : Screen("recomendaciones/{materiaId}") {
        const val ARG_MATERIA_ID = "materiaId"
        fun createRoute(materiaId: Long) = "recomendaciones/$materiaId"
    }

    // ── Calendario académico ─────────────────────────────────────
    data object Calendar : Screen("calendar")

    // ── Configuración global ────────────────────────────────────
    data object Settings : Screen("settings")
}
