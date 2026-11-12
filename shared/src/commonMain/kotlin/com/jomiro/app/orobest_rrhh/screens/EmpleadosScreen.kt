package com.jomiro.app.orobest_rrhh.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ClipboardClock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.UsersRound
import com.jomiro.app.orobest_rrhh.network.EmployeeDto
import com.jomiro.app.orobest_rrhh.network.LeaveRequest
import com.jomiro.app.orobest_rrhh.biometric.FingerprintPlatform
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

@Composable
fun EmpleadosScreen(
    employees: List<EmployeeDto>,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    error: String?,
    onMarkAttendance: (employeeId: Int) -> Unit,
    onSubmitLeaveRequest: suspend (LeaveRequest) -> String,
    onGetFingerprintEnrolledEmployeeIds: suspend () -> List<Int> = { emptyList() },
    onSaveFingerprint: suspend (Int, String) -> String = { _, _ -> error("Gestión de huellas no habilitada.") },
    onRevokeFingerprint: suspend (Int) -> String = { error("Gestión de huellas no habilitada.") },
    canManageFingerprints: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var leaveEmployee by remember { mutableStateOf<EmployeeDto?>(null) }
    var submittingLeave by remember { mutableStateOf(false) }
    var leaveError by remember { mutableStateOf<String?>(null) }
    var leaveSuccess by remember { mutableStateOf<String?>(null) }
    var fingerprintEmployee by remember { mutableStateOf<EmployeeDto?>(null) }
    var fingerprintEnrolled by remember { mutableStateOf(false) }
    var fingerprintConsent by remember { mutableStateOf(false) }
    var fingerprintBusy by remember { mutableStateOf(false) }
    var fingerprintPrompt by remember { mutableStateOf<String?>(null) }
    var fingerprintError by remember { mutableStateOf<String?>(null) }
    var confirmFingerprintRevoke by remember { mutableStateOf(false) }
    var fingerprintJob by remember { mutableStateOf<Job?>(null) }
    var fingerprintLoaded by remember { mutableStateOf(false) }
    var fingerprintSaving by remember { mutableStateOf(false) }

    fun manageFingerprint(employee: EmployeeDto) {
        fingerprintJob?.cancel()
        fingerprintEmployee = employee
        fingerprintEnrolled = false
        fingerprintConsent = false
        fingerprintLoaded = false
        fingerprintBusy = true
        fingerprintError = null
        fingerprintPrompt = "Consultando el estado de enrolamiento…"
        fingerprintJob = scope.launch {
            try {
                fingerprintEnrolled = employee.idempleado in onGetFingerprintEnrolledEmployeeIds()
                fingerprintLoaded = true
                fingerprintPrompt = if (fingerprintEnrolled) "Huella enrolada." else "No hay una huella enrolada."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                fingerprintError = error.message ?: "No se pudo consultar el estado de la huella."
                fingerprintPrompt = null
            } finally {
                fingerprintBusy = false
            }
        }
    }
    val visibleEmployees = employees.filter { employee ->
        val name = "${employee.nombres} ${employee.apellidos}"
        name.contains(searchQuery, ignoreCase = true) || employee.codigo.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Personal de tu sucursal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "La asistencia es la acción principal; los datos del empleado son informativos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "Actualizando…" else "Actualizar")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.UsersRound, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Empleados activos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(employees.size.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
            placeholder = { Text("Buscar por nombre o código") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )

        if (refreshing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Sincronizando empleados…", style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        leaveSuccess?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    androidx.compose.material3.TextButton(onClick = { leaveSuccess = null }) { Text("Cerrar") }
                }
            }
        }

        when {
            employees.isEmpty() && !refreshing -> EmptyEmployeesMessage("No hay empleados activos disponibles para esta sucursal.")
            visibleEmployees.isEmpty() && searchQuery.isNotBlank() -> EmptyEmployeesMessage("No encontramos empleados con esa búsqueda.")
            else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth >= 800.dp) {
                    EmployeeTable(
                        employees = visibleEmployees,
                        onMarkAttendance = { onMarkAttendance(it.idempleado) },
                        onManageFingerprint = if (FingerprintPlatform.isAvailable && canManageFingerprints) ::manageFingerprint else null,
                        onRequestLeave = {
                            leaveError = null
                            leaveEmployee = it
                        },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visibleEmployees, key = { it.idempleado }) { employee ->
                            EmployeeListCard(
                                employee = employee,
                                onMarkAttendance = { onMarkAttendance(employee.idempleado) },
                                onManageFingerprint = if (FingerprintPlatform.isAvailable && canManageFingerprints) {
                                    { manageFingerprint(employee) }
                                } else null,
                                onRequestLeave = {
                                    leaveError = null
                                    leaveEmployee = employee
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    leaveEmployee?.let { employee ->
        LeaveRequestDialog(
            employee = employee,
            loading = submittingLeave,
            error = leaveError,
            onDismiss = { if (!submittingLeave) leaveEmployee = null },
            onSubmit = { request ->
                scope.launch {
                    submittingLeave = true
                    leaveError = null
                    leaveSuccess = null
                    try {
                        leaveSuccess = onSubmitLeaveRequest(request).ifBlank {
                            "Solicitud enviada a RRHH para revisión."
                        }
                        leaveEmployee = null
                    } catch (exception: Throwable) {
                        leaveError = when (exception) {
                            is com.jomiro.app.orobest_rrhh.network.RrhhApiException ->
                                "No se pudo enviar la solicitud (HTTP ${exception.statusCode}). ${exception.message.orEmpty()}"
                            else -> exception.message ?: "No se pudo enviar la solicitud. Verifica la conexión."
                        }
                    } finally {
                        submittingLeave = false
                    }
                }
            },
        )
    }

    fingerprintEmployee?.let { employee ->
        AlertDialog(
            onDismissRequest = { if (!fingerprintBusy) fingerprintEmployee = null },
            title = { Text("Huella · ${employee.nombres} ${employee.apellidos}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    fingerprintPrompt?.let { Text(it) }
                    fingerprintError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!fingerprintEnrolled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = fingerprintConsent, onCheckedChange = { fingerprintConsent = it }, enabled = !fingerprintBusy)
                            Text("El empleado dio su consentimiento informado para el uso de su huella en control de asistencia.")
                        }
                    }
                }
            },
            confirmButton = {
                if (fingerprintEnrolled) {
                    TextButton(onClick = { confirmFingerprintRevoke = true }, enabled = !fingerprintBusy) { Text("Revocar huella") }
                } else {
                    TextButton(
                        enabled = fingerprintLoaded && fingerprintConsent && !fingerprintBusy,
                        onClick = {
                            fingerprintJob = scope.launch {
                                fingerprintBusy = true
                                fingerprintError = null
                                try {
                                    val fmd = FingerprintPlatform.enroll { fingerprintPrompt = it }
                                    fingerprintSaving = true
                                    fingerprintPrompt = "Guardando plantilla cifrada…"
                                    onSaveFingerprint(employee.idempleado, fmd)
                                    fingerprintEnrolled = true
                                    fingerprintPrompt = "Huella enrolada correctamente."
                                } catch (cancelled: CancellationException) {
                                    fingerprintPrompt = "Captura cancelada. Puede volver a intentar."
                                    throw cancelled
                                } catch (error: Throwable) {
                                    fingerprintError = FingerprintPlatform.reportFailure(error)
                                } finally {
                                    fingerprintSaving = false
                                    fingerprintBusy = false
                                }
                            }
                        },
                    ) { Text(if (fingerprintBusy) "Procesando…" else "Capturar y enrolar") }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (fingerprintBusy) fingerprintJob?.cancel() else fingerprintEmployee = null },
                    enabled = !fingerprintSaving,
                ) { Text(if (fingerprintBusy) "Cancelar captura" else "Cerrar") }
            },
        )
        if (confirmFingerprintRevoke) {
            AlertDialog(
                onDismissRequest = { confirmFingerprintRevoke = false },
                title = { Text("¿Revocar la huella?") },
                text = { Text("Se eliminará la plantilla biométrica del servidor y dejará de funcionar para identificar al empleado.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            fingerprintBusy = true
                            fingerprintError = null
                            try {
                                onRevokeFingerprint(employee.idempleado)
                                fingerprintEnrolled = false
                                fingerprintPrompt = "Huella revocada y eliminada."
                                confirmFingerprintRevoke = false
                            } catch (error: Throwable) {
                                fingerprintError = error.message ?: "No se pudo revocar la huella."
                            } finally {
                                fingerprintBusy = false
                            }
                        }
                    }) { Text("Revocar") }
                },
                dismissButton = { TextButton(onClick = { confirmFingerprintRevoke = false }) { Text("Cancelar") } },
            )
        }
    }
}

