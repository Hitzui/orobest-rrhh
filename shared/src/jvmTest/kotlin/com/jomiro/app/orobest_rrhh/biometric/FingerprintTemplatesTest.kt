package com.jomiro.app.orobest_rrhh.biometric

import com.jomiro.app.orobest_rrhh.network.FingerprintTemplateDto
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FingerprintTemplatesTest {
    private fun template(id: Int, bytes: ByteArray = ByteArray(64) { it.toByte() }) =
        FingerprintTemplateDto(id, "ANSI_378_2004", Base64.getEncoder().encodeToString(bytes))

    @Test fun candidateIndicesStillPointToTheCorrectEmployeeAfterFilteringFormats() {
        val gallery = decodeFingerprintTemplates(listOf(
            FingerprintTemplateDto(99, "OTHER", "not-a-template"), template(17), template(42),
        ))
        assertEquals(listOf(17, 42), gallery.map { it.first })
        assertContentEquals(ByteArray(64) { it.toByte() }, gallery[1].second)
    }

    @Test fun emptyOrIncompatibleGalleryFailsBeforeOpeningReader() {
        assertFailsWith<IllegalArgumentException> { decodeFingerprintTemplates(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            decodeFingerprintTemplates(listOf(FingerprintTemplateDto(1, "OTHER", "")))
        }
    }

    @Test fun malformedTemplateRejectsTheWholeGalleryWithoutLeakingPayload() {
        val secret = "private-template-%%%"
        val error = assertFailsWith<IllegalArgumentException> {
            decodeFingerprintTemplates(listOf(template(17), FingerprintTemplateDto(42, "ANSI_378_2004", secret)))
        }
        assertFalse(error.message.orEmpty().contains(secret))
    }

    @Test fun rejectsTruncatedOrOversizedTemplatesAndInvalidEmployeeIds() {
        for (bytes in listOf(ByteArray(0), ByteArray(31), ByteArray(40001), ByteArray(43000))) {
            assertFailsWith<IllegalArgumentException> { decodeFingerprintTemplates(listOf(template(1, bytes))) }
        }
        assertFailsWith<IllegalArgumentException> { decodeFingerprintTemplates(listOf(template(0))) }
    }
}
