package com.jomiro.app.orobest_rrhh.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.orobest_rrhh.network.AttendanceDto
import com.jomiro.app.orobest_rrhh.network.AttendanceEventRequest
import com.jomiro.app.orobest_rrhh.network.AttendanceOutbox
import com.jomiro.app.orobest_rrhh.network.AttendanceSyncResult
import com.jomiro.app.orobest_rrhh.network.EmployeeDto
import com.jomiro.app.orobest_rrhh.network.LeaveRequest
import com.jomiro.app.orobest_rrhh.network.QueuedAttendanceEvent
import com.jomiro.app.orobest_rrhh.network.RrhhApiClient
import com.jomiro.app.orobest_rrhh.network.RrhhApiClientFactory
import com.jomiro.app.orobest_rrhh.network.RrhhApiException
import com.jomiro.app.orobest_rrhh.network.defaultRrhhApiBaseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class RrhhAppUiState(
    val authenticated: Boolean = false,
    val apiBaseUrl: String = defaultRrhhApiBaseUrl(),
    val signedInUsername: String = "",
    val employees: List<EmployeeDto> = emptyList(),
    val loading: Boolean = false,
    val loginError: String? = null,
    val employeesError: String? = null,
    val attendance: AttendanceDto? = null,
    val attendanceEmployeeId: Int? = null,
    val attendanceError: String? = null,
    val queuedAttendanceEvents: List<QueuedAttendanceEvent> = emptyList(),
    val attendanceSyncResult: AttendanceSyncResult? = null,
    val syncingAttendance: Boolean = false,
)

