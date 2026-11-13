# Tareas — Rutinify (MVP)

> Una tarea a la vez, en orden. Cada tarea deja el proyecto **compilando y con los tests en verde**
> (`./gradlew assembleDebug testDebugUnitTest lintDebug`). Ver DoD en `CLAUDE.md` §12.
> Formato: `- [ ] T-xx · título — (requisitos)` + criterios de cierre.

## Fase 0 · Setup

- [x] **T-01 · Dependencias y build** — (base)
  - `git init` + `.gitignore` revisado; primer commit con el proyecto tal cual.
  - Añadir a `libs.versions.toml`: KSP, Hilt, hilt-navigation-compose, Room, kotlinx-serialization, navigation-compose, lifecycle-runtime/viewmodel-compose, Truth, Turbine, coroutines-test, Robolectric, room-testing. Actualizar Compose BOM y Kotlin (D-18).
  - JVM target 17. Plugins `kotlin.serialization`, `ksp`, `hilt`, `room`. Verificar compatibilidad con AGP 9 (riesgo K-1) y anotar versiones en `design.md` §2.
  - Test `TestStackSmokeTest` (Truth + Turbine + coroutines-test + serialization).
  - ✅ `assembleDebug`, `testDebugUnitTest` y `lintDebug` pasan.
  - _Hecho:_ stack verificado con AGP 9.1.1 + Kotlin 2.3.21 + KSP 2.3.13 + Hilt 2.60.1. Las últimas AndroidX exigían `compileSdk 37`; se bajaron a las más nuevas compatibles con 36 (D-20). Smoke test en verde, lint sin errores.
- [x] **T-02 · Esqueleto de Hilt y paquetes** — (base)
  - `RutinifyApp` `@HiltAndroidApp`, `MainActivity` `@AndroidEntryPoint`, módulos `TimeModule` (`Clock`, `ZoneProvider`), `DispatchersModule` (`@IoDispatcher`, `@DefaultDispatcher`), `CoroutineScopeModule` (`@ApplicationScope`).
  - Crear la estructura de paquetes de `CLAUDE.md` §3. Borrar `Greeting` de la plantilla.
  - `MainDispatcherRule` y `ui/preview/PreviewData.kt` vacíos listos para tests/previews.
  - ✅ La app arranca con una pantalla vacía.
  - _Hecho:_ `RutinifyApp`/`MainActivity` con Hilt, módulos de tiempo, dispatchers y `@ApplicationScope`; paquetes creados (D-21, D-22). Tests de `SystemZoneProvider` (zona leída en cada llamada) y `MainDispatcherRule`. Verificado en emulador API 36: arranca sin crash con pantalla vacía.
- [x] **T-03 · Navegación base** — (R-8.4)
  - Rutas `@Serializable` (§10 de design), `RutinifyNavHost` con pantallas placeholder, tema M3 claro/oscuro.
  - ✅ Se puede navegar Home → NewRoutine → atrás.
  - _Hecho:_ `Destinations.kt` (D-23), `RutinifyNavHost` con Inicio (FAB "Nueva rutina"), Nueva rutina y placeholders para Revisión/Detalle/Permisos; previews claro/oscuro con `@ThemePreviews`; ventana oscura en `values-night`. Tests Robolectric de navegación (FAB, flecha y back del sistema) y de serialización de rutas; verificado en emulador API 36.

## Fase 1 · Modelo y persistencia

- [x] **T-04 · Modelo de dominio** — (R-8.3, design §3)
  - `RoutineOrder`, `AlarmItem`, enums de estado, `OrderSummary.displayStatus`, `RoutineDraft`, `DraftItem`.
  - Tests: `displayStatus` (ACTIVE sin pendientes → FINISHED, etc.).
  - _Hecho:_ modelos de design §3 en `domain/model` (Kotlin puro, sin `android.*`), más `OrderWithAlarms` y `DraftOrigin`. 6 tests de `displayStatus`: activa sin pendientes (también con 0 alarmas) → FINISHED; pausada y cancelada no cambian.
