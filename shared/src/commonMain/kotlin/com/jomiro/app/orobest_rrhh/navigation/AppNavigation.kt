package com.jomiro.app.orobest_rrhh.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ClipboardClock
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.UsersRound
import com.jomiro.app.orobest_rrhh.network.EmployeeDto
import com.jomiro.app.orobest_rrhh.network.AttendanceDto
import com.jomiro.app.orobest_rrhh.network.AttendanceEventRequest
import com.jomiro.app.orobest_rrhh.network.AttendanceOutbox
import com.jomiro.app.orobest_rrhh.network.AttendanceSyncResult
import com.jomiro.app.orobest_rrhh.network.RrhhApiClient
import com.jomiro.app.orobest_rrhh.network.RrhhApiClientFactory
import com.jomiro.app.orobest_rrhh.network.RrhhApiException
import com.jomiro.app.orobest_rrhh.network.QueuedAttendanceEvent
import com.jomiro.app.orobest_rrhh.network.defaultRrhhApiBaseUrl
import com.jomiro.app.orobest_rrhh.screens.EmpleadosScreen
import com.jomiro.app.orobest_rrhh.screens.AsistenciaScreen
import com.jomiro.app.orobest_rrhh.screens.ConfiguracionScreen
import com.jomiro.app.orobest_rrhh.screens.HomeScreen
import com.jomiro.app.orobest_rrhh.screens.LoginScreen
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private enum class AppDestination(val label: String) {
    Home("Inicio"),
    Empleados("Empleados"),
    Asistencia("Asistencia"),
    Configuracion("Configuración"),
}