class RrhhAppViewModel(
    private val apiClientFactory: RrhhApiClientFactory,
    private val attendanceOutbox: AttendanceOutbox,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RrhhAppUiState())
    val uiState: StateFlow<RrhhAppUiState> = _uiState.asStateFlow()

    private var sessionApi: RrhhApiClient? = null

    init {
        viewModelScope.launch {
            refreshQueuedEvents()
        }
    }

    fun login(baseUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loginError = null) }
            var candidate: RrhhApiClient? = null
            try {
                val loginClient = apiClientFactory.create(baseUrl, "")
                candidate = loginClient
                val loginResult = loginClient.login(username, password)
                loginClient.close()
                candidate = null

                val authenticatedApi = apiClientFactory.create(baseUrl, loginResult.access_token)
                candidate = authenticatedApi
                val employees = authenticatedApi.getEmployees()
                sessionApi = authenticatedApi
                candidate = null
                _uiState.update {
                    it.copy(
                        authenticated = true,
                        apiBaseUrl = baseUrl,
                        signedInUsername = loginResult.usuario.username,
                        employees = employees,
                        employeesError = null,
                    )
                }
                synchronizePendingEvents()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                candidate?.close()
                _uiState.update { it.copy(loginError = error.toLoginMessage()) }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun refreshEmployees() {
        val api = sessionApi ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, employeesError = null) }
            try {
                _uiState.update { it.copy(employees = api.getEmployees()) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update { it.copy(employeesError = error.toApiMessage()) }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun consultAttendance(employeeId: Int) {
        val api = sessionApi ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    attendanceError = null,
                    attendanceEmployeeId = employeeId,
                    attendance = null,
                )
            }
            try {
                val remote = api.getAttendance(employeeId)
                setAttendanceWithLocalEvents(remote)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val employee = _uiState.value.employees.firstOrNull { it.idempleado == employeeId }
                val local = employee?.let {
                    AttendanceDto(
                        idempleado = it.idempleado,
                        codigo = it.codigo,
                        empleado = "${it.nombres} ${it.apellidos}",
                        fecha = currentManaguaDate(),
                        registrada = false,
                    )
                }
                if (local != null) {
                    setAttendanceWithLocalEvents(local)
                    _uiState.update {
                        it.copy(
                            attendanceSyncResult = AttendanceSyncResult(
                                sent = 0,
                                waiting = pendingEventsForCurrentServer().size,
                                needsReview = reviewEventsForCurrentServer().size,
                                connectionError = "Sin conexión con el servidor. Puedes guardar la marca y se sincronizará después.",
                            ),
                        )
                    }
                } else {
                    _uiState.update { it.copy(attendanceError = error.toApiMessage()) }
                }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun syncPendingAttendance() {
        viewModelScope.launch { synchronizePendingEvents() }
    }

    fun applyAttendance(employeeId: Int, record: AttendanceDto, entryTime: String?, exitTime: String?) {
        val api = sessionApi ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, attendanceError = null) }
            try {
                val server = _uiState.value.apiBaseUrl
                if (record.hora_entrada == null && entryTime != null) {
                    attendanceOutbox.enqueue(
                        server,
                        AttendanceEventRequest(
                            evento_id = kotlin.uuid.Uuid.random().toString(),
                            id = employeeId,
                            tipo = "entrada",
                            ocurrido_en = "${record.fecha}T$entryTime:00-06:00",
                        ),
                    )
                }
                if (record.hora_salida == null && exitTime != null) {
                    attendanceOutbox.enqueue(
                        server,
                        AttendanceEventRequest(
                            evento_id = kotlin.uuid.Uuid.random().toString(),
                            id = employeeId,
                            tipo = "salida",
                            ocurrido_en = "${record.fecha}T$exitTime:00-06:00",
                        ),
                    )
                }

                _uiState.update {
                    it.copy(
                        attendance = record.copy(
                            hora_entrada = record.hora_entrada ?: entryTime?.let { time -> "$time:00" },
                            hora_salida = record.hora_salida ?: exitTime?.let { time -> "$time:00" },
                            registrada = true,
                        ),
                    )
                }
                synchronizePendingEvents()
                runCatching { api.getAttendance(employeeId) }
                    .onSuccess { setAttendanceWithLocalEvents(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        attendanceError = error.toApiMessage(),
                        attendance = record.copy(
                            hora_entrada = record.hora_entrada ?: entryTime?.let { time -> "$time:00" },
                            hora_salida = record.hora_salida ?: exitTime?.let { time -> "$time:00" },
                            registrada = true,
                        ),
                    )
                }
                refreshQueuedEvents()
            } finally {
                _uiState.update { it.copy(loading = false, syncingAttendance = false) }
            }
        }
    }

    suspend fun submitLeaveRequest(request: LeaveRequest): String {
        val api = sessionApi ?: throw IllegalStateException("Inicia sesión para enviar la solicitud.")
        return api.submitLeaveRequest(request)
    }

    fun dismissAttendance() {
        if (_uiState.value.loading) return
        _uiState.update { it.copy(attendanceEmployeeId = null, attendanceError = null) }
    }

    fun logout() {
        val api = sessionApi ?: return
        viewModelScope.launch {
            runCatching { api.logout() }
            api.close()
            if (sessionApi === api) sessionApi = null
            _uiState.update {
                it.copy(
                    authenticated = false,
                    signedInUsername = "",
                    employees = emptyList(),
                    attendance = null,
                    attendanceEmployeeId = null,
                    attendanceError = null,
                    employeesError = null,
                    loginError = null,
                    loading = false,
                    syncingAttendance = false,
                )
            }
        }
    }

    override fun onCleared() {
        sessionApi?.close()
        sessionApi = null
    }

    private suspend fun synchronizePendingEvents() {
        val api = sessionApi ?: return
        val server = _uiState.value.apiBaseUrl
        _uiState.update { it.copy(syncingAttendance = true) }
        try {
            _uiState.update { it.copy(attendanceSyncResult = attendanceOutbox.synchronize(api, server)) }
            refreshQueuedEvents()
            _uiState.value.attendanceEmployeeId?.let { employeeId ->
                runCatching { api.getAttendance(employeeId) }
                    .onSuccess { setAttendanceWithLocalEvents(it) }
            }
        } finally {
            _uiState.update { it.copy(syncingAttendance = false) }
        }
    }

    private suspend fun refreshQueuedEvents() {
        _uiState.update { it.copy(queuedAttendanceEvents = attendanceOutbox.list()) }
    }

    private suspend fun setAttendanceWithLocalEvents(record: AttendanceDto) {
        refreshQueuedEvents()
        val current = _uiState.value
        val server = current.apiBaseUrl.trim().trimEnd('/')
        val localEvents = current.queuedAttendanceEvents.filter {
            it.server == server &&
                it.request.id == record.idempleado &&
                it.request.ocurrido_en.startsWith("${record.fecha}T")
        }
        val queuedEntry = localEvents.lastOrNull { it.request.tipo == "entrada" }
            ?.request?.ocurrido_en?.substringAfter('T')?.take(5)
        val queuedExit = localEvents.lastOrNull { it.request.tipo == "salida" }
            ?.request?.ocurrido_en?.substringAfter('T')?.take(5)
        _uiState.update {
            it.copy(
                attendance = record.copy(
                    hora_entrada = record.hora_entrada ?: queuedEntry?.let { time -> "$time:00" },
                    hora_salida = record.hora_salida ?: queuedExit?.let { time -> "$time:00" },
                    registrada = record.registrada || queuedEntry != null || queuedExit != null,
                ),
            )
        }
    }

    private fun pendingEventsForCurrentServer() = _uiState.value.queuedAttendanceEvents.filter {
        it.server == _uiState.value.apiBaseUrl.trim().trimEnd('/') && it.status == QueuedAttendanceEvent.STATUS_QUEUED
    }

    private fun reviewEventsForCurrentServer() = _uiState.value.queuedAttendanceEvents.filter {
        it.server == _uiState.value.apiBaseUrl.trim().trimEnd('/') && it.status == QueuedAttendanceEvent.STATUS_REVIEW
    }
}

private fun currentManaguaDate(): String =
    kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.of("America/Managua")).date.toString()

private fun Throwable.toLoginMessage(): String = when (this) {
    is RrhhApiException -> when (statusCode) {
        401 -> "Token inválido, revocado o no reconocido. Verifica el token Bearer."
        403 -> "Esta cuenta no tiene acceso a RRHH móvil o no tiene una agencia activa asignada."
        else -> "No se pudo validar el acceso (HTTP $statusCode). ${message.orEmpty()}"
    }

    else -> message ?: "No se pudo conectar con la API. Verifica la URL y la red."
}

private fun Throwable.toApiMessage(): String = when (this) {
    is RrhhApiException -> "Error HTTP $statusCode. ${message.orEmpty()}"
    else -> message ?: "No se pudo completar la operación. Verifica la conexión."
}
