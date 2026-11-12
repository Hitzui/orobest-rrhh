# Traspaso de contexto: app móvil OroBest (Kotlin Multiplatform)

Este documento permite continuar el trabajo de la app móvil en otro directorio o en una conversación nueva. Leerlo junto con `docs/Plan_de_trabajo_RRHH.txt` antes de modificar la API o implementar flujos móviles.

## Repositorios y carpetas

- Backend web/API Yii2: `C:\xampp\htdocs\orobest`
- Proyecto KMP: `E:\Mobil\orobest-rrhh` (repositorio independiente, ya existente).
- Mantener la app como proyecto/repo independiente. La app consume la API HTTP del backend; no copiar clases PHP ni acceder directamente a MySQL desde Kotlin.
- Si el nuevo chat se abre en la carpeta KMP, pedirle que lea este documento. La copia del backend está en `C:\xampp\htdocs\orobest\docs\KMP_APP_HANDOFF.md`; el backend se puede consultar bajo `C:\xampp\htdocs\orobest\...`.
- El proyecto tiene targets Android, iOS y Desktop y usa Compose Multiplatform.
- El branding del icono parte de `assets/branding/orobest-rrhh-icon.svg`; Desktop usa `.ico`, `.icns` y `.png` para MSI, DMG y DEB.

## Alcance inicial

La app es para el rol `supervisor_movil` y debe permitir:

1. Autenticarse con un token Bearer emitido por administración.
2. Consultar empleados activos de la agencia del usuario.
3. Consultar asistencia de un empleado por fecha.
4. Registrar entrada y salida, incluyendo operación sin conexión y sincronización posterior.
5. Enviar solicitudes de permisos/ausencias para revisión de RRHH.

El supervisor móvil no edita empleados, horarios, salarios ni planillas; no clasifica faltas injustificadas ni aprueba solicitudes. Las decisiones de acceso y la agencia permitida se aplican en el backend, no en la UI.

## API existente

URL predeterminada de producción: `https://orobestni.com/api/rrhh`. El formulario de inicio de sesión permite cambiarla para desarrollo y pruebas.

Todas las rutas están debajo de `/api/rrhh` y requieren:

```http
Authorization: Bearer <token>
Accept: application/json
Content-Type: application/json
```

### Inicio y cierre de sesión móvil

```http
POST /api/rrhh/login
Content-Type: application/json
```

```json
{"username":"supervisor","password":"..."}
```

El servidor valida la cuenta activa, la contraseña y la asignación explícita del rol `supervisor_movil`. Responde `401` con credenciales incorrectas y `403` cuando la cuenta no tiene el rol o agencia activa requeridos. Con éxito devuelve `data.access_token`; la app lo conserva solo en memoria y lo usa como Bearer. El token se almacena como SHA-256 en el backend.

```http
POST /api/rrhh/logout
Authorization: Bearer <token>
```

Revoca el token de esa sesión.

### Listar empleados

```http
GET /api/rrhh/empleados
GET /api/rrhh/empleados?id=123
```

Respuesta: `{ "success": true, "data": [...] }`. Incluye identidad operativa básica (ID, código, nombres, apellidos, estado y agencia). El servidor limita los resultados a empleados activos en la agencia asignada al usuario.

### Consultar asistencia

```http
GET /api/rrhh/asistencia?id=123&fecha=2026-10-02
```

`fecha` es opcional y por defecto se consulta hoy. Respuesta dentro de `data`: `idempleado`, `codigo`, `empleado`, `fecha`, `hora_entrada`, `hora_salida` y `registrada`.

### Registrar entrada/salida

```http
POST /api/rrhh/asistencia
```

```json
{
  "evento_id": "UUID",
  "id": 123,
  "tipo": "entrada",
  "ocurrido_en": "2026-10-02T08:00:00-06:00"
}
```

