# LibroMpaz — Libro de clases digital (prototipo académico)

## Integrantes del equipo

- **César Salvo** (Dueño del repo) — Configuración de Base 3, firma del APK, keystore `.jks` y entrega en AVA.
- **Matías Pérez** — Configuración de JaCoCo, alcance de lógica, pruebas del repositorio local, asistencia, sesiones, calificaciones y hoja de vida.
- **Katerine** — Pruebas del ViewModel y de dominio, recursos nativos (Vibración y DatePicker), y documentación README.

Aplicación Android (Kotlin, Jetpack Compose Material 3, MVVM, Room) que simula el libro de clases digital de un colegio básico. Es un prototipo de uso interno: todos los datos son ficticios y el flujo es aislado: no se envía ni se exporta información fuera del dispositivo o, en modo API, fuera de la red local del aula (RN-01, RNF-07).

> **Estado de la entrega EV3:** el profesor dejó la **API REST pendiente por falta de ambiente backend**. Por eso el modo evaluado es el **modo local con Room**, que la rúbrica acepta como equivalente. Los endpoints y el modo servidor se documentan abajo como contrato previsto, no como una integración funcionando en esta entrega.

La app trae dos modos que se cambian **en tiempo de ejecución**, sin recompilar, con el interruptor "Modo local (sin servidor)" del cuadro **Servidor** (botón en el login):

| Modo | Qué hace |
|---|---|
| Modo local | Todo se guarda en Room (`libro_mpaz_local.db`), sin red, con las mismas reglas de dominio. Funciona completo hoy, sin servidor. **Modo usado en la evaluación EV3.** |
| Modo servidor | Contrato declarado en `ApiService.kt`: el servidor local del aula sería la fuente de verdad y Room (`libro_mpaz.db`) la copia local. Su integración queda pendiente por falta de ambiente backend. |

**`USAR_API` solo define el valor inicial** del interruptor (`false` = parte en modo local, `true` = parte en modo servidor). Una vez que alguien elige un modo en la app, esa preferencia queda guardada en el dispositivo y manda sobre `USAR_API`. Se lee en cada operación: el cambio es inmediato, pero cierra la sesión actual.

Franja visible: bajo la barra superior siempre se muestra "Modo local" o "Modo servidor". En esta entrega se demuestra **"Modo local"**; no se demuestra modo servidor porque no hay backend disponible.

**Datos independientes, sin sincronización.** Los datos creados en modo local viven en su propia base y **no se envían** al servidor al volver a modo servidor (ni al revés). La acción "Restablecer datos locales" del cuadro Servidor (con confirmación) borra la base local y vuelve a cargar los datos de ejemplo. Si se activa el modo servidor sin la integración completa, la app avisa en pantalla y permite volver a modo local.

## Integrantes

| Integrante | Aporte en esta entrega | Contacto |
|---|---|---|
| César Salvo | Base 3, inyección de dependencias del ViewModel, firma del APK, `.jks` y AVA | `ces.salvo@duocuc.cl` · GitHub `fanfisco666` |
| Matías Pérez | Configuración de JaCoCo, pruebas del repositorio local y del ViewModel | `mn.perez@duocuc.cl` · GitHub `OMatinov` |
| Katerine Olmedo | Pruebas del ViewModel y de dominio, vacíos de cobertura y README final | `ka.olmedo@duocuc.cl` |

## Caso

El colegio necesita registrar asistencia, contenidos curriculares (OA), calificaciones y anotaciones de la hoja de vida, con acceso restringido por perfil y con un registro de auditoría inalterable. Cada perfil ve solo lo que le corresponde.

## Perfiles y usuarios de prueba

| Perfil | Usuario | Clave | Qué puede hacer |
|---|---|---|---|
| Docente (Matemática) | `docente.mat@libromp.cl` | `docente123` | Ver sus cursos; asistencia, clases con OA, notas y anotaciones solo en sus cargas |
| Docente (Lenguaje) | `docente.len@libromp.cl` | `docente123` | Igual, con sus propias cargas |
| UTP | `utp@libromp.cl` | `utp123` | Solo lectura: cobertura curricular y revisión de calificaciones |
| Inspectoría | `inspectoria@libromp.cl` | `inspectoria123` | Asistencia de todos los cursos (consultar y gestionar), alertas y hoja de vida |
| Dirección | `direccion@libromp.cl` | `direccion123` | Solo lectura: panel global y auditoría |

