package com.jomiro.app.orobest_rrhh.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.orobest_rrhh.network.RrhhApiException
import com.jomiro.app.orobest_rrhh.session.RrhhSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val sessionRepository: RrhhSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(baseUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                sessionRepository.login(baseUrl, username, password)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update { it.copy(error = error.toLoginMessage()) }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }
}

private fun Throwable.toLoginMessage(): String = when (this) {
    is RrhhApiException -> when (statusCode) {
        401 -> message?.takeIf { it.isNotBlank() } ?: "Usuario o contraseña incorrectos."
        403 -> message?.takeIf { it.isNotBlank() }
            ?: "Esta cuenta no tiene acceso a RRHH móvil o no tiene una agencia activa asignada."
        else -> "No se pudo validar el acceso (HTTP $statusCode). ${message.orEmpty()}"
    }

    else -> message ?: "No se pudo conectar con la API. Verifica la URL y la red."
}
