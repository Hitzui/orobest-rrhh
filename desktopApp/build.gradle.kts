import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
