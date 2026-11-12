package com.jomiro.app.orobest_rrhh.biometric

import com.digitalpersona.uareu.Engine
import com.digitalpersona.uareu.Fid
import com.digitalpersona.uareu.Fmd
import com.digitalpersona.uareu.Reader
import com.digitalpersona.uareu.UareUException
import com.digitalpersona.uareu.UareUGlobal
import com.jomiro.app.orobest_rrhh.network.FingerprintTemplateDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Base64
import kotlin.coroutines.CoroutineContext

actual object FingerprintPlatform {
    private const val FORMAT = "ANSI_378_2004"
    // UareUGlobal owns a process-wide collection and enrollment engine.
    private val readerMutex = Mutex()
    actual val isAvailable: Boolean
        get() = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    actual suspend fun enroll(onPrompt: (String) -> Unit): String = withContext(Dispatchers.IO) {
        readerMutex.withLock {
            val context = currentCoroutineContext()
            withReader { reader ->
                val engine = UareUGlobal.GetEngine()
                var samples = 0
                val fmd = createEnrollment(engine) { format ->
                        require(format == Fmd.Format.ANSI_378_2004) { "Formato FMD no compatible." }
                        while (true) {
                            context.ensureActive()
                            onPrompt(if (samples == 0) "Coloque un dedo en el lector." else "Retire y vuelva a colocar el MISMO dedo. Muestras aceptadas: $samples.")
                            val image = capture(reader, context, onPrompt)
                            val extracted = try {
                                engine.CreateFmd(image, format)
                            } catch (error: UareUException) {
                                if (error.code != UareUException.URU_E_TOO_SMALL_AREA && error.code != UareUException.URU_E_INVALID_FID) throw error
                                onPrompt("No se pudieron extraer suficientes detalles. Retire y vuelva a colocar el mismo dedo.")
                                continue
                            }
                            samples++
                            onPrompt("Muestra $samples aceptada. Levante el dedo.")
                            return@createEnrollment Engine.PreEnrollmentFmd().apply { fmd = extracted; view_index = 0 }
                        }
                        @Suppress("UNREACHABLE_CODE")
                        error("Enrolamiento interrumpido.")
                } ?: error("Enrolamiento cancelado: no se obtuvieron suficientes muestras.")
                context.ensureActive()
                Base64.getEncoder().encodeToString(fmd.data)
            }
        }
    }

    actual suspend fun identify(
        templates: List<FingerprintTemplateDto>,
        onPrompt: (String) -> Unit,
    ): Int? = withContext(Dispatchers.IO) {
        val gallery = decodeFingerprintTemplates(templates)
        readerMutex.withLock {
            val context = currentCoroutineContext()
            withReader { reader ->
                val importer = UareUGlobal.GetImporter()
                val fmds = gallery.map { (_, bytes) ->
                    importer.ImportFmd(bytes, Fmd.Format.ANSI_378_2004, Fmd.Format.ANSI_378_2004)
                }.toTypedArray()
                onPrompt("Coloque el dedo en el lector para identificarse.")
                val engine = UareUGlobal.GetEngine()
                val probe = engine.CreateFmd(capture(reader, context, onPrompt), Fmd.Format.ANSI_378_2004)
                context.ensureActive()
                val candidates = engine.Identify(probe, 0, fmds, Engine.PROBABILITY_ONE / 100000, 1)
                candidates.firstOrNull()?.let { gallery[it.fmd_index].first }
            }
        }
    }

    // Enumerates and opens a reader without capturing biometric data.
    internal suspend fun checkReader(): String = withContext(Dispatchers.IO) {
        readerMutex.withLock { withReader { "Lector disponible. Estado: ${it.GetStatus().status}" } }
    }

    actual fun reportFailure(error: Throwable): String {
        // No template bytes, API payloads or credentials in diagnostics.
        val detail = when (error) {
            is UareUException -> "Error U.are.U 0x${error.code.toUInt().toString(16)}. Cierre otros programas que usen el lector y compruebe el cable USB."
            is UnsatisfiedLinkError -> "No se pudieron cargar las DLL U.are.U. Reinicie con la ruta nativa configurada al arrancar Java y el runtime x64 completo (incluido nex_sdk.dll)."
            else -> error.message ?: "No se pudo completar la operación del lector."
        }
        System.err.println("U.are.U: ${error::class.simpleName}: $detail")
        return detail
    }

    private fun <T> withReader(block: (Reader) -> T): T {
        check(isAvailable) { "El lector U.are.U solo está habilitado en Windows Desktop." }
        UareuRuntime.load()
        val collection = UareUGlobal.GetReaderCollection()
        try {
            collection.GetReaders()
            val readers = collection.toList()
            val configured = System.getProperty("uareu.reader.name")?.takeIf(String::isNotBlank)
            val reader = if (configured != null) {
                readers.firstOrNull { it.GetDescription().name == configured }
                    ?: error("No se encontró el lector configurado. Compruebe uareu.reader.name.")
            } else {
                check(readers.size <= 1) { "Hay varios lectores conectados. Conecte solo el lector de asistencia o configure uareu.reader.name." }
                readers.firstOrNull() ?: error("No se detectó un lector U.are.U. Conecte el 4500, cierre el ejemplo Java y vuelva a intentar.")
            }
            reader.Open(Reader.Priority.COOPERATIVE)
            return try { block(reader) } finally { reader.Close() }
        } finally {
            UareUGlobal.DestroyReaderCollection()
        }
    }

    private fun capture(reader: Reader, context: CoroutineContext, onPrompt: (String) -> Unit): Fid {
        val resolution = reader.GetCapabilities().resolutions.firstOrNull()
            ?: error("El lector no informó una resolución compatible.")
        val deadline = System.nanoTime() + 60_000_000_000L
        var poorCaptures = 0
        while (System.nanoTime() < deadline) {
            context.ensureActive()
            when (val status = reader.GetStatus().status) {
                Reader.ReaderStatus.BUSY -> { Thread.sleep(100); continue }
                Reader.ReaderStatus.READY, Reader.ReaderStatus.NEED_CALIBRATION -> Unit
                else -> error("El lector no está listo ($status). Reconecte el USB e intente nuevamente.")
            }
            // Finite native waits let cancellation release the reader without a finger.
            val result = reader.Capture(Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT, resolution, 1000)
            context.ensureActive()
            when (result.quality) {
                Reader.CaptureQuality.GOOD -> result.image?.let { return it }
                Reader.CaptureQuality.TIMED_OUT, Reader.CaptureQuality.NO_FINGER -> continue
                Reader.CaptureQuality.CANCELED -> error("Captura cancelada por el lector. Intente nuevamente.")
                else -> {
                    poorCaptures++
                    onPrompt("No se obtuvo una huella clara (${result.quality}). Retire y vuelva a colocar el mismo dedo.")
                    check(poorCaptures < 8) { "No se logró una captura de buena calidad. Limpie el lector e intente nuevamente." }
                }
            }
        }
        error("Se agotó el tiempo de espera del lector. Intente nuevamente.")
    }
}

