# U.are.U fingerprint deployment notes

## Scope

- Fingerprint capture and matching run only in the Desktop JVM process on Windows with the U.are.U 4500 SDK/driver installed.
- The SDK creates ANSI 378-2004 FMD templates. Fingerprint images are not persisted.
- The API stores an encrypted FMD per employee. An authenticated attendance session downloads the active agency's templates to identify locally; template bytes remain in process memory and are not written to the workstation disk by this feature.
- Only accounts with the existing `rrhh` or `administrador` RBAC roles see enrollment/revocation controls. Attendance identification is also exposed to the existing mobile attendance permission.
- Enrollment requires the RRHH operator to confirm the employee's informed consent. Disabling an employee removes their template; explicit revocation deletes it as well.

## API server setup

1. Back up the database and deploy the backend changes.
2. Apply migration `m261018_000039_create_rrhh_empleado_huella_table`.
3. Configure `RRHH_BIOMETRIC_KEY` in the PHP server environment with a stable, secret key of at least 32 characters. For example, generate a value with `php -r "echo bin2hex(random_bytes(32)), PHP_EOL;"`. Store it in the hosting secret/configuration manager, not source control. Do not rotate it without first decrypting and re-encrypting stored templates.
4. Ensure API traffic uses HTTPS. The API encrypts templates at rest and only serves templates for the authenticated user's active agency.

## Windows Desktop: configuración probada con SDK 3.2.0.89

El U.are.U 4500 se usa desde Windows Desktop x64 (JVM). Android/iOS conservan sus alternativas PIN/QR; estas DLL de Windows no funcionan en esos targets.

Desde la raíz del proyecto, prepare una copia local del SDK oficial que ya posee:

```powershell
.\tools\Setup-Uareu.ps1 -SdkRoot "E:\JoseMiguel\OneDrive\Documents\OROBEST SISTEMA DOC\U.are.U_Windows_3.2.0.89"
.\gradlew.bat :desktopApp:checkFingerprintReader
.\gradlew.bat :desktopApp:run
```

- El script copia el JAR y las DLL x64 del mismo SDK a `.local/uareu/`, ignorado por Git. Extrae `nex_sdk.dll` y sus archivos de datos/licencia del `RTE/x64/Data1.cab` original. Tener solo `dpuareu_jni.dll` y `dpfpdd.dll` no basta: `dpfpdd.dll` depende de `nex_sdk.dll`.
- Gradle utiliza primero esa copia local. Puede cambiar las rutas con `-PuareuSdkJar=...` y `-PuareuDllDir=...` o `UAREU_SDK_JAR` / `UAREU_DLL_DIR`. Ambas deben apuntar al mismo SDK 3.2 x64 y a un runtime completo.
- Java recibe `java.library.path` al arrancar. La app precarga las dependencias por ruta absoluta; no intenta corregir una ruta JVM almacenada en caché después del arranque. Para ejecutar desde una configuración propia del IDE, use esas mismas opciones de JVM o ejecute la tarea Gradle `:desktopApp:run`.
- Cierre el ejemplo Java y otros programas de huellas al usar la app. Con más de un lector, conecte solo uno o configure `-Duareu.reader.name=<nombre exacto>` en el proceso Java.
- `checkFingerprintReader` enumera y abre el lector, muestra su estado y lo libera. No captura huellas ni llama a la API. Debe indicar `READY` antes de probar el flujo de empleados.
- Los binarios se usan localmente y no se agregan al repositorio. Esta configuración de desarrollo usa una ruta absoluta de la estación. Antes de generar/distribuir un MSI hay que preparar el runtime para el equipo destino y revisar la EULA y los avisos de redistribución del SDK.

## Registro e identificación en la app

1. Inicie sesión con una cuenta que tenga permiso `rrhh` o `administrador` para enrolar. En **Empleados → Huella**, confirme el consentimiento, pulse **Capturar y enrolar** y coloque varias veces el mismo dedo siguiendo las indicaciones. El SDK decide cuántas muestras acepta y genera una plantilla ANSI 378-2004.
2. Solo se confirma el enrolamiento después de que `PUT /huella?id=...` responda correctamente. No se guardan imágenes ni plantillas en disco. El servidor ya incluye cifrado de plantillas, permisos y separación por agencia.
3. En **Asistencia → Identificarse con huella**, se descargan las plantillas autorizadas de la agencia y se comparan localmente con `Engine.Identify`, con el mismo umbral del ejemplo (1/100000). Una huella desconocida no selecciona empleado ni genera marcas.
4. Una coincidencia muestra el empleado y sus marcas actuales. Pulse **Registrar Entrada ahora** o **Registrar Salida ahora**. Se conserva la cola existente y la confirmación de la API; identificar por sí solo no registra asistencia.
5. Puede cancelar la captura. Al salir de la pantalla o cerrar la sesión se cancela la identificación; las esperas nativas tienen un límite de un segundo y cada captura espera como máximo un minuto. Solo se permite una operación del lector a la vez.
6. La identificación requiere conexión para descargar plantillas y consultar la asistencia. PIN/QR y la cola de marcaciones existentes siguen disponibles; no se agrega una caché biométrica offline.

Los endpoints locales revisados ya existen en `C:/xampp/htdocs/orobest/controllers/ApiRrhhController.php`. Esto no demuestra que los cambios estén desplegados en producción. Antes de validar contra otro servidor, compruebe la migración, `RRHH_BIOMETRIC_KEY` y los permisos indicados arriba.

## Initial validation

1. Test with a non-production employee and documented consent.
2. Enroll the same finger following all prompts; confirm the FMD is saved and encrypted in the API database.
3. Verify Desktop attendance recognizes the enrolled finger and rejects an unenrolled finger; test PIN/QR fallback.
4. Revoke the template and verify it can no longer identify the employee. Deactivate another test employee and confirm their template is automatically deleted.
5. Do not enroll real employees until the organization's biometric notice/consent and retention process has been approved and communicated.
