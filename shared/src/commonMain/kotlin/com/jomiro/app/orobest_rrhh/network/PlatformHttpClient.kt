package com.jomiro.app.orobest_rrhh.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal expect fun createPlatformHttpClient(): HttpClient

internal expect fun defaultRrhhApiBaseUrl(): String

internal fun configureRrhhClient(client: HttpClient): HttpClient = client.config {
    expectSuccess = false
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        })
    }
}
