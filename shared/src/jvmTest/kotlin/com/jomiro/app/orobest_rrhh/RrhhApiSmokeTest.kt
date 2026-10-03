package com.jomiro.app.orobest_rrhh

import com.jomiro.app.orobest_rrhh.network.RrhhApiClient
import com.jomiro.app.orobest_rrhh.network.configureRrhhClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RrhhApiSmokeTest {
    @Test
    fun bearerTokenListsEmployeesOnlyFromAssignedAgency() = runBlocking {
        val token = System.getenv("OROBEST_RRHH_TOKEN")?.trim().orEmpty()
        if (token.isBlank()) return@runBlocking

        val baseUrl = System.getenv("OROBEST_RRHH_BASE_URL")
            ?: "http://localhost/orobest/api/rrhh"
        val client = RrhhApiClient(
            baseUrl,
            token,
            configureRrhhClient(HttpClient(OkHttp)),
        )
        try {
            val employees = client.getEmployees()
            assertFalse(employees.isEmpty(), "La cuenta de supervisor debe ver empleados de su agencia.")
            assertTrue(employees.all { it.idagencia == 1 }, "La respuesta debe quedar limitada a la agencia 1.")
            val attendance = client.getAttendance(employees.first().idempleado)
            assertTrue(attendance.idempleado == employees.first().idempleado)
        } finally {
            client.close()
        }
    }
}
