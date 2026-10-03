package com.jomiro.app.orobest_rrhh.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val usuario: LoginUserDto,
)

@Serializable
data class LoginUserDto(
    val username: String,
    val nombre: String? = null,
)

@Serializable
data class EmployeeDto(
    val idempleado: Int,
    val codigo: String,
    val nombres: String,
    val apellidos: String,
    val estado: Int,
    val idagencia: Int,
    val agencia_descripcion: String? = null,
)

@Serializable
data class AttendanceDto(
    val idempleado: Int,
    val codigo: String,
    val empleado: String,
    val fecha: String,
    val hora_entrada: String? = null,
    val hora_salida: String? = null,
    val registrada: Boolean,
)

@Serializable
data class AttendanceEventRequest(
    val evento_id: String,
    val id: Int,
    val tipo: String,
    val ocurrido_en: String,
)

@Serializable
data class LeaveRequest(
    val id: Int,
    val fecha: String,
    val tipo: String,
    val dias: Double = 1.0,
    val motivo: String,
    val observaciones: String? = null,
)
