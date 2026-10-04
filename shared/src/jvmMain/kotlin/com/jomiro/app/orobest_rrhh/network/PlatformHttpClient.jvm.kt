package com.jomiro.app.orobest_rrhh.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(CIO)

internal actual fun defaultRrhhApiBaseUrl(): String =
    "https://orobestni.com/api/rrhh"
