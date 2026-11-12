package com.jomiro.app.orobest_rrhh.biometric

import com.jomiro.app.orobest_rrhh.network.FingerprintTemplateDto

actual object FingerprintPlatform {
    actual val isAvailable: Boolean = false
    actual suspend fun enroll(onPrompt: (String) -> Unit): String =
        error("El lector U.are.U está disponible únicamente en Desktop Windows.")
    actual suspend fun identify(templates: List<FingerprintTemplateDto>, onPrompt: (String) -> Unit): Int? =
        error("El lector U.are.U está disponible únicamente en Desktop Windows.")
    actual fun reportFailure(error: Throwable): String = error.message ?: "Error del lector de huella."
}
