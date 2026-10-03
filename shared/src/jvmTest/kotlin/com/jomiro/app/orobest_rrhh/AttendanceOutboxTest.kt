package com.jomiro.app.orobest_rrhh

import com.jomiro.app.orobest_rrhh.network.AttendanceEventRequest
import com.jomiro.app.orobest_rrhh.network.AttendanceOutbox
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceOutboxTest {
    @Test
    fun eventsSurviveOutboxRecreationAndKeepCaptureOrder() = runBlocking {
        val settings = MapSettings()
        val first = AttendanceEventRequest(
            evento_id = "entry-event-uuid",
            id = 42,
            tipo = "entrada",
            ocurrido_en = "2026-10-03T08:00:00-06:00",
        )
        val second = AttendanceEventRequest(
            evento_id = "exit-event-uuid",
            id = 42,
            tipo = "salida",
            ocurrido_en = "2026-10-03T17:00:00-06:00",
        )

        AttendanceOutbox(settings).apply {
            enqueue("https://rrhh.example.test/api/rrhh", first)
            enqueue("https://rrhh.example.test/api/rrhh", second)
        }

        val restoredEvents = AttendanceOutbox(settings).list()
        assertEquals(listOf(first, second), restoredEvents.map { it.request })
        assertEquals(listOf("entrada", "salida"), restoredEvents.map { it.request.tipo })
        assertEquals(setOf("https://rrhh.example.test/api/rrhh"), restoredEvents.map { it.server }.toSet())
    }
}
