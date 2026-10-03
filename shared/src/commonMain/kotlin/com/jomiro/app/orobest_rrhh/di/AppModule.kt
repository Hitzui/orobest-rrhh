package com.jomiro.app.orobest_rrhh.di

import com.jomiro.app.orobest_rrhh.network.AttendanceOutbox
import com.jomiro.app.orobest_rrhh.network.RrhhApiClientFactory
import com.jomiro.app.orobest_rrhh.session.RrhhSessionRepository
import com.jomiro.app.orobest_rrhh.viewmodel.AttendanceViewModel
import com.jomiro.app.orobest_rrhh.viewmodel.ConfiguracionViewModel
import com.jomiro.app.orobest_rrhh.viewmodel.EmployeesViewModel
import com.jomiro.app.orobest_rrhh.viewmodel.HomeViewModel
import com.jomiro.app.orobest_rrhh.viewmodel.LoginViewModel
import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<Settings> { Settings() }
    single { AttendanceOutbox(get()) }
    single { RrhhApiClientFactory() }
    single { RrhhSessionRepository(get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { EmployeesViewModel(get()) }
    viewModel { AttendanceViewModel(get(), get()) }
    viewModel { ConfiguracionViewModel(get()) }
}