Las claves se guardan como hash SHA-256 con sal fija de demostración (no son aptas para producción). El servidor de la Experiencia 3 usa los mismos usuarios.

## Requerimientos

| RF | Descripción | Estado (modo local) | Dónde |
|---|---|---|---|
| RF-01 | Login real y cursos asignados | Implementado | `LoginScreen`, `MisCursosScreen`, `LibroMpazViewModel.ingresar/misCargas` |
| RF-02 | Control de acceso por rol (RBAC) | Implementado | `domain/PoliticaAcceso`, `AppNavigation.protegida`, verificación en cada operación del ViewModel |
| RF-03 | Registrar asistencia diaria | Implementado | `AsistenciaScreen`, `guardarAsistencia`, `domain/RegistrosAsistencia` |
| RF-04 | Asistencia en pocos pasos | Implementado | Todos parten presentes, se tocan solo las excepciones, "Todos presentes" |
| RF-05 | Inspectoría gestiona asistencia general | Implementado | `PanelInspectoriaScreen`, `porcentajesAsistencia` |
| RF-06 | Asociar OA y contenidos a la sesión | Implementado | `RegistroClaseScreen`, `guardarSesion`, `validarSesion` |
| RF-07 | UTP revisa cobertura curricular | Implementado | `CoberturaCurricularScreen`, `oasTrabajados`, lectura en voz alta |
| RF-08 | Calificaciones por instrumento y fecha | Implementado | `CalificacionesScreen`, `crearEvaluacion`, `guardarCalificaciones` |
| RF-09 | UTP detecta desfases en notas | Implementado | `RevisionCalificacionesScreen`, `calcularDesfases` |
| RF-10 | Anotaciones en la hoja de vida | Implementado | `HojaDeVidaScreen`, `registrarAnotacion` |
| RF-11 | Inspectoría detecta riesgos | Implementado | `AlertasInspectoriaScreen`, umbrales en `domain/Metricas` |
| RF-12 | Dirección ve el estado global | Implementado | `PanelDireccionScreen`, `resumenCursos` |
| RF-13 | Consultar la auditoría | Implementado | `AuditoriaScreen`, tabla `auditoria` sin actualizar ni borrar |

## Endpoints declarados (contrato REST)

Los siguientes endpoints están **declarados en** `app/src/main/java/com/librompaz/app/data/remote/ApiService.kt`. **No se ejecutan en esta entrega**: la API REST quedó pendiente por falta de ambiente backend. Se listan para dejar registrado el contrato; el flujo evaluado es el modo local con Room.

| Método | Endpoint | Uso previsto |
|---|---|---|
| `GET` | `/health` | Comprobar que el servidor local responde |
| `POST` | `/auth/login` | Ingresar con correo y clave |
| `POST` | `/auth/logout` | Cerrar sesión en el servidor |
| `GET` | `/me` | Datos del usuario autenticado |
| `GET` | `/cursos` | Lista de cursos |
| `GET` | `/asignaturas` | Lista de asignaturas |
| `GET` | `/objetivos?asignaturaId=` | Objetivos de aprendizaje por asignatura |
| `GET` | `/estudiantes?cursoId=` | Estudiantes por curso |
| `GET` | `/cargas?docenteId=` | Cargas del docente (RBAC) |
| `GET` | `/asistencias?cursoId=&fecha=` | Asistencia de un curso en una fecha |
| `PUT` | `/cursos/{cursoId}/asistencia/{fecha}` | Guardar o corregir asistencia |
| `GET` | `/sesiones?cursoId=&asignaturaId=` | Clases registradas |
| `POST` | `/sesiones` | Registrar clase con OA y contenidos |
| `GET` | `/evaluaciones?cursoId=&asignaturaId=` | Instrumentos de evaluación |
| `POST` | `/evaluaciones` | Crear evaluación |
| `DELETE` | `/evaluaciones/{id}` | Eliminar evaluación y sus notas |
| `GET` | `/calificaciones?evaluacionId=` | Notas de una evaluación |
| `PUT` | `/evaluaciones/{id}/calificaciones` | Guardar lote de notas |
| `GET` | `/anotaciones?estudianteId=&tipo=` | Hoja de vida filtrada |
| `POST` | `/anotaciones` | Registrar anotación firmada |
| `GET` | `/auditoria?accion=&perfil=&limite=` | Consultar auditoría (solo lectura) |