- [x] **T-05 · Room: entidades, converters y DB** — (R-5.1, design §5)
  - `RoutineOrderEntity`, `AlarmItemEntity` (FK cascade, índices), `Converters` (`LocalDate`, `LocalTime`, `Instant`, enums), `RutinifyDatabase` v1 con `exportSchema`.
  - Tests (Robolectric, in-memory): insertar/leer, cascade al borrar orden, **AUTOINCREMENT no reutiliza ids** tras borrar.
  - _Hecho:_ entidades con FK cascade e índices (`date`, `order_id`, `(status, order_id)`), `Converters`, `RutinifyDatabase` v1 con esquema en `app/schemas/`, DAOs mínimos y `DatabaseModule` (D-25, D-26). 12 tests: conversores, lectura completa, cascade, AUTOINCREMENT en ambas tablas y FK que rechaza orden inexistente.
- [x] **T-06 · DAOs** — (R-8.1, R-8.2, R-8.6, R-9.1, R-6.1, R-12.1)
  - `OrderDao` (`observeSummaries`, `observeWithAlarms`, `findOpenByDate`, `updateStatus`, `delete`), `AlarmItemDao` (ver design §5).
  - Tests con Turbine: el resumen cuenta pendientes/total y se re-emite al cambiar un estado; orden por fecha desc + creación desc; `getPendingForActiveOrders` excluye pausadas/canceladas.
  - _Hecho:_ consultas de design §5 en `OrderDao` y `AlarmItemDao` con filas `OrderSummaryRow`, `OrderWithAlarmsRow` y `PendingAlarmRow` (D-27). 15 tests nuevos: recuentos, re-emisión al cambiar alarma u orden, orden R-8.1, conflicto por fecha, pendientes de activas, `updateStatus` con `fired_at`/`done_at`, snooze y reinserción con el mismo id.
- [x] **T-07 · Repositorios** — (R-5.1, R-5.7)
  - Interfaces en `domain/repository`, implementaciones en `data/repository` con mapeo entity↔dominio y operaciones `@Transaction` (crear orden con alarmas, reemplazar).
  - Tests: creación atómica (si falla un insert, no queda nada), mapeos.
  - _Hecho:_ `OrderRepository`/`AlarmRepository` en dominio; implementaciones con mapeos y `createOrder` transaccional que también resuelve el reemplazo (R-6.2), binding Hilt. 15 tests: fallo de alarmas o de reemplazo no deja nada escrito, reemplazo cancela solo pendientes, orden de la línea de tiempo, restauración idéntica (D-28, D-29).

## Fase 2 · Importación (TDD)

- [x] **T-08 · `JsonExtractor`** — (R-3.1, R-3.3)
  - Tests primero: JSON limpio, ```` ```json ```` fence, fence sin lenguaje, texto antes/después, llaves dentro de strings, strings con `\"`, dos objetos (gana el primero válido), primer candidato roto y segundo válido, sin JSON, BOM.
  - _Corrección (R-3.23, D-32):_ límite de 20 candidatos y de 32 niveles de anidamiento; tests de texto patológico (miles de `{`) y de anidamiento profundo.
  - _Hecho:_ `JsonExtractor` en `data/importer` devuelve `ExtractedJson(json, text)` (D-30). 18 tests: todos los casos listados más prioridad del bloque, bloque sin JSON, candidato sin cerrar, no devolver objetos interiores de un candidato inválido, array suelto y comillas tipográficas.
- [x] **T-09 · `RoutineJsonParser` — nivel rutina** — (R-3.2, R-3.4, R-3.5, R-3.6, R-3.7, R-3.18)
  - Tests primero con fixtures en `src/test/resources/fixtures/`. `ImportResult`, `ValidationIssue`, `IssueKind`.
  - _Hecho:_ tipos en `domain/importer` (D-31) y `RoutineJsonParser` con las reglas de rutina; 18 fixtures (incluido `valid_v1.json`, el ejemplo canónico) y 25 tests: versión (ausente, 2, texto, decimal), `routine`, nombre, fecha, notas, `items` (ausente, vacío, no lista, 200/201), acumulación y campos desconocidos. El lector de items ya aplica la tabla §4.3; sus tests van en T-10.
