package com.notasapp.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notasapp.data.local.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para la pantalla de onboarding.
 *
 * Se encarga de marcar el onboarding como completado en DataStore
 * para que no se muestre de nuevo.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPrefsRepository: UserPreferencesRepository
) : ViewModel() {

    /** Marca el onboarding como completado. */
    fun markOnboardingComplete() {
        viewModelScope.launch {
            userPrefsRepository.setHasSeenOnboarding()
        }
    }
}
