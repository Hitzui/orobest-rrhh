package com.jomiro.app.orobest_rrhh.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin)

internal actual fun defaultRrhhApiBaseUrl(): String =
    "http://localhost/orobest/api/rrhh"