- [x] **T-10 · `RoutineJsonParser` — nivel item** — (R-3.8–R-3.15, R-3.16, R-3.17, R-3.21, R-3.22, D-13, D-32)
  - Tests primero: cada fila de la tabla design §4.3; tipos erróneos; acumulación de varios errores; warning de título recortado; normalización de categoría; `6:45` → `06:45`; orden por `(dayOffset, time, position)`.
  - Límites (R-3.21, R-3.22): notas/descripción 500, responsable 40, categoría 30 tras normalizar (justo en el límite pasa, uno más falla); 10 categorías distintas pasan, 11 fallan.
  - _Hecho:_ `IssueKind` gana `INPUT_TOO_LONG`, `TEXT_TOO_LONG` y `TOO_MANY_CATEGORIES`, y `ValidationIssue` gana `limit` (D-33). El parser aplica los límites de longitud (tras `trim`; categoría tras normalizar) y de 10 categorías. 39 tests nuevos en `RoutineJsonParserItemTest`: hora, título, dayOffset, duración, textos opcionales, categoría, límites en el borde, item no objeto, acumulación y orden.
- [x] **T-11 · `RoutineImporter` + plantilla** — (R-1.3, R-3.19, R-3.20, R-3.23)
  - `RoutineJsonImporter` (extractor + parser) detrás de la interfaz de dominio, binding Hilt.
  - `res/raw/prompt_template.txt` con el texto del prompt de producto y el ejemplo v1 completo.
  - Test: el JSON embebido en la plantilla parsea sin errores. Test de rendimiento: 200 items < 300 ms en JVM (R-13.2, orientativo).
  - Límite de 100 KB antes de extraer (`INPUT_TOO_LONG`, R-3.20) y análisis en `@DefaultDispatcher` (R-3.23).
  - _Hecho:_ `RoutineImporter` (dominio) + `RoutineJsonImporter` + `ImporterModule`; el borrador lleva `rawJson` (D-34). Plantilla en `res/raw` con instrucciones, límites, el ejemplo v1 y el marcador. 14 tests: markdown, `rawJson`, UUID, sin JSON, errores del parser, 100 KB exactos / +1 byte / multibyte / KB redondeados, dispatcher inyectado, 200 items < 300 ms, y la plantilla (marcador, parsea, idéntica al fixture).
- [x] **T-12 · Mensajes de error en español** — (R-3.3–R-3.17, R-3.20–R-3.22, R-13.3)
  - `strings.xml` + `ui/common/ErrorMessages.kt` (`ValidationIssue` → texto). Límite de 20 + "y {k} más".
  - Tests: cada `IssueKind` produce un mensaje con actividad, campo y valor cuando aplica.
  - _Hecho:_ `UiText` (recurso + argumentos anidados, `asString()` en Compose) y `ErrorMessages.message/list` con los textos de R-3.3–R-3.22 en `strings.xml` (D-35). 25 tests Robolectric con el texto exacto: cada `IssueKind`, contexto título/hora/número, título largo acortado, tipos no técnicos, nivel rutina e item, y 20 / 21 / 25 errores.

## Fase 3 · Tiempo y planificación

- [x] **T-13 · `TriggerTimeCalculator` y `DefaultDatePolicy`** — (R-4.4, R-4.5, R-5.2, R-5.8, R-12.2)
  - Tests primero: dayOffset 1 a las 02:00, hueco DST (`Europe/Madrid`), solape DST, fecha por defecto hoy/mañana con `Clock.fixed`, cambio de zona recalcula.
  - _Hecho:_ `TriggerTimeCalculator` (`triggerAt`, `effectiveTrigger` con snooze) y `DefaultDatePolicy` → `DefaultDate(date, chosenByApp)` en `domain/time`. 16 tests: medianoche, dayOffset, fin de año, hueco y solape DST en Madrid, cambio de zona, snooze, fecha del JSON, hoy/mañana, borde `<= now` y "hoy" según la zona.