private val NavigationPurple = Color(0xFF5143A5)
private val NavigationSelected = Color(0xFF7164C5)
private val NavigationMuted = Color(0xFFB8AEF4)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppNavigation() {
    val scope = rememberCoroutineScope()
    val attendanceOutbox = koinInject<AttendanceOutbox>()
    val apiClientFactory = koinInject<RrhhApiClientFactory>()
    var api by remember { mutableStateOf<RrhhApiClient?>(null) }
    var employees by remember { mutableStateOf<List<EmployeeDto>>(emptyList()) }
    var destination by remember { mutableStateOf(AppDestination.Home) }
    var apiBaseUrl by remember { mutableStateOf(defaultRrhhApiBaseUrl()) }
    var signedInUsername by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var employeesError by remember { mutableStateOf<String?>(null) }
    var attendance by remember { mutableStateOf<AttendanceDto?>(null) }
    var attendanceEmployeeId by remember { mutableStateOf<Int?>(null) }
    var attendanceError by remember { mutableStateOf<String?>(null) }
    var queuedAttendanceEvents by remember { mutableStateOf<List<QueuedAttendanceEvent>>(emptyList()) }
    var attendanceSyncResult by remember { mutableStateOf<AttendanceSyncResult?>(null) }
    var syncingAttendance by remember { mutableStateOf(false) }

    val activeApi = api
    LaunchedEffect(activeApi) {
        queuedAttendanceEvents = attendanceOutbox.list()
        if (activeApi != null && queuedAttendanceEvents.any {
                it.status == QueuedAttendanceEvent.STATUS_QUEUED && it.server == apiBaseUrl.trim().trimEnd('/')
            }) {
            syncingAttendance = true
            attendanceSyncResult = attendanceOutbox.synchronize(activeApi, apiBaseUrl)
            queuedAttendanceEvents = attendanceOutbox.list()
            syncingAttendance = false
        }
    }
    if (activeApi == null) {
        LoginScreen(
            initialBaseUrl = defaultRrhhApiBaseUrl(),
            loading = loading,
            error = loginError,
            onLogin = { baseUrl, username, password ->
                scope.launch {
                    loading = true
                    loginError = null
                    var candidate: RrhhApiClient? = null
                    try {
                        val loginClient = apiClientFactory.create(baseUrl, "")
                        candidate = loginClient
                        val loginResult = loginClient.login(username, password)
                        loginClient.close()
                        candidate = null

                        val sessionApi = apiClientFactory.create(baseUrl, loginResult.access_token)
                        candidate = sessionApi
                        val loadedEmployees = sessionApi.getEmployees()
                        api = sessionApi
                        employees = loadedEmployees
                        apiBaseUrl = baseUrl
                        signedInUsername = loginResult.usuario.username
                        destination = AppDestination.Home
                        candidate = null
                    } catch (error: Throwable) {
                        candidate?.close()
                        loginError = error.toLoginMessage()
                    } finally {
                        loading = false
                    }
                }
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(destination.label, fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = NavigationPurple,
                    shadowElevation = 16.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(76.dp).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppDestination.entries.forEach { item ->
                            FloatingNavigationItem(
                                destination = item,
                                selected = destination == item,
                                onClick = { destination = item },
                            )
                        }
                    }
                }
            }
        },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            when (destination) {
                AppDestination.Home -> HomeScreen(
                    employeeCount = employees.size,
                    onOpenEmployees = { destination = AppDestination.Empleados },
                    onOpenAttendance = { destination = AppDestination.Asistencia },
                )

                AppDestination.Empleados -> EmpleadosScreen(
                    employees = employees,
                    onRefresh = {
                        scope.launch {
                            loading = true
                            employeesError = null
                            try {
                                employees = activeApi.getEmployees()
                            } catch (error: Throwable) {
                                employeesError = error.toApiMessage()
                            } finally {
                                loading = false
                            }
                        }
                    },
                    refreshing = loading,
                    error = employeesError,
                    onMarkAttendance = { employeeId ->
                        destination = AppDestination.Asistencia
                        attendanceEmployeeId = employeeId
                        attendance = null
                        attendanceError = null
                        scope.launch {
                            loading = true
                            try {
                                val remoteRecord = activeApi.getAttendance(employeeId)
                                attendance = mergeQueuedAttendance(remoteRecord, employeeId, queuedAttendanceEvents, apiBaseUrl)
                            } catch (error: Throwable) {
                                val employee = employees.firstOrNull { it.idempleado == employeeId }
                                val localRecord = employee?.let {
                                    AttendanceDto(
                                        idempleado = it.idempleado,
                                        codigo = it.codigo,
                                        empleado = "${it.nombres} ${it.apellidos}",
                                        fecha = currentManaguaDate(),
                                        registrada = false,
                                    )
                                }
                                attendance = localRecord?.let { mergeQueuedAttendance(it, employeeId, queuedAttendanceEvents, apiBaseUrl) }
                                attendanceError = if (attendance == null) error.toApiMessage() else null
                                attendanceSyncResult = AttendanceSyncResult(
                                    sent = 0,
                                    waiting = queuedAttendanceEvents.count { it.status == QueuedAttendanceEvent.STATUS_QUEUED },
                                    needsReview = queuedAttendanceEvents.count { it.status == QueuedAttendanceEvent.STATUS_REVIEW },
                                    connectionError = "Sin conexión con el servidor. Puedes guardar la marca y se sincronizará después.",
                                )
                            } finally {
                                loading = false
                            }
                        }
                    },
                    onSubmitLeaveRequest = { request -> activeApi.submitLeaveRequest(request) },
                )

                AppDestination.Asistencia -> AsistenciaScreen(
                    employees = employees,
                    attendance = attendance,
                    selectedEmployeeId = attendanceEmployeeId,
                    loading = loading,
                    error = attendanceError,
                    queuedEvents = queuedAttendanceEvents,
                    currentServer = apiBaseUrl,
                    syncResult = attendanceSyncResult,
                    syncing = syncingAttendance,
                    onConsult = { employeeId ->
                        scope.launch {
                            loading = true
                            attendanceError = null
                            attendanceEmployeeId = employeeId
                            attendance = null
                            try {
                                val remoteRecord = activeApi.getAttendance(employeeId)
                                attendance = mergeQueuedAttendance(remoteRecord, employeeId, queuedAttendanceEvents, apiBaseUrl)
                            } catch (error: Throwable) {
                                val employee = employees.firstOrNull { it.idempleado == employeeId }
                                attendance = employee?.let {
                                    AttendanceDto(
                                        idempleado = it.idempleado,
                                        codigo = it.codigo,
                                        empleado = "${it.nombres} ${it.apellidos}",
                                        fecha = currentManaguaDate(),
                                        registrada = false,
                                    )
                                }?.let { mergeQueuedAttendance(it, employeeId, queuedAttendanceEvents, apiBaseUrl) }
                                attendanceError = if (attendance == null) error.toApiMessage() else null
                                attendanceSyncResult = AttendanceSyncResult(
                                    sent = 0,
                                    waiting = queuedAttendanceEvents.count { it.status == QueuedAttendanceEvent.STATUS_QUEUED },
                                    needsReview = queuedAttendanceEvents.count { it.status == QueuedAttendanceEvent.STATUS_REVIEW },
                                    connectionError = "Sin conexión con el servidor. Puedes guardar la marca y se sincronizará después.",
                                )
                            } finally {
                                loading = false
                            }
                        }
                    },
                    onSyncPending = {
                        scope.launch {
                            syncingAttendance = true
                            try {
                                attendanceSyncResult = attendanceOutbox.synchronize(activeApi, apiBaseUrl)
                                queuedAttendanceEvents = attendanceOutbox.list()
                                attendanceEmployeeId?.let { employeeId ->
                                    runCatching { activeApi.getAttendance(employeeId) }
                                        .onSuccess { attendance = mergeQueuedAttendance(it, employeeId, queuedAttendanceEvents, apiBaseUrl) }
                                }
                            } finally {
                                syncingAttendance = false
                            }
                        }
                    },
                    onDismissDialog = {
                        if (!loading) {
                            attendanceEmployeeId = null
                            attendanceError = null
                        }
                    },
                    onApplyMarks = { employeeId, record, entryTime, exitTime ->
                        scope.launch {
                            loading = true
                            attendanceError = null
                            try {
                                if (record.hora_entrada == null && entryTime != null) {
                                    attendanceOutbox.enqueue(
                                        apiBaseUrl,
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
                                        apiBaseUrl,
                                        AttendanceEventRequest(
                                            evento_id = kotlin.uuid.Uuid.random().toString(),
                                            id = employeeId,
                                            tipo = "salida",
                                            ocurrido_en = "${record.fecha}T$exitTime:00-06:00",
                                        ),
                                    )
                                }
                                attendance = record.copy(
                                    hora_entrada = record.hora_entrada ?: entryTime?.let { "$it:00" },
                                    hora_salida = record.hora_salida ?: exitTime?.let { "$it:00" },
                                    registrada = true,
                                )
                                syncingAttendance = true
                                attendanceSyncResult = attendanceOutbox.synchronize(activeApi, apiBaseUrl)
                                queuedAttendanceEvents = attendanceOutbox.list()
                                runCatching { activeApi.getAttendance(employeeId) }
                                    .onSuccess { attendance = mergeQueuedAttendance(it, employeeId, queuedAttendanceEvents, apiBaseUrl) }
                            } catch (error: Throwable) {
                                attendanceError = error.toApiMessage()
                                queuedAttendanceEvents = attendanceOutbox.list()
                                attendance = record.copy(
                                    hora_entrada = record.hora_entrada ?: entryTime?.let { "$it:00" },
                                    hora_salida = record.hora_salida ?: exitTime?.let { "$it:00" },
                                    registrada = true,
                                )
                            } finally {
                                syncingAttendance = false
                                loading = false
                            }
                        }
                    },
                )

                AppDestination.Configuracion -> ConfiguracionScreen(
                    username = signedInUsername,
                    apiBaseUrl = apiBaseUrl,
                    onLogout = {
                        scope.launch {
                            runCatching { activeApi.logout() }
                            activeApi.close()
                            api = null
                            employees = emptyList()
                            attendance = null
                            attendanceEmployeeId = null
                            signedInUsername = ""
                            destination = AppDestination.Home
                        }
                    },
                )
            }
        }
    }
}

