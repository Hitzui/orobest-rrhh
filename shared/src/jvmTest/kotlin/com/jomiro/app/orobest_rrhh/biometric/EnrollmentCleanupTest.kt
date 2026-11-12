package com.jomiro.app.orobest_rrhh.biometric

import com.digitalpersona.uareu.Engine
import com.digitalpersona.uareu.Fmd
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EnrollmentCleanupTest {
    @Test fun cancellationReturnsNullToSdkBeforeRethrowingSoEnrollmentCanFinish() {
        var finished = false
        // Mirrors SDK 3.2: finish is skipped if the callback throws unchecked exceptions.
        val engine = Proxy.newProxyInstance(Engine::class.java.classLoader, arrayOf(Engine::class.java)) { _, method, args ->
            check(method.name == "CreateEnrollmentFmd")
            val callback = args!![1] as Engine.EnrollmentCallback
            assertNull(callback.GetFmd(Fmd.Format.ANSI_378_2004))
            finished = true
            null
        } as Engine
        val cancellation = CancellationException("cancelled")
        val thrown = assertFailsWith<CancellationException> {
            createEnrollment(engine) { throw cancellation }
        }
        assertTrue(finished)
        assertSame(cancellation, thrown)
    }
}
