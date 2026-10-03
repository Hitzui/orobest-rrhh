package com.jomiro.app.orobest_rrhh.network

import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.HttpClient

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp)

internal actual fun defaultRrhhApiBaseUrl(): String =
    "http://10.0.2.2/orobest/api/rrhh"
