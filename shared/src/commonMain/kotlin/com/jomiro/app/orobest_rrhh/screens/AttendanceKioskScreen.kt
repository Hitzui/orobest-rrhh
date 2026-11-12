package com.jomiro.app.orobest_rrhh.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.jomiro.app.orobest_rrhh.network.AttendanceDto

@Composable
fun AttendanceKioskScreen(
    attendance: AttendanceDto?,
    loading: Boolean,
    error: String?,
    fingerprintPrompt: String? = null,
    queuedCount: Int,
    onIdentifyPin: (String) -> Unit,
    onIdentifyQr: (String) -> Unit,
    onIdentifyFingerprint: (() -> Unit)? = null,
    onCancelFingerprint: () -> Unit = {},
    onMark: (String) -> Unit,
    onSync: () -> Unit,
    onReset: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { onCancelFingerprint() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Marcar asistencia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Identifícate con tu PIN, tarjeta QR o huella.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (attendance == null) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PIN de 6 dígitos") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                    Button(onClick = { onIdentifyPin(pin); pin = "" }, enabled = pin.length == 6 && !loading, modifier = Modifier.fillMaxWidth()) {
                        Text("Continuar con PIN")
                    }
                    AttendanceQrScannerButton(onScanned = onIdentifyQr)
                    onIdentifyFingerprint?.let { identify ->
                        Button(onClick = identify, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text("Identificarse con huella")
                        }
                    }
                }
            }
        } else {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(attendance.empleado, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Código ${attendance.codigo} · ${attendance.fecha}")
                    Text("Entrada: ${attendance.hora_entrada?.take(5) ?: "Pendiente"}")
                    Text("Salida: ${attendance.hora_salida?.take(5) ?: "Pendiente"}")
                    Spacer(Modifier.height(4.dp))
                    when {
                        attendance.hora_entrada == null -> Button(onClick = { onMark("entrada") }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Registrar Entrada ahora") }
                        attendance.hora_salida == null -> Button(onClick = { onMark("salida") }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Registrar Salida ahora") }
                        else -> Text("La asistencia de hoy está completa.", fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onReset, enabled = !loading) { Text("Cambiar de empleado") }
                }
            }
        }

        if (loading) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
            Text("Procesando…")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        fingerprintPrompt?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
            if (loading) TextButton(onClick = onCancelFingerprint) { Text("Cancelar lectura") }
        }
        if (queuedCount > 0) {
            Text("$queuedCount marca(s) pendiente(s) de sincronizar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onSync, enabled = !loading) { Text("Reintentar sincronización") }
        }
    }
}
