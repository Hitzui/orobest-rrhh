package com.jomiro.app.orobest_rrhh.biometric

import java.io.File
import kotlinx.coroutines.runBlocking

internal object UareuRuntime {
    @Volatile private var loaded = false

    fun directory(): File = File(
        System.getProperty("uareu.dll.dir")?.takeIf(String::isNotBlank)
            ?: System.getenv("UAREU_DLL_DIR")?.takeIf(String::isNotBlank)
            ?: ".local/uareu/native-x64",
    ).absoluteFile

    @Synchronized fun load() {
        if (loaded) return
        check(System.getProperty("os.arch").let { it == "amd64" || it == "x86_64" }) {
            "U.are.U requiere Java Windows x64 y las DLL x64 del mismo SDK."
        }
        val dir = directory()
        val required = listOf("dpfj.dll", "dpfpdd.dll", "dpfpdd_4k.dll", "dpuareu_jni.dll", "nex_sdk.dll")
        val missing = required.filterNot { File(dir, it).isFile }
        check(missing.isEmpty()) { "Runtime U.are.U incompleto en $dir. Faltan: ${missing.joinToString()}." }
        // Dpfpdd calls loadLibrary itself: configure java.library.path at JVM startup.
        listOf("nex_sdk.dll", "dpfj.dll", "dpfpdd.dll", "dpuareu_jni.dll").forEach {
            System.load(File(dir, it).absolutePath)
        }
        loaded = true
    }
}

/** Run :desktopApp:checkFingerprintReader; never captures or persists a fingerprint. */
object UareuDiagnostics {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        println("Runtime U.are.U: ${UareuRuntime.directory()}")
        println(FingerprintPlatform.checkReader())
    }
}
