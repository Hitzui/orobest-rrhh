package com.jomiro.app.orobest_rrhh.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock3
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.UsersRound
import com.jomiro.app.orobest_rrhh.network.AttendanceDto
import com.jomiro.app.orobest_rrhh.network.AttendanceSyncResult
import com.jomiro.app.orobest_rrhh.network.EmployeeDto
import com.jomiro.app.orobest_rrhh.network.QueuedAttendanceEvent

private val AttendanceAccent = Color(0xFF5142B5)
private val AttendanceAccentSoft = Color(0xFFF0EDFF)
private val AttendanceSuccess = Color(0xFF217A55)
private val AttendanceSuccessSoft = Color(0xFFE8F5EE)

@Composable
fun AsistenciaScreen(
    employees: List<EmployeeDto>,
    attendance: AttendanceDto?,
    selectedEmployeeId: Int?,
    loading: Boolean,
    error: String?,
    queuedEvents: List<QueuedAttendanceEvent>,
    currentServer: String,
    syncResult: AttendanceSyncResult?,
    syncing: Boolean,
    onConsult: (employeeId: Int) -> Unit,
    onSyncPending: () -> Unit,
    onDismissDialog: () -> Unit,
    onApplyMarks: (employeeId: Int, record: AttendanceDto, entryTime: String?, exitTime: String?) -> Unit,
) {
    val selectedEmployee = employees.firstOrNull { it.idempleado == selectedEmployeeId }
    var searchQuery by remember { mutableStateOf("") }
    val visibleEmployees = employees.filter { employee ->
        val fullName = "${employee.nombres} ${employee.apellidos}"
        fullName.contains(searchQuery, ignoreCase = true) || employee.codigo.contains(searchQuery, ignoreCase = true)
    }
    val normalizedServer = currentServer.trim().trimEnd('/')
    val currentServerEvents = queuedEvents.filter { it.server == normalizedServer }
    val otherServerPendingCount = queuedEvents.count {
        it.server != normalizedServer && it.status == QueuedAttendanceEvent.STATUS_QUEUED
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Control diario de entrada y salida",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = AttendanceAccentSoft),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.UsersRound, contentDescription = null, tint = AttendanceAccent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Personal de tu sucursal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Selecciona una persona para consultar o registrar sus marcas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    employees.size.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AttendanceAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (queuedEvents.isNotEmpty() || syncResult?.connectionError != null) {
            val reviewCount = currentServerEvents.count { it.status == QueuedAttendanceEvent.STATUS_REVIEW }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (reviewCount > 0) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        when {
                            reviewCount > 0 -> "$reviewCount marca(s) requieren revisión"
                            syncing -> "Sincronizando marcas guardadas…"
                            currentServerEvents.isNotEmpty() -> "${currentServerEvents.size} marca(s) pendientes o en revisión"
                            else -> "Sincronización de marcas"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    currentServerEvents.filter { it.status == QueuedAttendanceEvent.STATUS_REVIEW }.take(3).forEach { event ->
                        val employee = employees.firstOrNull { it.idempleado == event.request.id }
                        Text(
                            "${employee?.let { "${it.nombres} ${it.apellidos}" } ?: "Empleado ${event.request.id}"}: ${event.detail ?: "Conflicto con una marca guardada; revisar desde la web."}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    syncResult?.connectionError?.let {
                        Text("La marca permanece en este dispositivo. $it", style = MaterialTheme.typography.bodySmall)
                    }
                    if (otherServerPendingCount > 0) {
                        Text(
                            "$otherServerPendingCount marca(s) guardadas para otro servidor; inicia sesión en ese servidor para sincronizarlas.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (currentServerEvents.any { it.status == QueuedAttendanceEvent.STATUS_QUEUED }) {
                        TextButton(onClick = onSyncPending, enabled = !syncing) {
                            Text(if (syncing) "Sincronizando…" else "Reintentar sincronización")
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
            placeholder = { Text("Buscar por nombre o código") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        if (employees.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay empleados activos disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (visibleEmployees.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontraron empleados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth >= 800.dp) {
                    AttendanceEmployeeTable(
                        employees = visibleEmployees,
                        loading = loading,
                        onConsult = onConsult,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visibleEmployees, key = { it.idempleado }) { employee ->
                            EmployeeAttendanceCard(
                                employee = employee,
                                onClick = { if (!loading) onConsult(employee.idempleado) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedEmployee != null) {
        val selectedRecord = attendance?.takeIf { it.idempleado == selectedEmployee.idempleado }
        val localEvents = queuedEvents.filter {
            it.server == normalizedServer &&
                it.request.id == selectedEmployee.idempleado &&
                it.request.ocurrido_en.startsWith("${selectedRecord?.fecha}T")
        }
        AttendanceDialog(
            employee = selectedEmployee,
            record = selectedRecord,
            entryQueued = localEvents.any { it.request.tipo == "entrada" && it.status == QueuedAttendanceEvent.STATUS_QUEUED },
            entryNeedsReview = localEvents.any { it.request.tipo == "entrada" && it.status == QueuedAttendanceEvent.STATUS_REVIEW },
            exitQueued = localEvents.any { it.request.tipo == "salida" && it.status == QueuedAttendanceEvent.STATUS_QUEUED },
            exitNeedsReview = localEvents.any { it.request.tipo == "salida" && it.status == QueuedAttendanceEvent.STATUS_REVIEW },
            loading = loading,
            error = error,
            onDismiss = onDismissDialog,
            onRetry = { onConsult(selectedEmployee.idempleado) },
            onApply = { record, entry, exit ->
                onApplyMarks(selectedEmployee.idempleado, record, entry, exit)
            },
        )
    }
}

@Composable
private fun AttendanceEmployeeTable(
    employees: List<EmployeeDto>,
    loading: Boolean,
    onConsult: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Código", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Empleado", Modifier.weight(2.2f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Sucursal", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Acción", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(employees, key = { it.idempleado }) { employee ->
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(employee.codigo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${employee.nombres} ${employee.apellidos}", Modifier.weight(2.2f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(employee.idagencia.toString(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Button(
                                onClick = { onConsult(employee.idempleado) },
                                enabled = !loading,
                                modifier = Modifier.weight(1f).height(38.dp),
                            ) {
                                Icon(Lucide.Clock3, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ver marcas")
                            }
                        }
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeAttendanceCard(
    employee: EmployeeDto,
    onClick: () -> Unit,
) {
    val initials = listOf(employee.nombres, employee.apellidos)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(AttendanceAccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials, color = AttendanceAccent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${employee.nombres} ${employee.apellidos}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Código ${employee.codigo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Lucide.ChevronRight, contentDescription = "Ver asistencia", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AttendanceDialog(
    employee: EmployeeDto,
    record: AttendanceDto?,
    entryQueued: Boolean,
    entryNeedsReview: Boolean,
    exitQueued: Boolean,
    exitNeedsReview: Boolean,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onApply: (record: AttendanceDto, entryTime: String?, exitTime: String?) -> Unit,
) {
    var entryInput by remember(employee.idempleado) { mutableStateOf("") }
    var exitInput by remember(employee.idempleado) { mutableStateOf("") }
    var entryPickerVisible by remember { mutableStateOf(false) }
    var exitPickerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(record?.hora_entrada, record?.hora_salida) {
        entryInput = ""
        exitInput = ""
    }

    val entryExists = record?.hora_entrada != null
    val exitExists = record?.hora_salida != null
    val effectiveEntry = record?.hora_entrada?.take(5) ?: entryInput
    val entryIsValid = effectiveEntry.isValidTime()
    val entryWithinFutureLimit = !entryIsValid || effectiveEntry.toMinutes() <= currentManaguaMinutes() + 5
    val exitWithinFutureLimit = !exitInput.isValidTime() || exitInput.toMinutes() <= currentManaguaMinutes() + 5
    val exitIsAfterEntry = entryIsValid && exitInput.isValidTime() && exitInput.toMinutes() > effectiveEntry.toMinutes()
    val hasNewMark = (!entryExists && entryInput.isNotBlank()) || (!exitExists && exitInput.isNotBlank())
    val canApply = record != null && !loading && !exitExists && hasNewMark &&
        (entryExists || (entryIsValid && entryWithinFutureLimit)) &&
        !entryNeedsReview &&
        exitWithinFutureLimit &&
        (exitInput.isBlank() || (!exitExists && exitIsAfterEntry))

    AlertDialog(
        onDismissRequest = { if (!loading && !entryPickerVisible && !exitPickerVisible) onDismiss() },
        title = null,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(AttendanceAccentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${employee.nombres.firstOrNull()?.uppercase().orEmpty()}${employee.apellidos.firstOrNull()?.uppercase().orEmpty()}",
                            color = AttendanceAccent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Registrar asistencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${employee.nombres} ${employee.apellidos}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when {
                    loading && record == null -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("Consultando las marcas de hoy…")
                        }
                    }

                    record == null -> {
                        Text(error ?: "No se pudo cargar la asistencia.", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry, enabled = !loading) { Text("Reintentar") }
                    }

                    else -> {
                        Text("Hoy · ${record.fecha}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TimeSelectionRow(
                            title = "Entrada",
                            value = if (entryExists) record.hora_entrada.orEmpty().take(5) else entryInput,
                            isSaved = entryExists,
                            enabled = !entryExists && !loading,
                            helper = when {
                                entryNeedsReview -> "Requiere revisión desde la web de RRHH"
                                entryQueued -> "Guardada en este dispositivo · pendiente de sincronización"
                                entryExists -> "Registrada · las correcciones se hacen desde la web"
                                entryInput.isNotBlank() && !entryWithinFutureLimit -> "No puede superar la hora actual por más de 5 minutos"
                                entryInput.isNotBlank() -> "Hora seleccionada"
                                else -> "Selecciona la hora de entrada"
                            },
                            helperColor = if (entryInput.isNotBlank() && !entryWithinFutureLimit) MaterialTheme.colorScheme.error else null,
                            onClick = { entryPickerVisible = true },
                        )
                        TimeSelectionRow(
                            title = "Salida",
                            value = if (exitExists) record.hora_salida.orEmpty().take(5) else exitInput,
                            isSaved = exitExists,
                            enabled = entryIsValid && !entryNeedsReview && !exitExists && !loading,
                            helper = when {
                                exitNeedsReview -> "Requiere revisión desde la web de RRHH"
                                exitQueued -> "Guardada en este dispositivo · pendiente de sincronización"
                                exitExists -> "Registrada · las correcciones se hacen desde la web"
                                !entryIsValid || !entryWithinFutureLimit -> "Registra una entrada válida primero"
                                exitInput.isNotBlank() && !exitWithinFutureLimit -> "No puede superar la hora actual por más de 5 minutos"
                                exitInput.isNotBlank() && !exitIsAfterEntry -> "La salida debe ser posterior a la entrada"
                                else -> "Puedes registrarla ahora o más tarde"
                            },
                            helperColor = if (exitInput.isNotBlank() && (!exitWithinFutureLimit || !exitIsAfterEntry)) MaterialTheme.colorScheme.error else null,
                            onClick = { exitPickerVisible = true },
                        )
                        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (loading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Aplicando marcas…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (record != null && !exitExists) {
                Button(
                    onClick = {
                        onApply(
                            record,
                            entryInput.takeIf { !entryExists && it.isNotBlank() },
                            exitInput.takeIf { !exitExists && it.isNotBlank() },
                        )
                    },
                    enabled = canApply,
                ) {
                    Text("Aplicar")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !loading && !entryPickerVisible && !exitPickerVisible,
            ) { Text(if (exitExists) "Listo" else "Cancelar") }
        },
        shape = RoundedCornerShape(28.dp),
    )

    if (entryPickerVisible) {
        AttendanceTimePickerDialog(
            title = "Hora de entrada",
            initialTime = entryInput.ifBlank { currentManaguaTime() },
            onDismiss = { entryPickerVisible = false },
            onConfirm = { entryInput = it; entryPickerVisible = false },
        )
    }
    if (exitPickerVisible) {
        AttendanceTimePickerDialog(
            title = "Hora de salida",
            initialTime = exitInput.ifBlank { currentManaguaTime() },
            onDismiss = { exitPickerVisible = false },
            onConfirm = { exitInput = it; exitPickerVisible = false },
        )
    }
}

@Composable
private fun TimeSelectionRow(
    title: String,
    value: String,
    isSaved: Boolean,
    enabled: Boolean,
    helper: String = if (isSaved) "Registrada · las correcciones se hacen desde la web" else "Selecciona la hora",
    helperColor: Color? = null,
    onClick: () -> Unit,
) {
    val containerColor = if (isSaved) AttendanceSuccessSoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val accentColor = if (isSaved) AttendanceSuccess else AttendanceAccent

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Clock3, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value.ifBlank { "Seleccionar hora" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(helper, style = MaterialTheme.typography.bodySmall, color = helperColor ?: accentColor)
            }
            if (!isSaved) Icon(Lucide.ChevronRight, contentDescription = "Elegir hora", tint = accentColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceTimePickerDialog(
    title: String,
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val parts = initialTime.split(':')
    val initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
    val initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onConfirm("${state.hour.toString().padStart(2, '0')}:${state.minute.toString().padStart(2, '0')}")
                    }) {
                        Text("Elegir hora")
                    }
                }
            }
        }
    }
}

private fun String.isValidTime(): Boolean =
    Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(this)

private fun String.toMinutes(): Int =
    substringBefore(':').toInt() * 60 + substringAfter(':').toInt()

/** API uses America/Managua, which is UTC-06:00 for current dates. */
private fun currentManaguaMinutes(): Int {
    val utcMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val managuaMillis = utcMillis - 6 * 60 * 60 * 1000L
    return ((managuaMillis / 60_000) % (24 * 60)).toInt()
}

private fun currentManaguaTime(): String {
    val minutes = currentManaguaMinutes()
    return "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
}