- [x] **T-14 · `BuildSchedulePreviewUseCase`** — (R-4.1, R-4.6–R-4.11)
  - Tests: marca pasadas (`<= now`), exclusiones manuales, resumen "28 alarmas, de 06:45 a 02:00 (+1 día)", sin futuras → `canSchedule = false`, sugerencia de mañana.
  - _Hecho:_ `BuildSchedulePreviewUseCase` → `SchedulePreview` (items con `triggerAt`/`isPast`/`isIncluded`, `ScheduleSummary`, `canSchedule`, `suggestTomorrow`; D-36). 15 tests: horas por fecha, orden, pasadas y borde `<= now`, exclusión manual, resumen con "+1 día", sin incluidas, cambio de fecha y los casos de R-4.11.

## Fase 4 · Scheduler, receivers y notificaciones

- [x] **T-15 · `PendingIntentFactory` + `AndroidAlarmScheduler`** — (R-5.3, R-5.4, R-12.6, design §6)
  - Tests Robolectric (`ShadowAlarmManager`): requestCode = id; `schedule` dos veces → una sola alarma; `cancel` encuentra el mismo PI; modo EXACT vs WINDOWED según `canScheduleExactAlarms`.
  - _Hecho:_ `AlarmScheduler`/`ScheduleMode` en dominio; `PendingIntentFactory` (disparo `rutinify://alarm/{id}`, requestCode = id, inmutable, `FLAG_NO_CREATE` para cancelar) y `AndroidAlarmScheduler` (exacta en reposo o ventana de 10 min; `SecurityException` → ventana), `AlarmModule`, permiso `SCHEDULE_EXACT_ALARM` y `AlarmReceiver` vacío hasta T-18 (D-37). 20 tests Robolectric.
- [x] **T-16 · Canales y `AndroidNotificationPublisher`** — (R-5.9, R-7.1–R-7.3, R-7.8, design §8)
  - Canal `cat_<categoria>` idempotente, contenido, acciones "Hecho"/"+5 min", content intent con deep link, `cancel(alarmId)` y `cancelForOrder`.
  - Tests Robolectric: canal creado una vez; notificación con 2 acciones y canal correcto; sin permiso no lanza.
  - _Hecho:_ `NotificationPublisher`/`AlarmNotification` en dominio; `AndroidNotificationPublisher` (canales `cat_<categoria>` en el grupo "Rutinas" solo si faltan, notificación con título/texto/subtítulo, hora de disparo, acciones "Hecho" y "+5 min", deep link al detalle, `cancel` y `cancelForOrder`); `PendingIntentFactory` añade los intents de acción y de detalle; permiso `POST_NOTIFICATIONS`, icono propio y `NotificationActionReceiver` vacío hasta T-18 (D-38). 17 tests Robolectric.
- [x] **T-17 · Use cases de disparo y acciones** — (R-7.1, R-7.4, R-7.5, R-7.7, R-9.5)
  - `HandleAlarmFiredUseCase`, `MarkAlarmDoneUseCase`, `SnoozeAlarmUseCase`, `AlarmMutationLock`.
  - Tests con fakes: no notifica si la alarma no está pendiente/orden no activa; snooze guarda `snoozedUntil = now+5`.
  - _Hecho:_ `AlarmMutationLock` (Mutex singleton), `HandleAlarmFiredUseCase` → `FireOutcome`, `MarkAlarmDoneUseCase` y `SnoozeAlarmUseCase` (D-39). Fakes a mano en `testutil` (`FakeAlarmRepository`, `FakeOrderRepository`, `FakeAlarmScheduler`, `FakeNotificationPublisher`, builders). 24 tests: disparo ignorado por estado/orden, sin permiso → `FIRED`, snooze = ahora + 5 min, idempotencia de los dos botones.
