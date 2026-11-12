package com.jomiro.app.orobest_rrhh.screens

import androidx.compose.runtime.Composable

@Composable
expect fun AttendanceQrScannerButton(onScanned: (String) -> Unit)