- `tipo`: `entrada` o `salida`.
- `evento_id`: UUID v4 u otro UUID válido, único por evento físico.
- `ocurrido_en`: fecha/hora ISO-8601 con zona horaria explícita; nunca enviar una hora local ambigua.
- Para reintentar un evento offline se conserva exactamente el UUID y el contenido original.
- Un reintento idéntico es idempotente; no genera una segunda marca. Reutilizar un UUID con contenido distinto o intentar reemplazar una entrada/salida existente puede responder HTTP `409`; el evento conflictivo requiere revisión de RRHH y no sobrescribe la asistencia existente.
- El servidor convierte la fecha/hora del evento a `America/Managua`, guarda el instante UTC y la hora de recepción. Rechaza eventos más de cinco minutos en el futuro.
- Persistir los eventos pendientes hasta recibir confirmación inequívoca. No generar un UUID nuevo por reintento.

### Identificación para marcación compartida

- La pantalla de marcación acepta PIN numérico de seis dígitos o QR de tarjeta (Android). La API valida la credencial y devuelve el nombre/código y las marcas actuales; PIN y QR son alternativas, no se requieren juntos.
- `POST /api/rrhh/credencial` recibe `{"pin":"123456"}` o `{"qr":"orobest-attendance:v1:<token>"}`. Se requiere la sesión Bearer de supervisor móvil; hay límite temporal de intentos.
- El QR contiene un token aleatorio y no datos personales. RRHH/administra PIN y QR desde el expediente web de empleado; solo se muestran al generarlos y regenerar revoca ambos anteriores. El PIN no se imprime en la tarjeta.
- Las marcas online envían `usar_hora_servidor:true`; la API fija el instante al recibir el evento. Las marcas en cola offline conservan el instante del dispositivo. Para marcar offline, primero se debe haber identificado al empleado mientras había conexión y mantener la app abierta.
- Escaneo de tarjeta está implementado para Android mediante Google Code Scanner; el target iOS aún no tiene lector QR.
- La app mantiene una cola durable local en Android SharedPreferences, iOS UserDefaults y Desktop Java Preferences. Cada marca guarda el servidor de origen, orden de captura y payload original; se elimina solo después de una respuesta exitosa. HTTP 4xx queda en revisión con el mensaje del servidor, 401 conserva la cola y una salida espera si la entrada del mismo día fue rechazada.

### Enviar solicitud de permiso/ausencia

```http
POST /api/rrhh/solicitudes
```

```json
{
  "id": 123,
  "fecha": "2026-10-05",
  "tipo": "permiso_medico",
  "dias": 1,
  "motivo": "Consulta médica",
  "observaciones": "Opcional"
}
```

Tipos admitidos: `permiso_medico`, `permiso_personal`, `incapacidad` y `justificada`, correspondientes a las constantes actuales de `models/Ausencia.php`. `dias` debe estar entre `0.01` y `1` por fecha. La fecha debe ser laborable y programada, y no puede ser feriado. Las solicitudes quedan Pendientes; RRHH decide y aplica el tratamiento.

## Autenticación y preparación de cuenta

- No hay endpoint móvil de inicio de sesión/renovación: la autenticación de API usa token Bearer dedicado.
- El token claro se muestra una sola vez al emitirlo. La base guarda SHA-256; no se debe guardar en el repositorio, logs, capturas ni analytics.
- Para emitirlo: elegir/crear un usuario activo, asignarle una agencia activa y ejecutar desde el backend `php yii api-rrhh/activar <idusuario>`. El comando asigna el rol `supervisor_movil` si hace falta y muestra el token una vez.
- Para revocarlo: `php yii api-rrhh/revocar <idusuario>`.
- Cuenta de pruebas activa: `gvado`, ID 11, agencia 1 (Bello Horizonte). Se le asignó `supervisor_movil` y conserva el rol previo `caja`.
- Hay un token activo de pruebas en `rrhh_token_gvado_11.txt`, ignorado por Git y con ACL restringida al usuario local/SYSTEM/Administradores. El token anterior fue revocado durante la puesta a punto; no copiar el token actual a código, assets, logs ni documentación.
- La API no implementa por ahora endpoint de perfil/quién-soy. La app puede mostrar el nombre de usuario solo si se añade un endpoint autorizado; no inferir permisos localmente.

