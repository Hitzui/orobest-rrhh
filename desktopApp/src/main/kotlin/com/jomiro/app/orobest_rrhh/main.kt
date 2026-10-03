package com.jomiro.app.orobest_rrhh

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import javax.imageio.ImageIO

fun main() = application {
    val appIcon = remember {
        val image = requireNotNull(object {}.javaClass.getResourceAsStream("/icons/orobest-rrhh.png"))
            .use { ImageIO.read(it) }
        BitmapPainter(image.toComposeImageBitmap())
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "OroBest RRHH",
        icon = appIcon,
        state = WindowState(WindowPlacement.Maximized, isMinimized = false),
    ) {
        App()
    }
}
