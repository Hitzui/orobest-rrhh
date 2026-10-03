package com.jomiro.app.orobest_rrhh

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "OroBest RRHH",
    ) {
        App()
    }
}