## Decisiones técnicas recomendadas para KMP

- Compartir en `commonMain`: modelos DTO, cliente HTTP, manejo de errores, repositorios y cola/sincronización de eventos.
- La inyección de dependencias multiplataforma usa Koin 4.2.2 en `commonMain`. `App()` inicializa `KoinApplication`; `di/AppModule.kt` registra Settings, AttendanceOutbox, RrhhApiClientFactory, RrhhSessionRepository y ViewModels por pantalla: Login, Home, Empleados, Asistencia y Configuración. `AppNavigation` observa la sesión compartida y cada pantalla usa `koinViewModel()` y su propio `StateFlow`.
- Usar Ktor Client y Kotlinx Serialization salvo que el proyecto ya tenga una convención distinta.
- No persistir el token en preferencias planas. Usar almacenamiento seguro nativo (Android Keystore/encrypted storage e iOS Keychain mediante una abstracción multiplataforma).
- La app usa `https://orobestni.com/api/rrhh` como URL predeterminada y permite cambiarla desde el formulario de inicio de sesión. No desactivar validación TLS en producción.
- Para cola offline, guardar como mínimo UUID, empleado, tipo, timestamp ISO-8601, estado de envío y respuesta/conflicto del servidor. Reintentos con backoff, sin alterar el evento original.
- Manejar 401 (token ausente/inválido/revocado), 403 (cuenta/agencia/permisos), 404 (empleado no visible en esa agencia), 409 (conflicto que debe escalarse) y errores de validación HTTP 4xx. No convertir errores en confirmaciones de marca.
- Para usar un backend local en emulador Android, el host del PC suele ser `10.0.2.2`; en dispositivo físico se usa la IP LAN del PC, misma red y firewall habilitado. La URL predeterminada en producción es el dominio HTTPS OroBest.

## Próxima secuencia de trabajo

1. Navegación Compose con Login (usuario/contraseña) y barra inferior Inicio, Empleados, Asistencia y Configuración. Empleados muestra datos básicos, búsqueda, acceso directo a marcar asistencia y formulario de solicitud de permiso. Asistencia permite aplicar entrada/salida pendientes; marcas existentes de solo lectura en móvil, editables desde web. Las solicitudes quedan pendientes para RRHH/administración en la web. Configuración muestra cuenta/servidor y permite cerrar sesión. El token permanece en memoria.
2. Prueba JVM de integración pasó con el token activo y el motor OkHttp usado por Android: `GET empleados` y `GET asistencia`; se comprobó que los empleados devueltos pertenecen a agencia 1.
3. APK debug compilado e instalado/lanzado en el emulador Pixel 9 API 34. La consulta autenticada desde el formulario visual todavía queda por repetir; no se registró ninguna marca ni solicitud.
4. Validar offline: registrar sin red, cerrar/reabrir la app, reconectar, revisar que se conserve el UUID y que conflictos 409 no sobrescriban marcas.
5. Implementar una indicación de conectividad y sincronización automática al recuperar red; actualmente hay sincronización al iniciar sesión y reintento manual desde Asistencia.
6. Mantener el token fuera de Git y cambiar a almacenamiento seguro nativo antes de persistir sesiones en la app.

## Archivos del backend para consultar

- `controllers/ApiRrhhController.php`: contratos y reglas de los endpoints.
- `components/RrhhBearerAuth.php`: validación de Bearer tokens.
- `commands/ApiRrhhController.php`: emisión/revocación de tokens.
- `models/RrhhApiToken.php` y `models/RrhhAsistenciaEvento.php`: persistencia de credenciales/eventos.
- `docs/Plan_de_trabajo_RRHH.txt`: decisiones RRHH, estado y riesgos.
- `E:\Mobil\orobest-rrhh\shared\src\commonMain\kotlin\com\jomiro\app\orobest_rrhh\network`: cliente Ktor y DTOs compartidos de API.

Al retomar, inspeccionar estos archivos en su estado actual antes de asumir que los contratos no cambiaron.
