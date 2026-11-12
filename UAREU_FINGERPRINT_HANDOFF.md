# Contexto: lector U.are.U en OroBest RRHH

## Objetivo

Evaluar e integrar el lector U.are.U en la aplicación existente OroBest RRHH para evitar instalar una aplicación adicional. La conversación continúa en este proyecto (`E:\Mobil\orobest-rrhh`). No se ha implementado todavía.

## SDK y ejemplos disponibles

- Librerías: `C:\Huella\Windows\Lib\Java`
- Ejemplo de enrolamiento: `C:\Huella\Windows\Samples\UareUSampleJava\Enrollment.java`
- Ejemplo de identificación: `C:\Huella\Windows\Samples\UareUSampleJava\Identification.java`
- El proyecto RRHH es Kotlin Multiplatform con targets Android, iOS y Desktop (JVM).
- Los ejemplos Java usan U.are.U y producen/comparan plantillas FMD. Enrollment captura varias muestras del mismo dedo y crea un FMD; Identification compara la captura con una colección de FMDs. El ejemplo usa formato ANSI 378-2004.

## Consideraciones para continuar

- Las librerías indicadas son las de Windows/Java. No asumir que funcionan en Android o iOS; confirmar SDK, drivers y compatibilidad del lector por plataforma antes de comprometer esos targets.
- Si el lector solo se usará conectado a una PC Windows, implementar la integración en `jvmMain` y conectarla desde la UI Desktop de RRHH permitiría tener una sola app instalada para ese uso.
- Enrolamiento/identificación usan plantillas biométricas FMD; no son equivalentes a guardar una imagen PNG. Definir propósito, consentimiento, custodia, almacenamiento, retención y acceso antes de persistir plantillas.
- No copiar librerías/DLLs ni plantillas biométricas al repositorio sin revisar licencia, redistribución y política de datos.
- No cambiar la app OroBest Sign ni su alcance PNG-only como parte de esta tarea.

## Estado al preparar este traspaso

- No se han copiado SDKs, plantillas ni ejemplos al repositorio.
- No se ha modificado la implementación. El árbol de trabajo RRHH ya tenía cambios locales; preservarlos y revisar el `git status` antes de editar archivos.
- Leer `KMP_APP_HANDOFF.md` y las instrucciones del proyecto antes de implementar. Ese archivo ya tenía cambios locales al iniciar esta sesión y no se sobrescribió.
