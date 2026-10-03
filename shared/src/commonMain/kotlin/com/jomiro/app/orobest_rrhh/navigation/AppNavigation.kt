package com.jomiro.app.orobest_rrhh.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.composables.icons.lucide.ClipboardClock
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.UsersRound
import com.jomiro.app.orobest_rrhh.screens.AsistenciaScreen
import com.jomiro.app.orobest_rrhh.screens.ConfiguracionScreen
import com.jomiro.app.orobest_rrhh.screens.EmpleadosScreen
import com.jomiro.app.orobest_rrhh.screens.HomeScreen
import com.jomiro.app.orobest_rrhh.screens.LoginScreen
import com.jomiro.app.orobest_rrhh.viewmodel.RrhhAppViewModel
import org.koin.compose.viewmodel.koinViewModel

private enum class AppDestination(val label: String) {
    Home("Inicio"),
    Empleados("Empleados"),
    Asistencia("Asistencia"),
    Configuracion("Configuración"),
}

private val NavigationPurple = Color(0xFF5143A5)
private val NavigationSelected = Color(0xFF7164C5)
private val NavigationMuted = Color(0xFFB8AEF4)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppNavigation(
    viewModel: RrhhAppViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var destination by remember { mutableStateOf(AppDestination.Home) }

    if (!state.authenticated) {
        LoginScreen(
            initialBaseUrl = state.apiBaseUrl,
            loading = state.loading,
            error = state.loginError,
            onLogin = viewModel::login,
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(destination.label, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = NavigationPurple,
                    shadowElevation = 16.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(76.dp).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppDestination.entries.forEach { item ->
                            FloatingNavigationItem(
                                destination = item,
                                selected = destination == item,
                                onClick = { destination = item },
                            )
                        }
                    }
                }
            }
        },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            when (destination) {
                AppDestination.Home -> HomeScreen(
                    employeeCount = state.employees.size,
                    onOpenEmployees = { destination = AppDestination.Empleados },
                    onOpenAttendance = { destination = AppDestination.Asistencia },
                )

                AppDestination.Empleados -> EmpleadosScreen(
                    employees = state.employees,
                    onRefresh = viewModel::refreshEmployees,
                    refreshing = state.loading,
                    error = state.employeesError,
                    onMarkAttendance = { employeeId ->
                        destination = AppDestination.Asistencia
                        viewModel.consultAttendance(employeeId)
                    },
                    onSubmitLeaveRequest = viewModel::submitLeaveRequest,
                )

                AppDestination.Asistencia -> AsistenciaScreen(
                    employees = state.employees,
                    attendance = state.attendance,
                    selectedEmployeeId = state.attendanceEmployeeId,
                    loading = state.loading,
                    error = state.attendanceError,
                    queuedEvents = state.queuedAttendanceEvents,
                    currentServer = state.apiBaseUrl,
                    syncResult = state.attendanceSyncResult,
                    syncing = state.syncingAttendance,
                    onConsult = viewModel::consultAttendance,
                    onSyncPending = viewModel::syncPendingAttendance,
                    onDismissDialog = viewModel::dismissAttendance,
                    onApplyMarks = viewModel::applyAttendance,
                )

                AppDestination.Configuracion -> ConfiguracionScreen(
                    username = state.signedInUsername,
                    apiBaseUrl = state.apiBaseUrl,
                    onLogout = viewModel::logout,
                )
            }
        }
    }
}

@Composable
private fun RowScope.FloatingNavigationItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) NavigationSelected else Color.Transparent,
        label = "navigation-item-background",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else NavigationMuted,
        label = "navigation-item-tint",
    )
    val icon = when (destination) {
        AppDestination.Home -> Lucide.House
        AppDestination.Empleados -> Lucide.UsersRound
        AppDestination.Asistencia -> Lucide.ClipboardClock
        AppDestination.Configuracion -> Lucide.Settings
    }

    Row(
        modifier = Modifier
            .weight(if (selected) 2f else 1f)
            .fillMaxHeight()
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .clickable(onClick = onClick)
            .animateContentSize(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = destination.label, tint = tint, modifier = Modifier.size(22.dp))
        if (selected) {
            Spacer(Modifier.size(7.dp))
            Text(
                text = destination.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
