package com.jomiro.app.orobest_rrhh.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jomiro.app.orobest_rrhh.network.EmployeeDto
import com.jomiro.app.orobest_rrhh.network.LeaveRequest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val leaveTypes = listOf(
    "permiso_medico" to "Permiso médico",
    "permiso_personal" to "Permiso personal",
    "incapacidad" to "Incapacidad",
    "justificada" to "Ausencia justificada",
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LeaveRequestDialog(
    employee: EmployeeDto,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (LeaveRequest) -> Unit,
) {
    var date by remember(employee.idempleado) { mutableStateOf(todayInManagua().toString()) }
    var type by remember(employee.idempleado) { mutableStateOf(leaveTypes.first().first) }
    var daysInput by remember(employee.idempleado) { mutableStateOf("1") }
    var motive by remember(employee.idempleado) { mutableStateOf("") }
    var observations by remember(employee.idempleado) { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    val days = daysInput.replace(',', '.').toDoubleOrNull()
    val formValid = date.isNotBlank() && days != null && days in 0.01..1.0 && motive.isNotBlank()
    val typeLabel = leaveTypes.firstOrNull { it.first == type }?.second ?: "Permiso personal"

    AlertDialog(
        onDismissRequest = { if (!loading && !showDatePicker) onDismiss() },
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Solicitar permiso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Para ${employee.nombres} ${employee.apellidos}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Fecha solicitada", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(date.toDisplayDate(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Tipo de solicitud", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(
                            onClick = { typeMenuExpanded = true },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(typeLabel, modifier = Modifier.weight(1f))
                            Text("⌄", style = MaterialTheme.typography.titleMedium)
                        }
                        DropdownMenu(
                            expanded = typeMenuExpanded,
                            onDismissRequest = { typeMenuExpanded = false },
                        ) {
                            leaveTypes.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        type = value
                                        typeMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = daysInput,
                        onValueChange = { daysInput = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(5) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Días solicitados") },
                        supportingText = { Text("De 0.01 a 1 día para esta fecha") },
                        enabled = !loading,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.medium,
                    )

                    OutlinedTextField(
                        value = motive,
                        onValueChange = { motive = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Motivo") },
                        placeholder = { Text("Describe brevemente el motivo") },
                        enabled = !loading,
                        minLines = 2,
                        maxLines = 3,
                        shape = MaterialTheme.shapes.medium,
                    )

                    OutlinedTextField(
                        value = observations,
                        onValueChange = { observations = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Observaciones (opcional)") },
                        enabled = !loading,
                        minLines = 2,
                        maxLines = 3,
                        shape = MaterialTheme.shapes.medium,
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            "La solicitud quedará pendiente. RRHH o administración la revisará y decidirá desde la web.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Enviando solicitud…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        LeaveRequest(
                            id = employee.idempleado,
                            fecha = date,
                            tipo = type,
                            dias = days ?: 1.0,
                            motivo = motive.trim(),
                            observaciones = observations.trim().ifBlank { null },
                        ),
                    )
                },
                enabled = formValid && !loading,
            ) {
                Text("Enviar a RRHH")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") }
        },
        shape = RoundedCornerShape(26.dp),
    )

    if (showDatePicker) {
        LeaveDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                date = it
                showDatePicker = false
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LeaveDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { datePickerState.selectedDateMillis?.let { onConfirm(it.toManaguaDateString()) } },
                enabled = datePickerState.selectedDateMillis != null,
            ) { Text("Seleccionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = datePickerState, title = { Text("Fecha de permiso", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

private fun todayInManagua(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.of("America/Managua")).date

private fun String.toPickerMillis(): Long =
    LocalDate.parse(this).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toManaguaDateString(): String =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date.toString()

private fun String.toDisplayDate(): String = runCatching {
    val parsed = LocalDate.parse(this)
    "${parsed.day.toString().padStart(2, '0')}/${(parsed.month.ordinal + 1).toString().padStart(2, '0')}/${parsed.year}"
}.getOrDefault(this)