Reglas de negocio relevantes: RN-03 (docente solo opera en sus cargas), RN-04 (notas de 1,0 a 7,0 con un decimal), RN-05 (una asistencia por estudiante y fecha, índice único e id determinista), RN-06 (anotaciones sin editar ni eliminar: su DAO no tiene `@Update` ni `@Delete`), RN-07 (alertas con 3 o más ausencias y 2 o más anotaciones negativas), RN-08 (una nota por estudiante y evaluación, solo de estudiantes del curso).

Porcentaje de asistencia: (presentes + atrasados) / total de registros x 100, redondeado; sin registros se muestra "—".

## Recursos nativos y permisos

- Texto a voz (`TextToSpeech`): botón "Escuchar resumen" en la cobertura curricular.
- Vibración (permiso `VIBRATE`): respuesta háptica ante errores y confirmaciones.
- Red (`INTERNET` y `ACCESS_NETWORK_STATE`): solo para hablar con el servidor local del aula en modo API.

No se usan cámara, micrófono, ubicación, biometría, notificaciones ni intercambio de archivos.

## Arquitectura

```
com.librompaz.app
├── model/        Entidades Room y tipos (Perfil, Usuario, Curso, Estudiante, Asistencia, Sesion, Evaluacion, Anotacion, Auditoría)
├── data/
│   ├── remote/       ApiService (Retrofit) y Dtos (completos: son el contrato con la API); ApiClient, Interceptores, ConfigServidor,
│   │                 ApiResult, ErroresApi, Mapeadores, FuenteRemota y ServidorPreferencias (con TODO)
│   ├── local/        AppDatabase, DAOs, Converters, CacheLocal/CacheRoom (reemplazo atómico de la copia local)
│   ├── repository/   LibroMpazDatos (contrato único), SelectorFuente (elige local o servidor por operación), PreferenciaModo, LibroMpazRepository (modo local, completo) y LibroMpazApiRepository (modo servidor, con TODO)
│   └── MockData.kt   Datos ficticios iniciales (los mismos de la semilla del servidor)
├── domain/       Reglas puras y testeables: PoliticaAcceso, Validaciones, Metricas, ClaveSegura, RegistrosAsistencia
├── viewmodel/    LibroMpazViewModel (StateFlow, un solo ViewModel compartido; elige la fuente de datos en cada operación con `SelectorFuente`, según el modo guardado)
├── ui/
│   ├── theme/ components/ (campos con validación, avisos, selectores, cuadro "Servidor", banner de conexión)
│   └── screen/   comun, docente, utp, inspectoria, direccion
├── navigation/   AppNavigation (un NavHost, rutas protegidas por perfil)
├── util/         Fechas, Dispositivo (vibración y voz), Conectividad
└── MainActivity.kt
```

Flujo en modo local: la interfaz observa `StateFlow` del ViewModel; el ViewModel valida con `domain`, comprueba permisos con `PoliticaAcceso` y delega la escritura a `LibroMpazRepository`, que guarda y audita en la misma transacción Room.

Flujo en modo API:

- Lectura: API -> `FuenteRemota` -> el repositorio reemplaza la copia local en Room -> la interfaz observa los `Flow` de Room.
- Escritura: ViewModel (valida y autoriza) -> `LibroMpazApiRepository` -> `FuenteRemota` -> API. Si el servidor acepta, el repositorio vuelve a leer y actualiza Room; si rechaza, se muestra su mensaje; si no hay red, no se guarda nada y se avisa.

## Cómo ejecutar

**Modo local (como se entrega).**

1. Abrir la carpeta `LibroMpaz` con Android Studio (JDK 17, Android SDK 35).
2. Sincronizar Gradle y ejecutar el módulo `app` en un emulador o dispositivo (minSdk 24).
3. Iniciar sesión con alguno de los usuarios de la tabla. La base se puebla sola la primera vez.

**Modo API (Experiencia 3).**

