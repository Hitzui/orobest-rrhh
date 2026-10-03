package com.jomiro.app.orobest_rrhh.network

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

@Serializable
data class QueuedAttendanceEvent(
    val request: AttendanceEventRequest,
    val status: String = STATUS_QUEUED,
    val detail: String? = null,
    val queuedAtEpochMillis: Long = 0,
    val server: String = "",
) {
    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_REVIEW = "review"
    }
}

data class AttendanceSyncResult(
    val sent: Int,
    val waiting: Int,
    val needsReview: Int,
    val connectionError: String? = null,
)

/** Durable outbox. Event payloads are immutable once enqueued so every retry uses the same UUID and timestamp. */
class AttendanceOutbox(
    private val settings: Settings = Settings(),
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(): List<QueuedAttendanceEvent> = mutex.withLock { readAll() }

    suspend fun enqueue(server: String, request: AttendanceEventRequest) = mutex.withLock {
        val key = keyFor(request.evento_id)
        if (!settings.hasKey(key)) {
            val nextOrder = maxOf(
                Clock.System.now().toEpochMilliseconds(),
                (readAll().maxOfOrNull { it.queuedAtEpochMillis } ?: 0L) + 1L,
            )
            settings.putString(
                key,
                json.encodeToString(
                    QueuedAttendanceEvent(
                        request = request,
                        queuedAtEpochMillis = nextOrder,
                        server = server.trim().trimEnd('/'),
                    ),
                ),
            )
        }
    }

    suspend fun synchronize(api: RrhhApiClient, server: String): AttendanceSyncResult = mutex.withLock {
        var sent = 0
        var connectionError: String? = null
        val blockedAttendanceDays = mutableSetOf<String>()
        val targetServer = server.trim().trimEnd('/')

        for (event in readAll()) {
            if (event.server != targetServer) continue
            val attendanceDay = "${event.request.id}:${event.request.ocurrido_en.substringBefore('T')}"
            if (event.status == QueuedAttendanceEvent.STATUS_REVIEW) {
                if (event.request.tipo == "entrada") blockedAttendanceDays += attendanceDay
                continue
            }
            if (event.request.tipo == "salida" && attendanceDay in blockedAttendanceDays) continue
            try {
                api.recordAttendance(event.request)
                settings.remove(keyFor(event.request.evento_id))
                sent++
            } catch (error: RrhhApiException) {
                if (error.statusCode == 401) {
                    connectionError = "La sesión venció. Inicia sesión para sincronizar las marcas pendientes."
                    break
                }
                if (error.statusCode in 400..499) {
                    settings.putString(
                        keyFor(event.request.evento_id),
                        json.encodeToString(event.copy(status = QueuedAttendanceEvent.STATUS_REVIEW, detail = error.message)),
                    )
                    if (event.request.tipo == "entrada") blockedAttendanceDays += attendanceDay
                    continue
                }
                connectionError = error.message
                break
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                connectionError = error.message ?: "Sin conexión. La marca quedó guardada en este dispositivo."
                break
            }
        }

        val remaining = readAll()
        val targetRemaining = remaining.filter { it.server == targetServer }
        AttendanceSyncResult(
            sent = sent,
            waiting = targetRemaining.count { it.status == QueuedAttendanceEvent.STATUS_QUEUED },
            needsReview = targetRemaining.count { it.status == QueuedAttendanceEvent.STATUS_REVIEW },
            connectionError = connectionError,
        )
    }

    private fun readAll(): List<QueuedAttendanceEvent> = settings.keys
        .filter { it.startsWith(KEY_PREFIX) }
        .mapNotNull { key -> runCatching { json.decodeFromString<QueuedAttendanceEvent>(settings.getString(key, "")) }.getOrNull() }
        .sortedBy { it.queuedAtEpochMillis }

    private fun keyFor(eventId: String) = KEY_PREFIX + eventId

    private companion object {
        const val KEY_PREFIX = "attendance_outbox_"
    }
}
