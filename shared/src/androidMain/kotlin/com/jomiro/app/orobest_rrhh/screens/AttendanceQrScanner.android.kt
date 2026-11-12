package com.jomiro.app.orobest_rrhh.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
actual fun AttendanceQrScannerButton(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    Button(onClick = {
        GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(onScanned) }
    }) { Text("Escanear credencial QR") }
}
