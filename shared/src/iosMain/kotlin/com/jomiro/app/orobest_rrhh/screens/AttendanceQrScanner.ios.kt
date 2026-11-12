package com.jomiro.app.orobest_rrhh.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
actual fun AttendanceQrScannerButton(onScanned: (String) -> Unit) {
    Button(onClick = {}, enabled = false) { Text("Escaneo QR disponible en Android") }
}
