package com.jomiro.app.orobest_rrhh.di

import com.jomiro.app.orobest_rrhh.network.AttendanceOutbox
import com.jomiro.app.orobest_rrhh.network.RrhhApiClientFactory
import com.russhwolf.settings.Settings
import org.koin.dsl.module

val appModule = module {
    single<Settings> { Settings() }
    single { AttendanceOutbox(get()) }
    single { RrhhApiClientFactory() }
}