private fun currentManaguaDate(): String {
    val now = kotlin.time.Clock.System.now()
    return now.toLocalDateTime(TimeZone.of("America/Managua")).date.toString()
}

private fun mergeQueuedAttendance(
    record: AttendanceDto,
    employeeId: Int,
    queuedEvents: List<QueuedAttendanceEvent>,
    server: String,
): AttendanceDto {
    val localEvents = queuedEvents
        .filter {
            it.server == server.trim().trimEnd('/') &&
                it.request.id == employeeId &&
                it.request.ocurrido_en.startsWith("${record.fecha}T")
        }
    val queuedEntry = localEvents.lastOrNull { it.request.tipo == "entrada" }
        ?.request?.ocurrido_en?.substringAfter('T')?.take(5)
    val queuedExit = localEvents.lastOrNull { it.request.tipo == "salida" }
        ?.request?.ocurrido_en?.substringAfter('T')?.take(5)

    return record.copy(
        hora_entrada = record.hora_entrada ?: queuedEntry?.let { "$it:00" },
        hora_salida = record.hora_salida ?: queuedExit?.let { "$it:00" },
        registrada = record.registrada || queuedEntry != null || queuedExit != null,
    )
}

@Composable
private fun RowScope.FloatingNavigationItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) NavigationSelected else Color.Transparent,
        label = "navigation-item-background",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else NavigationMuted,
        label = "navigation-item-tint",
    )
    val icon = when (destination) {
        AppDestination.Home -> Lucide.House
        AppDestination.Empleados -> Lucide.UsersRound
        AppDestination.Asistencia -> Lucide.ClipboardClock
        AppDestination.Configuracion -> Lucide.Settings
    }

    Row(
        modifier = Modifier
            .weight(if (selected) 2f else 1f)
            .fillMaxHeight()
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .clickable(onClick = onClick)
            .animateContentSize(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = destination.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        if (selected) {
            Spacer(Modifier.size(7.dp))
            Text(
                text = destination.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

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
    else -> message ?: "No se pudo actualizar la lista de empleados. Verifica la red."
}
