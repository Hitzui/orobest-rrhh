import java.io.File
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val installedUareuDllDir = "C:/Program Files/DigitalPersona/U.are.U SDK/Windows/Lib/x64"
val legacyUareuDllDir = "C:/Huella/Windows/Lib/x64"
val localUareuDllDir = rootProject.file(".local/uareu/native-x64").absolutePath
val defaultUareuDllDir = when {
    file("$localUareuDllDir/dpuareu_jni.dll").isFile -> localUareuDllDir
    file("$installedUareuDllDir/dpuareu_jni.dll").isFile -> installedUareuDllDir
    else -> legacyUareuDllDir
}
val uareuDllDir = providers.gradleProperty("uareuDllDir")
    .orElse(providers.environmentVariable("UAREU_DLL_DIR"))
    .orElse(defaultUareuDllDir)
val javaNativeLibraryPath = uareuDllDir.get().replace('\\', '/')

// Compose's Gradle `run` task launches a JavaExec process. Keep direct Gradle runs
// aligned with the native distribution's JVM startup options.
tasks.withType<JavaExec>().configureEach {
    systemProperty("uareu.dll.dir", uareuDllDir.get())
    systemProperty("java.library.path", javaNativeLibraryPath)
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        val pathKey = environment.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        environment(pathKey, uareuDllDir.get() + File.pathSeparator + environment[pathKey]?.toString().orEmpty())
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.jomiro.app.orobest_rrhh.MainKt"
        jvmArgs += "-Duareu.dll.dir=${uareuDllDir.get()}"
        // Must be a JVM startup option: the JVM caches native search paths before main() runs.
        jvmArgs += "-Djava.library.path=$javaNativeLibraryPath"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "OroBest RRHH"
            packageVersion = "1.0.0"
            description = "Gestión de personal y asistencia"
            macOS {
                iconFile.set(project.file("src/main/resources/icons/orobest-rrhh.icns"))
                dockName = "OroBest RRHH"
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/orobest-rrhh.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icons/orobest-rrhh.png"))
            }
        }
    }
}

// Open the configured reader without capturing a fingerprint or calling the API.
tasks.register<JavaExec>("checkFingerprintReader") {
    group = "verification"
    description = "Verifica las DLL U.are.U y abre el lector sin capturar huellas."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.jomiro.app.orobest_rrhh.biometric.UareuDiagnostics")
}