1. Completar los `TODO(E3-n)` (tabla más abajo; en Android Studio: View > Tool Windows > TODO).
2. Descomprimir el servidor (archivo `_Backend.zip` que se entrega aparte) y levantarlo en el computador: `cd backend && npm install && npm start`. Escucha en el puerto 3000.
3. Ejecutar la app y, en el login, abrir **Servidor** y desactivar "Modo local (sin servidor)" (o poner `USAR_API=true` en `gradle.properties` para que parta en modo servidor en una instalación nueva).
4. Dirección del servidor: en el emulador, `http://10.0.2.2:3000/` (valor por defecto del build); en un teléfono, `http://<IP-del-computador>:3000/` desde el botón **Servidor** del login (Probar conexión > Guardar); por USB, `adb reverse tcp:3000 tcp:3000` y `http://localhost:3000/`.

### Regla de aislamiento (RN-01 y RNF-07): solo red local

El caso exige un flujo aislado: asistencia, notas y hoja de vida de menores no pueden salir de la red del aula. Por eso:

- Solo se usa el emulador (`10.0.2.2`) o la red del aula (IP privada del computador). Está **prohibido** el reenvío público de puertos (por ejemplo, Port Forwarding con visibilidad pública de VS Code), los túneles de internet y publicar el servidor en la nube.
- La app debe rechazar cualquier dirección que no sea local (TODO E3-3, E3-4, E3-5 y E3-17), y el servidor responde 403 a los reenvíos con encabezados de proxy.
- Los permisos de red son solo `INTERNET` y `ACCESS_NETWORK_STATE`; no se agrega ningún SDK de terceros, analítica ni mensajería.
- En la presentación hay que demostrar que no hay tráfico a hosts externos (ver el enunciado).

## Lo que se completa en la Experiencia 3 (TODO)

Cada `TODO(E3-n)` en el código dice qué hacer, en qué archivo y con qué criterio de aceptación. En modo local ningún TODO se ejecuta: la app sigue funcionando completa. Si se activa el modo servidor antes de completarlos, la app lo avisa en pantalla (no se cae) y se puede volver a modo local.

