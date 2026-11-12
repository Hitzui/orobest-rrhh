package com.jomiro.app.orobest_rrhh.session

import com.jomiro.app.orobest_rrhh.network.EmployeeDto
import com.jomiro.app.orobest_rrhh.network.FingerprintTemplateDto
import com.jomiro.app.orobest_rrhh.network.LeaveRequest
import com.jomiro.app.orobest_rrhh.network.RrhhApiClient
import com.jomiro.app.orobest_rrhh.network.RrhhApiClientFactory
import com.jomiro.app.orobest_rrhh.network.SaveFingerprintRequest
import com.jomiro.app.orobest_rrhh.network.defaultRrhhApiBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RrhhSessionState(
    val authenticated: Boolean = false,
    val apiBaseUrl: String = defaultRrhhApiBaseUrl(),
    val username: String = "",
    val canManageFingerprints: Boolean = false,
    val employees: List<EmployeeDto> = emptyList(),
)

/** Shared authenticated session and employee cache consumed by the screen-specific ViewModels. */
class RrhhSessionRepository(
    private val apiClientFactory: RrhhApiClientFactory,
) {
    private val _state = MutableStateFlow(RrhhSessionState())
    val state: StateFlow<RrhhSessionState> = _state.asStateFlow()

    private var activeApi: RrhhApiClient? = null

    suspend fun login(baseUrl: String, username: String, password: String) {
        val loginApi = apiClientFactory.create(baseUrl, "")
        val loginResult = try {
            loginApi.login(username, password)
        } finally {
            loginApi.close()
        }

        val authenticatedApi = apiClientFactory.create(baseUrl, loginResult.access_token)
        try {
            val employees = authenticatedApi.getEmployees()
            activeApi?.close()
            activeApi = authenticatedApi
            _state.update {
                it.copy(
                    authenticated = true,
                    apiBaseUrl = baseUrl,
                    username = loginResult.usuario.username,
                    canManageFingerprints = loginResult.usuario.puede_gestionar_huellas,
                    employees = employees,
                )
            }
        } catch (error: Throwable) {
            authenticatedApi.close()
            throw error
        }
    }

    suspend fun refreshEmployees(): List<EmployeeDto> {
        val api = activeApi ?: throw IllegalStateException("Inicia sesión para consultar empleados.")
        val employees = api.getEmployees()
        _state.update { it.copy(employees = employees) }
        return employees
    }

    fun apiOrNull(): RrhhApiClient? = activeApi

    suspend fun submitLeaveRequest(request: LeaveRequest): String {
        val api = activeApi ?: throw IllegalStateException("Inicia sesión para enviar la solicitud.")
        return api.submitLeaveRequest(request)
    }

    suspend fun getFingerprintTemplates(): List<FingerprintTemplateDto> =
        (activeApi ?: throw IllegalStateException("Inicia sesión para consultar huellas.")).getFingerprintTemplates()

    suspend fun getFingerprintEnrolledEmployeeIds(): List<Int> =
        (activeApi ?: throw IllegalStateException("Inicia sesión para consultar huellas.")).getFingerprintEnrolledEmployeeIds()

    suspend fun saveFingerprint(employeeId: Int, request: SaveFingerprintRequest): String =
        (activeApi ?: throw IllegalStateException("Inicia sesión para enrolar huellas.")).saveFingerprint(employeeId, request)

    suspend fun revokeFingerprint(employeeId: Int): String =
        (activeApi ?: throw IllegalStateException("Inicia sesión para revocar huellas.")).revokeFingerprint(employeeId)

    suspend fun logout() {
        val api = activeApi
        activeApi = null
        try {
            runCatching { api?.logout() }
        } finally {
            api?.close()
            _state.update {
                it.copy(
                    authenticated = false,
                    username = "",
                    canManageFingerprints = false,
                    employees = emptyList(),
                )
            }
        }
    }
}