@Composable
private fun EmployeeTable(
    employees: List<EmployeeDto>,
    onMarkAttendance: (EmployeeDto) -> Unit,
    onManageFingerprint: ((EmployeeDto) -> Unit)?,
    onRequestLeave: (EmployeeDto) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Código", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Empleado", Modifier.weight(2.2f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Sucursal", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Estado", Modifier.weight(0.7f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Acciones", Modifier.weight(1.8f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(employees, key = { it.idempleado }) { employee ->
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(employee.codigo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${employee.nombres} ${employee.apellidos}", Modifier.weight(2.2f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(employee.agencia_descripcion?.takeIf { it.isNotBlank() } ?: "#${employee.idagencia}", Modifier.weight(0.8f), style = MaterialTheme.typography.bodyMedium)
                            Text("Activo", Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Row(Modifier.weight(1.8f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onMarkAttendance(employee) }, modifier = Modifier.height(38.dp)) {
                                    Icon(Lucide.ClipboardClock, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Asistencia")
                                }
                                OutlinedButton(onClick = { onRequestLeave(employee) }, modifier = Modifier.height(38.dp)) {
                                    Text("Solicitar permiso")
                                }
                                onManageFingerprint?.let { manage ->
                                    OutlinedButton(onClick = { manage(employee) }, modifier = Modifier.height(38.dp)) { Text("Huella") }
                                }
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
private fun EmployeeListCard(
    employee: EmployeeDto,
    onMarkAttendance: () -> Unit,
    onManageFingerprint: (() -> Unit)?,
    onRequestLeave: () -> Unit,
) {
    val initials = "${employee.nombres.firstOrNull()?.uppercase().orEmpty()}${employee.apellidos.firstOrNull()?.uppercase().orEmpty()}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initials, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "${employee.nombres} ${employee.apellidos}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Código ${employee.codigo} · Sucursal ${employee.agencia_descripcion?.takeIf { it.isNotBlank() } ?: "#${employee.idagencia}"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Activo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onMarkAttendance, modifier = Modifier.weight(1f).height(44.dp)) {
                        Icon(Lucide.ClipboardClock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Asistencia")
                    }
                    OutlinedButton(onClick = onRequestLeave, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("Solicitar permiso")
                    }
                }
                onManageFingerprint?.let { manage ->
                    OutlinedButton(onClick = manage, modifier = Modifier.fillMaxWidth().height(40.dp)) { Text("Gestionar huella") }
                }
            }
    }
}

@Composable
private fun EmptyEmployeesMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
