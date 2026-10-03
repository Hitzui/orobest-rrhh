package com.jomiro.app.orobest_rrhh.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.orobest_rrhh.session.RrhhSessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(val employeeCount: Int = 0)

class HomeViewModel(
    sessionRepository: RrhhSessionRepository,
) : ViewModel() {
    val uiState = sessionRepository.state
        .map { HomeUiState(employeeCount = it.employees.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
