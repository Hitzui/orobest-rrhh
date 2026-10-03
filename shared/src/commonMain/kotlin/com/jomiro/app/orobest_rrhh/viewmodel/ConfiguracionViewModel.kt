package com.jomiro.app.orobest_rrhh.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.orobest_rrhh.session.RrhhSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConfiguracionUiState(
    val username: String = "",
    val apiBaseUrl: String = "",
    val loggingOut: Boolean = false,
)

class ConfiguracionViewModel(
    private val sessionRepository: RrhhSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConfiguracionUiState())
    val uiState: StateFlow<ConfiguracionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collect { session ->
                _uiState.update {
                    it.copy(username = session.username, apiBaseUrl = session.apiBaseUrl)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(loggingOut = true) }
            try {
                sessionRepository.logout()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                _uiState.update { it.copy(loggingOut = false) }
            }
        }
    }
}
