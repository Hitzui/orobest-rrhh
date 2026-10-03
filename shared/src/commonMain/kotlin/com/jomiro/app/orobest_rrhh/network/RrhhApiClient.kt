package com.jomiro.app.orobest_rrhh.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RrhhApiException(val statusCode: Int, detail: String) : RuntimeException(detail)

private val apiErrorJson = Json { ignoreUnknownKeys = true }

class RrhhApiClient(
    baseUrl: String,
    private val bearerToken: String,
    client: HttpClient? = null,
) {
    private val baseUrl = baseUrl.trim().trimEnd('/')
    private val client = client ?: configureRrhhClient(createPlatformHttpClient())

    suspend fun login(username: String, password: String): LoginResponse {
        val response = client.post("$baseUrl/login") {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }
        return response.readData()
    }

    suspend fun getEmployees(): List<EmployeeDto> {
        val response = client.get("$baseUrl/empleados") { authorized() }
        return response.readData<List<EmployeeDto>>()
    }

    suspend fun getAttendance(employeeId: Int, date: String? = null): AttendanceDto {
        val response = client.get("$baseUrl/asistencia") {
            authorized()
            parameter("id", employeeId)
            if (!date.isNullOrBlank()) parameter("fecha", date)
        }
        return response.readData()
    }

    suspend fun recordAttendance(request: AttendanceEventRequest): String {
        val response = client.post("$baseUrl/asistencia") {
            authorized()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.readMessage()
    }

    suspend fun submitLeaveRequest(request: LeaveRequest): String {
        val response = client.post("$baseUrl/solicitudes") {
            authorized()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.readMessage()
    }

    suspend fun logout() {
        val response = client.post("$baseUrl/logout") { authorized() }
        response.readMessage()
    }

    fun close() = client.close()

    private fun io.ktor.client.request.HttpRequestBuilder.authorized() {
        header(HttpHeaders.Authorization, "Bearer $bearerToken")
        accept(ContentType.Application.Json)
    }

    private suspend inline fun <reified T> HttpResponse.readData(): T {
        if (statusCode !in 200..299) {
            throw RrhhApiException(statusCode, errorDetail())
        }
        val envelope = body<ApiEnvelope<T>>()
        if (!envelope.success) {
            throw RrhhApiException(statusCode, envelope.message ?: "La API rechazó la solicitud.")
        }
        return envelope.data ?: throw RrhhApiException(statusCode, "La API devolvió una respuesta sin datos.")
    }

    private suspend fun HttpResponse.readMessage(): String {
        if (statusCode !in 200..299) {
            throw RrhhApiException(statusCode, errorDetail())
        }
        val response = body<ApiEnvelopeMessage>()
        if (!response.success) {
            throw RrhhApiException(statusCode, response.message ?: "La API rechazó la solicitud.")
        }
        return response.message.orEmpty()
    }

    private val HttpResponse.statusCode: Int
        get() = status.value

    private suspend fun HttpResponse.errorDetail(): String {
        val bodyText = runCatching { bodyAsText() }.getOrDefault("")
        val serverMessage = runCatching {
            apiErrorJson.decodeFromString<ApiErrorResponse>(bodyText).message
        }.getOrNull()
        return serverMessage?.takeIf { it.isNotBlank() }
            ?: bodyText.take(300).ifBlank { "La API rechazó la solicitud." }
    }

}

@Serializable
private data class ApiErrorResponse(val message: String? = null)

@kotlinx.serialization.Serializable
private data class ApiEnvelopeMessage(val success: Boolean, val message: String? = null)