internal fun decodeFingerprintTemplates(templates: List<FingerprintTemplateDto>): List<Pair<Int, ByteArray>> {
    val compatible = templates.filter { it.formato == "ANSI_378_2004" }
    require(compatible.isNotEmpty()) { "No hay huellas ANSI 378-2004 enroladas para esta sucursal." }
    return compatible.map { template ->
        require(template.idempleado > 0) { "La galería de huellas contiene un empleado inválido." }
        val bytes = try {
            require(template.fmd.length <= 56000)
            Base64.getDecoder().decode(template.fmd)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("La plantilla del empleado ${template.idempleado} no contiene Base64 válido.")
        }
        require(bytes.size in 32..40000) { "La plantilla del empleado ${template.idempleado} tiene un tamaño inválido." }
        template.idempleado to bytes
    }
}

// SDK 3.2 only catches UareUException around the callback. Throwing a coroutine
// cancellation directly would skip finish_enrollment and poison the next operation.
internal fun createEnrollment(engine: Engine, nextSample: (Fmd.Format) -> Engine.PreEnrollmentFmd?): Fmd? {
    var failure: Throwable? = null
    val enrolled = engine.CreateEnrollmentFmd(Fmd.Format.ANSI_378_2004, object : Engine.EnrollmentCallback {
        override fun GetFmd(format: Fmd.Format): Engine.PreEnrollmentFmd? = try {
            nextSample(format)
        } catch (error: Throwable) {
            failure = error
            null // Let the SDK finish enrollment before propagating the failure.
        }
    })
    failure?.let { throw it }
    return enrolled
}
