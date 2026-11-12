package com.jomiro.app.orobest_rrhh.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.orobest_rrhh.network.AttendanceDto
import com.jomiro.app.orobest_rrhh.network.AttendanceEventRequest
import com.jomiro.app.orobest_rrhh.network.AttendanceOutbox
import com.jomiro.app.orobest_rrhh.network.AttendanceSyncResult
import com.jomiro.app.orobest_rrhh.network.EmployeeDto
import com.jomiro.app.orobest_rrhh.network.QueuedAttendanceEvent
import com.jomiro.app.orobest_rrhh.network.RrhhApiException
import com.jomiro.app.orobest_rrhh.session.RrhhSessionRepository
import com.jomiro.app.orobest_rrhh.biometric.FingerprintPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class AttendanceUiState(
    val employees: List<EmployeeDto> = emptyList(),
    val attendance: AttendanceDto? = null,
    val selectedEmployeeId: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val fingerprintPrompt: String? = null,
    val queuedEvents: List<QueuedAttendanceEvent> = emptyList(),
    val syncResult: AttendanceSyncResult? = null,
    val syncing: Boolean = false,
)

class AttendanceViewModel(
    private val sessionRepository: RrhhSessionRepository,
    private val attendanceOutbox: AttendanceOutbox,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()
    private var fingerprintJob: Job? = null

    init {
        viewModelScope.launch { refreshQueuedEvents() }
        viewModelScope.launch {
            sessionRepository.state.collect { session ->
                if (!session.authenticated) cancelFingerprint()
                _uiState.update {
                    it.copy(
                        employees = session.employees,
                        attendance = if (session.authenticated) it.attendance else null,
                        selectedEmployeeId = if (session.authenticated) it.selectedEmployeeId else null,
                        loading = if (session.authenticated) it.loading else false,
                    )
                }
            }
        }
        viewModelScope.launch {
            sessionRepository.state
                .map { it.authenticated to it.apiBaseUrl }
                .distinctUntilChanged()
                .collect { (authenticated, _) ->
                    if (authenticated) synchronizePendingEvents()
                }
        }
    }

    fun consult(employeeId: Int) {
        val api = sessionRepository.apiOrNull() ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, error = null, selectedEmployeeId = employeeId, attendance = null)
            }
            try {
                setAttendanceWithLocalEvents(api.getAttendance(employeeId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val employee = _uiState.value.employees.firstOrNull { it.idempleado == employeeId }
                val offlineRecord = employee?.toTodayAttendance()
                if (offlineRecord != null) {
                    setAttendanceWithLocalEvents(offlineRecord)
                    val server = sessionRepository.state.value.apiBaseUrl.normalized()
                    val events = _uiState.value.queuedEvents.filter { it.server == server }
                    _uiState.update {
                        it.copy(
                            syncResult = AttendanceSyncResult(
                                sent = 0,
                                waiting = events.count { event -> event.status == QueuedAttendanceEvent.STATUS_QUEUED },
                                needsReview = events.count { event -> event.status == QueuedAttendanceEvent.STATUS_REVIEW },
                                connectionError = "Sin conexión con el servidor. Puedes guardar la marca y se sincronizará después.",
                            ),
                        )
                    }
                } else {
                    _uiState.update { it.copy(error = error.toApiMessage()) }
                }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun identifyEmployee(pin: String? = null, qr: String? = null) {
        if (pin.isNullOrBlank() && qr.isNullOrBlank()) {
            _uiState.update { it.copy(attendance = null, selectedEmployeeId = null, error = null) }
            return
        }
        val api = sessionRepository.apiOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, attendance = null, selectedEmployeeId = null) }
            try {
                val record = api.identifyAttendanceEmployee(pin, qr)
                setAttendanceWithLocalEvents(record)
                _uiState.update { it.copy(selectedEmployeeId = record.idempleado) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update { it.copy(error = error.toApiMessage()) }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun identifyFingerprint() {
        if (_uiState.value.loading || fingerprintJob?.isActive == true) return
        val api = sessionRepository.apiOrNull() ?: return
        fingerprintJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, fingerprintPrompt = "Descargando plantillas autorizadas…", attendance = null, selectedEmployeeId = null) }
            try {
                val templates = api.getFingerprintTemplates()
                val employeeId = FingerprintPlatform.identify(templates) { prompt ->
                    _uiState.update { it.copy(fingerprintPrompt = prompt) }
                } ?: throw IllegalStateException("No se reconoció la huella. Intente otra vez o use PIN/QR.")
                ensureActive()
                if (sessionRepository.apiOrNull() !== api) throw CancellationException("La sesión cambió.")
                val record = api.getAttendance(employeeId)
                ensureActive()
                setAttendanceWithLocalEvents(record)
                _uiState.update { it.copy(selectedEmployeeId = record.idempleado, error = null, fingerprintPrompt = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update { it.copy(error = FingerprintPlatform.reportFailure(error), fingerprintPrompt = null) }
            } finally {
                _uiState.update { it.copy(loading = false, fingerprintPrompt = null) }
            }
        }
    }

    fun cancelFingerprint() {
        fingerprintJob?.cancel()
    }

    fun clearKioskEmployee() {
        if (!_uiState.value.loading) _uiState.update { it.copy(attendance = null, selectedEmployeeId = null, error = null) }
    }

    fun markKiosk(type: String) {
        val api = sessionRepository.apiOrNull() ?: return
        val record = _uiState.value.attendance ?: return
        if (type !in listOf("entrada", "salida")) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val current = try {
                    api.getAttendance(record.idempleado)
                } catch (error: RrhhApiException) {
                    throw error
                } catch (_: Throwable) {
                    record
                }
                if (type == "entrada" && current.hora_entrada != null) throw IllegalStateException("La entrada ya está registrada.")
                if (type == "salida" && current.hora_entrada == null) throw IllegalStateException("Primero debe registrarse la entrada.")
                if (type == "salida" && current.hora_salida != null) throw IllegalStateException("La salida ya está registrada.")

                val online = try {
                    api.getAttendance(current.idempleado)
                    true
                } catch (error: RrhhApiException) {
                    throw error
                } catch (_: Throwable) {
                    false
                }
                val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.of("America/Managua"))
                val time = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
                val request = attendanceRequest(current.idempleado, current.fecha, type, time, useServerTime = online)
                attendanceOutbox.enqueue(sessionRepository.state.value.apiBaseUrl, request)
                _uiState.update { it.copy(attendance = current.withLocalTimes(if (type == "entrada") time else null, if (type == "salida") time else null)) }
                synchronizePendingEvents()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update { it.copy(error = error.toApiMessage()) }
                refreshQueuedEvents()
            } finally {
                _uiState.update { it.copy(loading = false, syncing = false) }
            }
        }
    }

    fun dismissDialog() {
        if (_uiState.value.loading) return
        _uiState.update { it.copy(selectedEmployeeId = null, error = null) }
    }

    fun retrySync() {
        viewModelScope.launch { synchronizePendingEvents() }
    }

    fun applyMarks(employeeId: Int, record: AttendanceDto, entryTime: String?, exitTime: String?) {
        val api = sessionRepository.apiOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val server = sessionRepository.state.value.apiBaseUrl
                if (record.hora_entrada == null && entryTime != null) {
                    attendanceOutbox.enqueue(server, attendanceRequest(employeeId, record.fecha, "entrada", entryTime))
                }
                if (record.hora_salida == null && exitTime != null) {
                    attendanceOutbox.enqueue(server, attendanceRequest(employeeId, record.fecha, "salida", exitTime))
                }
                _uiState.update {
                    it.copy(attendance = record.withLocalTimes(entryTime, exitTime))
                }
                synchronizePendingEvents()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(error = error.toApiMessage(), attendance = record.withLocalTimes(entryTime, exitTime))
                }
                refreshQueuedEvents()
            } finally {
                _uiState.update { it.copy(loading = false, syncing = false) }
            }
        }
    }

    private suspend fun synchronizePendingEvents() {
        val api = sessionRepository.apiOrNull() ?: return
        _uiState.update { it.copy(syncing = true) }
        try {
            _uiState.update {
                it.copy(syncResult = attendanceOutbox.synchronize(api, sessionRepository.state.value.apiBaseUrl))
            }
            refreshQueuedEvents()
            _uiState.value.selectedEmployeeId?.let { employeeId ->
                runCatching { api.getAttendance(employeeId) }
                    .onSuccess { setAttendanceWithLocalEvents(it) }
            }
        } finally {
            _uiState.update { it.copy(syncing = false) }
        }
    }

    private suspend fun refreshQueuedEvents() {
        _uiState.update { it.copy(queuedEvents = attendanceOutbox.list()) }
    }

    private suspend fun setAttendanceWithLocalEvents(record: AttendanceDto) {
        refreshQueuedEvents()
        val current = _uiState.value
        val server = sessionRepository.state.value.apiBaseUrl.normalized()
        val localEvents = current.queuedEvents.filter {
            it.server == server &&
                it.request.id == record.idempleado &&
                it.request.ocurrido_en.startsWith("${record.fecha}T")
        }
        val localEntry = localEvents.lastOrNull { it.request.tipo == "entrada" }
            ?.request?.ocurrido_en?.substringAfter('T')?.take(5)
        val localExit = localEvents.lastOrNull { it.request.tipo == "salida" }
            ?.request?.ocurrido_en?.substringAfter('T')?.take(5)
        _uiState.update {
            it.copy(
                attendance = record.copy(
                    hora_entrada = record.hora_entrada ?: localEntry?.let { time -> "$time:00" },
                    hora_salida = record.hora_salida ?: localExit?.let { time -> "$time:00" },
                    registrada = record.registrada || localEntry != null || localExit != null,
                ),
            )
        }
    }
}

private fun EmployeeDto.toTodayAttendance() = AttendanceDto(
    idempleado = idempleado,
    codigo = codigo,
    empleado = "$nombres $apellidos",
    fecha = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.of("America/Managua")).date.toString(),
    registrada = false,
)

private fun attendanceRequest(employeeId: Int, date: String, type: String, time: String, useServerTime: Boolean = false) =
    AttendanceEventRequest(
        evento_id = kotlin.uuid.Uuid.random().toString(),
        id = employeeId,
        tipo = type,
        ocurrido_en = "${date}T${time}:00-06:00",
        usar_hora_servidor = useServerTime,
    )

private fun AttendanceDto.withLocalTimes(entryTime: String?, exitTime: String?) = copy(
    hora_entrada = hora_entrada ?: entryTime?.let { "$it:00" },
    hora_salida = hora_salida ?: exitTime?.let { "$it:00" },
    registrada = true,
)

private fun String.normalized() = trim().trimEnd('/')

private fun Throwable.toApiMessage(): String = when (this) {
    is RrhhApiException -> "Error HTTP $statusCode. ${message.orEmpty()}"
    else -> message ?: "No se pudo completar la operación. Verifica la conexión."
}