- [x] **T-18 · `AlarmReceiver` + `NotificationActionReceiver`** — (R-7.1, R-7.4, R-7.5)
  - `goAsync` + `@ApplicationScope` + timeout; manifiesto (`exported=false`).
  - Tests Robolectric: cada intent invoca el use case correcto con el id del data URI.
  - _Hecho:_ `AlarmReceiver` y `NotificationActionReceiver` con `@AndroidEntryPoint`, `goAsync()` y el scope de aplicación; `ReceiverCommand` (intent → comando con id), `ReceiverCommandHandler` (comando → use case) y `launchReceiverWork` (timeout 9 s, log y `finish()` siempre) (D-40). 13 tests: parseo de los tres intents y de intents inválidos, receivers no exportados, cada comando llega a su use case, `finish()` tras éxito, fallo, timeout y cancelación.
- [x] **T-19 · `RescheduleAllUseCase` + `RescheduleReceiver`** — (R-11.6, R-11.7, R-12.1–R-12.7)
  - Lógica de §7.2 (futuro → programar; ≤15 min → disparar; >15 min → SKIPPED), llamada desde `RutinifyApp.onCreate`.
  - Manifiesto: `RECEIVE_BOOT_COMPLETED`, acciones de boot/hora/zona/package replaced/permiso exacto.
  - Tests: idempotencia (2 ejecuciones → mismas alarmas), snooze respetado, pausadas ignoradas, umbral 15 min en ambos lados.
  - _Hecho:_ `RescheduleAllUseCase` → `RescheduleResult` (futuras → `schedule`, ≤15 min → disparo inmediato vía `fireLocked`, >15 min → `SKIPPED` y cancelada), `RescheduleReceiver` exportado con las 5 acciones, `RECEIVE_BOOT_COMPLETED` y reconciliación en `RutinifyApp.onCreate`; los tests Robolectric usan un `Application` simple (D-41). 16 tests: idempotencia, snooze, pausadas/canceladas, solo pendientes, 15 min exactos y 15 min + 1 s, cambio de zona, sin exactas, mezcla, filtro de acciones y manifiesto.

## Fase 5 · Nueva rutina y revisión

- [x] **T-20 · `ScheduleOrderUseCase` + `FindConflictingOrdersUseCase`** — (R-5.1, R-5.5, R-5.7, R-5.10, R-6.1–R-6.5)
  - Tests: pasadas/excluidas → SKIPPED; con REPLACE las órdenes previas pasan a CANCELLED y sus alarmas se cancelan; KEEP_BOTH no toca nada; fallo de Room → ninguna llamada al scheduler.
  - Límite de 500 (R-5.10): 500 exactas se programan; 501 → `TooManyAlarms` sin escribir en Room ni llamar al scheduler; con REPLACE se descuentan las pendientes que se cancelarían.
  - _Hecho:_ `ScheduleOrderUseCase` → `ScheduleOrderResult` (`Scheduled`/`TooManyAlarms`/`NothingToSchedule`/`StorageFailure`) con `ConflictPolicy` REPLACE/KEEP_BOTH, y `FindConflictingOrdersUseCase` (D-42). `FakeOrderRepository.createOrder` comparte estado con el fake de alarmas y simula el fallo de Room. 17 tests: horas programadas, omitidas, canales, ventana, nada que programar, fallo de Room, REPLACE (órdenes, alarmas, notificaciones, otras fechas), KEEP_BOTH, 500/501, pausadas fuera del límite y descuento con REPLACE.
