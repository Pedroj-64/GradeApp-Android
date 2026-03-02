package com.notasapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.notasapp.data.local.UserPreferencesRepository
import com.notasapp.navigation.NotasNavGraph
import com.notasapp.navigation.Screen
import com.notasapp.ui.theme.NotasAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Actividad principal y único punto de entrada de la app.
 *
 * En onCreate lee el email persistido en DataStore para determinar
 * si el usuario ya tiene sesión activa y saltar directamente al Home,
 * o ir a Login si es la primera vez / hizo logout.
 *
 * El SplashScreen se mantiene visible mientras se resuelve el destino.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var userPrefsRepository: UserPreferencesRepository

    // Null = todavía resolviendo; non-null = destino listo → recomposición dispara NavGraph
    private var startDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Mantener el splash hasta que sepamos si hay sesión
        splashScreen.setKeepOnScreenCondition { startDestination == null }

        // Leer DataStore (I/O ligero) y decidir destino inicial
        lifecycleScope.launch {
            val email = userPrefsRepository.userEmail.first()
            startDestination = if (!email.isNullOrBlank()) {
                // Si ya vio el onboarding → Main; si no → Onboarding
                val hasOnboarding = userPrefsRepository.hasSeenOnboarding.first()
                if (hasOnboarding) Screen.Main.route else Screen.Onboarding.route
            } else {
                Screen.Login.route
            }
        }

        setContent {
            NotasAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Sólo se compone el NavGraph cuando ya conocemos el destino
                    val dest = startDestination
                    if (dest != null) {
                        NotasNavGraph(startDestination = dest)
                    }
                }
            }
        }
    }
}