| TODO | Archivo | Qué hacer | Criterio de aceptación |
|---|---|---|---|
| E3-1 | `data/remote/ApiClient.kt` | Crear el OkHttpClient (timeouts 8 s / 15 s / 15 s; interceptores en orden RedLocal, Token, depuración) y un Retrofit con Gson por cada URL base. | Un host no local lanza IOException sin abrir conexión; un host local lleva Authorization Bearer; la misma URL devuelve la misma instancia. |
| E3-2 | `data/remote/Interceptores.kt (TokenInterceptor)` | Agregar «Authorization: Bearer <token>» cuando hay sesión y la petición no trae ya el encabezado. | Con token «abc» el servidor recibe «Bearer abc»; sin token no recibe el encabezado; el logout no lo duplica. |
| E3-3 | `data/remote/Interceptores.kt (RedLocalInterceptor)` | RN-01 y RNF-07: lanzar IOException si el host de la petición no es local, antes de abrir ninguna conexión. | https://api.ejemplo.com falla con requestCount = 0 en el servidor simulado; 10.0.2.2, localhost y 192.168.x.x pasan. |
| E3-4 | `data/remote/ConfigServidor.kt (esHostLocal)` | Devolver true solo para localhost, 127.x, 10.x, 172.16 a 172.31, 192.168.x, 169.254.x, IPv6 local y nombres internos (sin punto, .local, .lan, .internal). | true: localhost, 10.0.2.2, 172.31.255.255, 192.168.1.10, ::1, aula-pc. false: 8.8.8.8, 172.32.0.1, 192.169.0.1, 300.1.1.1, 192.168.1, www.google.com. |
| E3-5 | `data/remote/ConfigServidor.kt (validarUrl)` | Validar y normalizar la URL escrita con los mensajes de la clase; rechazar todo lo que no sea red local. | «10.0.2.2:3000» da «http://10.0.2.2:3000/»; «https://api.ejemplo.com» da MENSAJE_NO_LOCAL; vacía, ftp://, puerto 99999 y rutas dan su mensaje. |
| E3-6 | `data/remote/ApiResult.kt` | Completar mapear, obtenerOSalir y mensajeDeFallo de ApiResult. | Exito(2).mapear { it * 3 } es Exito(6); un Error o SinConexion pasa sin cambios; mensajeDeFallo es null en un éxito. |
| E3-7 | `data/remote/ErroresApi.kt (cuerpo y códigos)` | parsearCuerpoDeError, mensajePorCodigo y traducirRespuestaHttp: textos en español y uso del mensaje y el campo que manda el servidor. | {"error":"…","campo":"bloque"} se conserva con su código; un cuerpo no JSON da null; 401 es MENSAJE_SESION_EXPIRADA. |
| E3-8 | `data/remote/ErroresApi.kt (llamarApi y probarServidor)` | Ejecutar la llamada y devolver Exito, Error (HTTP o cuerpo mal formado) o SinConexion (red o tiempo agotado); relanzar la CancellationException; probar /health. | 409 da Error con el mensaje del servidor; servidor apagado da SinConexion; timeout da MENSAJE_TIEMPO_AGOTADO; cancelar no produce ApiResult. |
| E3-9 | `data/remote/Mapeadores.kt` | Mapeadores DTO <-> entidad de todas las colecciones; valor de enum desconocido devuelve null. | entidad.aDto().aEntidad() es igual a la entidad; estado «XYZ» da null; el estado viaja como nombre del enum; la nota viaja como número. |
| E3-10 | `data/remote/FuenteRemota.kt (sesión y lecturas)` | Login, logout, descargar lo que el perfil puede leer (AlcanceLectura) y asistencia de un día. | Para UTP no se piden /asistencias, /anotaciones ni /auditoria; un fallo en cualquier colección devuelve el fallo y no una descarga parcial. |
| E3-11 | `data/remote/FuenteRemota.kt (escrituras)` | Las seis escrituras: asistencia, clase, evaluación (crear y eliminar), notas y anotación. | El JSON enviado coincide con el contrato declarado en `ApiService.kt` y `Dtos.kt`; no se envía firma, hora ni auditoría; 409 y 422 llegan como Error con el mensaje del servidor. |
| E3-12 | `data/repository/LibroMpazApiRepository.kt (ingresar)` | Login contra /auth/login: token en memoria, usuario con el hash de la clave en Room, ingreso sin red solo con credenciales ya usadas. | Con red deja token y usuario; sin red solo ingresa quien ya ingresó en ese equipo; la clave nunca se guarda en claro. |
| E3-13 | `data/repository/LibroMpazApiRepository.kt (sincronizar)` | Descargar con FuenteRemota y reemplazar la copia local de una vez (API fuente de verdad, Room caché). | Si la API falla, Room queda como estaba; si Room falla, «No se pudo actualizar la copia local de los datos.»; una sola llamada a reemplazar. |
| E3-14 | `data/repository/LibroMpazApiRepository.kt (tras)` | Escritura «API primero»: si el servidor acepta se sincroniza; si rechaza se devuelve su mensaje; sin red no se guarda nada. | 409 no cambia Room y muestra el mensaje del servidor; sin red el mensaje termina en «No se guardó nada.»; un éxito dispara una sincronización. |
| E3-15 | `data/repository/LibroMpazApiRepository.kt (asistenciaDe)` | Asistencia de un día desde la API y, si falla, desde Room. | Con el servidor apagado devuelve lo que hay en Room y no lanza excepción. |
| E3-16 | `data/remote/ServidorPreferencias.kt` | Guardar, leer y restablecer la URL del servidor en SharedPreferences; ignorar lo guardado si ya no es una dirección local válida. | Sin nada guardado devuelve la URL del build; lo guardado sobrevive al cierre de la app; una dirección pública guardada a mano se ignora. |
| E3-17 | `viewmodel/LibroMpazViewModel.kt (probarConexion y guardarServidor)` | Cuadro «Servidor»: validar la URL, probar /health sin guardarla y guardarla solo si es local. | Una dirección pública muestra el mensaje bajo el campo y no hace ninguna petición ni se guarda; con el servidor encendido dice «Conexión correcta». |

Ya vienen resueltos: `Dtos.kt` y `ApiService.kt` (el contrato de la API: no se modifican), `AlcanceLectura.kt`, `TokenSesion.kt`, `CacheLocal.kt` y `CacheRoom.kt`, el cuadro `ServidorDialog` y el banner `BannerSincronizacion`, el logging de OkHttp solo en debug, la configuración de red y el repositorio local.