- [x] **T-21 · Pantalla Nueva rutina** — (R-1.1, R-1.2, R-1.4, R-2.1–R-2.5, R-3.19)
  - `ClipboardReader` en `data/system`, `DraftStore`, `NewRoutineViewModel` (texto en `SavedStateHandle`), `NewRoutineScreen` + previews (vacío, con errores, validando).
  - Tests VM: copiar plantilla emite efecto; portapapeles vacío; validar con error → issues; validar OK → efecto de navegación con `draftId`.
  - _Hecho:_ `Clipboard` + `AndroidClipboard`, `PromptTemplateSource` + `RawPromptTemplateSource`, `DraftStore` + `InMemoryDraftStore` (con avisos), `NewRoutineViewModel` (texto en `SavedStateHandle`, efectos `ShowMessage`/`NavigateToReview`) y `NewRoutineRoute`/`NewRoutineScreen` con 4 previews (vacía, plantilla abierta, con errores, validando) (D-43). 15 tests: 11 del VM y 4 Robolectric del portapapeles real y la plantilla. No se ha visto aún en un emulador.
- [x] **T-22 · Pantalla Revisión** — (R-4.1–R-4.12, R-5.6, R-6.1–R-6.4)
  - `ReviewViewModel`: edición de hora/título/categoría con revalidación, quitar con deshacer, excluir, selector de fecha, resumen, diálogo de conflicto, borrador perdido.
  - `ReviewScreen` + previews (normal, con pasadas, sin futuras, diálogo de conflicto).
  - Tests VM con Turbine.
  - _Hecho:_ `DraftItemRules` (reglas compartidas con el parser), `ReviewViewModel` (edición con revalidación, quitar con deshacer, excluir, fecha, resumen, aviso de fecha elegida, sugerir mañana, conflicto, 500, reintento, borrador perdido) y `ReviewRoute`/`ReviewScreen` con 4 previews (normal con pasadas, sin futuras, conflicto, edición) (D-44). 36 tests: 29 del VM y 7 de las reglas. Probado en emulador (API 36): pegar → revisar → editar → programar → notificación → "Hecho" y "+5 min".

## Fase 6 · Inicio y detalle

- [x] **T-23 · Pantalla Inicio** — (R-8.1–R-8.6)
  - `HomeViewModel`, tarjeta de orden (nombre, fecha, pendientes/total, estado), estado vacío de 3 pasos, FAB. Previews.
  - _Hecho:_ `ObserveOrderSummariesUseCase` + `HomeViewModel` (`isLoading` evita el parpadeo del estado vacío) + `HomeRoute`/`HomeScreen` con tarjetas (nombre, fecha larga, "Pendientes: n de m", etiqueta de estado) y estado vacío de 3 pasos; tocar una tarjeta abre el detalle. Formato de fecha compartido con Revisión. 8 tests nuevos (7 VM + 1 navegación); probado en emulador con una orden real (D-45).
- [x] **T-24 · Pantalla Detalle de orden (lectura)** — (R-9.1, R-9.2, R-7.6, R-5.4)
  - Timeline con estados, próxima alarma destacada, aviso de modo ventana, deep link desde la notificación con `highlightAlarmId`. Previews.
  - _Hecho:_ `ObserveOrderDetailUseCase` (orden por hora efectiva, próxima pendiente) + `CanScheduleExactAlarmsUseCase` + `OrderDetailViewModel`/`OrderDetailRoute`/`OrderDetailScreen`: cabecera, aviso de R-5.4 (revisado al volver), línea de tiempo con estado, "Próxima" y borde en la alarma resaltada, "{n} alarmas programadas" una vez, "Esta rutina ya no existe". Deep link `rutinify://order/{id}?alarm=` en el NavHost; etiqueta de estado compartida con Inicio. 21 tests nuevos (6 use case + 14 VM + 1 navegación); probado en emulador abriendo el enlace de la notificación (D-46).
