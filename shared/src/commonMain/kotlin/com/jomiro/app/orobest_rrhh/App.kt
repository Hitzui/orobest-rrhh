package com.jomiro.app.orobest_rrhh

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.ui.theme.ComposablesTheme
import com.jomiro.app.orobest_rrhh.di.appModule
import com.jomiro.app.orobest_rrhh.navigation.AppNavigation
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

private val OroBestColors = lightColorScheme(
    primary = Color(0xFF5143A5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE9FF),
    onPrimaryContainer = Color(0xFF28205E),
    secondary = Color(0xFF5C6072),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9EAF2),
    onSecondaryContainer = Color(0xFF252838),
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF1B1B22),
    surface = Color(0xFFFFFEFF),
    onSurface = Color(0xFF1B1B22),
    surfaceVariant = Color(0xFFEDECF3),
    onSurfaceVariant = Color(0xFF62616D),
    outline = Color(0xFF797783),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val OroBestShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun App() {
    KoinApplication(configuration = koinConfiguration { modules(appModule) }) {
        ComposablesTheme {
            MaterialTheme(
                colorScheme = OroBestColors,
                shapes = OroBestShapes,
                typography = Typography(),
            ) {
                AppNavigation()
            }
        }
    }
}
