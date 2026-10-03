package com.jomiro.app.orobest_rrhh.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.orobest_rrhh.network.EmployeeDto
import com.jomiro.app.orobest_rrhh.network.LeaveRequest
import com.jomiro.app.orobest_rrhh.network.RrhhApiException
import com.jomiro.app.orobest_rrhh.session.RrhhSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmployeesUiState(
    val employees: List<EmployeeDto> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null,
)

class EmployeesViewModel(
    private val sessionRepository: RrhhSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmployeesUiState())
    val uiState: StateFlow<EmployeesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collect { session ->
                _uiState.update { it.copy(employees = session.employees) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, error = null) }
            try {
                sessionRepository.refreshEmployees()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update { it.copy(error = error.toApiMessage()) }
            } finally {
                _uiState.update { it.copy(refreshing = false) }
            }
        }
    }

    suspend fun submitLeaveRequest(request: LeaveRequest): String =
        sessionRepository.submitLeaveRequest(request)
}

private fun Throwable.toApiMessage(): String = when (this) {
    is RrhhApiException -> "Error HTTP $statusCode. ${message.orEmpty()}"
    else -> message ?: "No se pudo completar la operación. Verifica la conexión."
}
