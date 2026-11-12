package com.jomiro.app.orobest_rrhh.biometric

import com.jomiro.app.orobest_rrhh.network.FingerprintTemplateDto

/** Platform biometric adapter; the U.are.U implementation is available only on Windows Desktop. */
expect object FingerprintPlatform {
    val isAvailable: Boolean
    suspend fun enroll(onPrompt: (String) -> Unit): String
    suspend fun identify(templates: List<FingerprintTemplateDto>, onPrompt: (String) -> Unit): Int?
    fun reportFailure(error: Throwable): String
}