- [x] **T-25 · Acciones sobre alarmas individuales** — (R-9.3–R-9.5)
  - `DeleteAlarmUseCase`, `RestoreAlarmUseCase`, marcar hecha. Snackbar "Deshacer".
  - Tests: deshacer reprograma si es futura y no si ya pasó.
  - _Hecho:_ `DeleteAlarmUseCase` (borra, cancela si estaba pendiente, quita la notificación y devuelve la alarma) y `RestoreAlarmUseCase` (misma fila e id; reprograma si es futura, *Omitida* si ya pasó, sin programar en órdenes pausadas). El detalle gana "Marcar como hecha" (pendientes y disparadas, con `MarkAlarmDoneUseCase`) y "Eliminar" con snackbar "Deshacer". 15 tests nuevos (4 + 6 use case, 5 VM); probado en emulador: borrar y deshacer vuelve a registrar la alarma en `AlarmManager` (D-47).

## Fase 7 · Gestión de órdenes

- [ ] **T-26 · Eliminar todas las alarmas / eliminar orden** — (R-10.1–R-10.4, R-10.8)
  - `CancelOrderAlarmsUseCase`, `DeleteOrderUseCase`, diálogo de confirmación con nº de pendientes, ocultar acciones en órdenes canceladas.
  - Tests: con alarmas FIRED/DONE se conservan; se quitan notificaciones visibles de la orden; scheduler recibe `cancel` solo de las PENDING.
- [ ] **T-27 · Pausar / reanudar** — (R-10.5, R-10.6)
  - Tests: pausar cancela sin cambiar estado; reanudar omite pasadas y programa futuras; `RescheduleAll` ignora pausadas.
- [ ] **T-28 · Duplicar para otra fecha** — (R-10.7)
  - `DuplicateOrderUseCase` → borrador en `DraftStore` → navegar a Revisión con la fecha elegida. Tests.

## Fase 8 · Permisos

- [ ] **T-29 · `PermissionChecker` + pantalla Permisos + banner** — (R-11.3–R-11.5, R-11.8)
  - Estado refrescado en `ON_RESUME`, banner en Inicio, pantalla con botones a los ajustes correspondientes. Previews.
- [ ] **T-30 · Flujo de permisos al programar** — (R-11.1, R-11.2, R-11.6)
  - Diálogos explicativos en Revisión antes de programar; al volver con permiso exacto concedido → `RescheduleAll`.
  - Tests VM de la máquina de pasos (notificaciones → exactas → conflicto → programar).

## Fase 9 · Pulido y verificación

- [ ] **T-31 · Pulido** — (R-13.3, R-13.4)
  - Revisión de strings y plurales, accesibilidad (contentDescription, 48 dp), modo oscuro, icono de notificación monocromo, icono de app.
- [ ] **T-32 · Cobertura y verificación final** — (R-13.5, R-13.6)
  - Añadir el plugin Kover (D-19) con regla `minBound(80)`; `koverVerify` ≥ 80 % en dominio + importer; `lintDebug` limpio; manifiesto sin `INTERNET`.
  - Prueba manual en dispositivo/emulador (checklist en `specs/manual-test.md`): importar JSON real del cachorro, disparo exacto, "Hecho", "+5 min", reinicio del emulador, cambio de zona horaria, revocar alarmas exactas, eliminar orden con alarmas disparadas.

## Trazabilidad rápida

| Requisito | Tareas |
|---|---|
| R-1 Plantilla | T-11, T-21 |
| R-2 Entrada | T-21 |
| R-3 Validación | T-08, T-09, T-10, T-11, T-12 |
| R-4 Revisión | T-13, T-14, T-22 |
| R-5 Programación | T-13, T-15, T-16, T-20, T-22 |
| R-6 Reemplazo | T-06, T-20, T-22 |
| R-7 Notificación | T-16, T-17, T-18, T-24 |
| R-8 Inicio | T-04, T-06, T-23 |
| R-9 Detalle | T-24, T-25 |
| R-10 Gestión de orden | T-26, T-27, T-28 |
| R-11 Permisos | T-19, T-29, T-30 |
| R-12 Robustez | T-13, T-19 |
| R-13 No funcionales | T-11, T-12, T-31, T-32 |