## Cómo correr las pruebas

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat jacocoTestReport
.\gradlew.bat jacocoTestCoverageVerification
```

En Linux/macOS se usa `./gradlew` en lugar de `.\gradlew.bat`. Las pruebas JVM usan JUnit 4, MockK, MockWebServer y `kotlinx-coroutines-test`.

- Total en `app/src/test`: **229 `@Test`, 51 omitidas y 178 activas, con 0 fallos**. Las 51 omitidas vienen de 5 clases con `@Ignore` a nivel de clase, a la espera de la integración con la API (que quedó pendiente por falta de ambiente backend): `ApiServiceMockWebServerTest` (15), `ConfigServidorTest` (6), `ErroresApiTest` (11), `MapeadoresTest` (7) y `LibroMpazApiRepositoryTest` (12).
- ViewModel: `LibroMpazViewModelTest.kt` (21 casos de Matías) y `LibroMpazViewModelLocalTest.kt` (27 casos de Katerine: login con fallback sin red, RBAC, notas, lote atómico, `sinNota` y doble guardado).
- JaCoCo genera `app/build/reports/jacoco/jacocoTestReport/html/index.html` y `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`. La verificación exige al menos **80% de instrucciones** sobre `domain/`, `data/repository/`, `data/local/` y `viewmodel/`.
- `connectedDebugAndroidTest` sigue disponible para pruebas de interfaz con emulador o dispositivo; no es necesario para la evidencia JVM de esta entrega.

## Cómo generar el APK firmado

La entrega incluye la llave exclusiva de esta actividad (`llave/librompaz.jks`), el APK firmado (`apk/app-release.apk`) y la configuración de firma en `keystore.properties`. Los valores de esa configuración no se reproducen en esta documentación. Si no se definen los cuatro datos de firma, `assembleRelease` genera un APK sin firmar (no instalable).

**Con el asistente de Android Studio** (Build > Generate Signed App Bundle / APK > APK): elegir `llave/librompaz.jks`, variante `release` y marcar las firmas V1 y V2. El detalle paso a paso está en el enunciado de la actividad.

**Con Gradle:**

1. La llave ya está en el repositorio. Solo si hay que regenerarla: `keytool -genkeypair -v -keystore mi-llave.jks -alias mi_alias -keyalg RSA -keysize 2048 -validity 10000` (llave nueva y exclusiva, no reutilizar la de esta entrega en otra app).
2. La configuración ya está en `keystore.properties`: `storeFile` (ruta absoluta), `storePassword`, `keyAlias` y `keyPassword`. En otro equipo se parte de `keystore.properties.example` y se completan esos valores, o se usan `LIBROMPAZ_STORE_FILE`, `LIBROMPAZ_STORE_PASSWORD`, `LIBROMPAZ_KEY_ALIAS` y `LIBROMPAZ_KEY_PASSWORD` en `~/.gradle/gradle.properties` o como variables de entorno.
3. Ejecutar `.\gradlew.bat assembleRelease`. El APK queda en `app/build/outputs/apk/release/`; la copia entregada está en `apk/app-release.apk`.

Verificar la firma con `apksigner verify --verbose --print-certs --min-sdk-version 22 apk/app-release.apk` (debe decir que verificó V1 y V2; se baja el `minSdk` de la verificación porque con `minSdk = 24` `apksigner` solo comprueba el esquema V2 y marca V1 como `false`, aunque el APK esté firmado con ambos). `versionCode` y `versionName` están en `app/build.gradle.kts`; `.gitignore` excluye `local.properties`, `build/`, `.gradle/`, `.idea/` y `.kotlin/`. La llave, el APK y su configuración se incluyen porque la pauta lo pide; no reutilices esa llave en otra app ni publiques sus valores.

## Capturas

- [Verificación de la firma V1/V2 del APK](docs/img/verificacion-firma.png).
- [Evidencia de la llave de firma (`llave/librompaz.jks`, alias `librompaz`)](docs/evidencia-llave.txt).

Las capturas de las pantallas de la app se toman en el dispositivo o emulador al momento de la defensa; este repositorio no incluye imágenes inventadas de pantallas que no se ejecutaron.

## Limitaciones

- API REST pendiente por falta de ambiente backend: la evaluación se hace en **modo local con Room**; los endpoints de `ApiService` son un contrato declarado, no una integración probada contra servidor.
- Prototipo académico: datos ficticios, claves con sal fija de demostración, base local con migración destructiva, sin validez oficial ante el MINEDUC ni el sostenedor.
