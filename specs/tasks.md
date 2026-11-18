# Tareas — Rutinify (MVP, v1.1 y roadmap v1.2–v2.0)

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

- [x] **T-26 · Eliminar todas las alarmas / eliminar orden** — (R-10.1–R-10.4, R-10.8)
  - `CancelOrderAlarmsUseCase`, `DeleteOrderUseCase`, diálogo de confirmación con nº de pendientes, ocultar acciones en órdenes canceladas.
  - Tests: con alarmas FIRED/DONE se conservan; se quitan notificaciones visibles de la orden; scheduler recibe `cancel` solo de las PENDING.
  - _Hecho:_ `OrderRepository.cancelOrder` (transacción, reutilizada por el reemplazo), `CancelOrderAlarmsUseCase` y `DeleteOrderUseCase`. El detalle gana el botón "Eliminar todas las alarmas" (oculto si la orden está cancelada) y "Eliminar orden" en el menú ⋮, ambos con confirmación; tras borrar vuelve a Inicio. 14 tests nuevos (1 repositorio + 3 + 3 use case + 7 VM); probado en emulador: la alarma pendiente desaparece de `AlarmManager` y borrar la orden vuelve al estado vacío (D-48).
- [x] **T-27 · Pausar / reanudar** — (R-10.5, R-10.6)
  - Tests: pausar cancela sin cambiar estado; reanudar omite pasadas y programa futuras; `RescheduleAll` ignora pausadas.
  - _Hecho:_ `PauseOrderUseCase` (orden → `PAUSED`, luego `cancel()` de sus pendientes, que siguen `PENDING`) y `ResumeOrderUseCase` (pendientes con hora no futura → `SKIPPED` sin tolerancia, orden → `ACTIVE`, luego programa las futuras; devuelve `ResumeResult`). El detalle muestra "Pausar" o "Reanudar" junto a "Eliminar todas las alarmas" y, al reanudar, avisa de cuántas alarmas se omitieron. `RescheduleAll` ya ignoraba las pausadas (test de T-19). 16 tests nuevos (4 + 6 use case + 6 VM); probado en emulador: pausar quita las alarmas de `AlarmManager` y reanudar tras las 22:04 omite esa y programa el resto (D-49).
- [x] **T-28 · Duplicar para otra fecha** — (R-10.7)
  - `DuplicateOrderUseCase` → borrador en `DraftStore` → navegar a Revisión con la fecha elegida. Tests.
  - _Hecho:_ `DuplicateOrderUseCase` copia todas las alarmas de la orden (cualquier estado, a su hora original, en orden de línea de tiempo) a un borrador `DUPLICATE` sin `rawJson` con la fecha elegida. "Duplicar para otra fecha" va en el menú ⋮ del detalle (también en órdenes canceladas; oculto si no hay alarmas), abre un selector de fecha en mañana y navega a Revisión. El selector de fecha pasa a `ui/common/RoutineDatePickerDialog`, compartido con Revisión. 9 tests nuevos (5 use case + 4 VM); probado en emulador: duplicar al 20/11 abre Revisión con las 3 actividades y al programar vuelve con la nueva orden en Inicio (D-50).

## Fase 8 · Permisos

- [x] **T-29 · `PermissionChecker` + pantalla Permisos + banner** — (R-11.3–R-11.5, R-11.8)
  - Estado refrescado en `ON_RESUME`, banner en Inicio, pantalla con botones a los ajustes correspondientes. Previews.
  - _Hecho:_ `PermissionChecker` + `AndroidPermissionChecker` (mismas comprobaciones que el scheduler y el publicador), `GetPermissionStatusUseCase`, banner "Faltan permisos…" en Inicio y pantalla *Permisos* con el estado de cada permiso y "Corregir" (diálogo de Android 13+ o ajustes de notificaciones si se denegó para siempre; "Alarmas y recordatorios" para las exactas). Ambas se refrescan en `ON_RESUME`; al pasar de no exactas a exactas, *Permisos* llama a `RescheduleAll`. Se elimina `PlaceholderScreen`. 12 tests nuevos (3 use case + 4 VM Permisos + 3 VM Inicio + 2 navegación); probado en emulador: conceder alarmas exactas desde *Permisos* deja las 3 alarmas exactas en `AlarmManager` y quita el banner (D-51).
- [x] **T-30 · Flujo de permisos al programar** — (R-11.1, R-11.2, R-11.6)
  - Diálogos explicativos en Revisión antes de programar; al volver con permiso exacto concedido → `RescheduleAll`.
  - Tests VM de la máquina de pasos (notificaciones → exactas → conflicto → programar).
  - _Hecho:_ `SchedulingPermissionsUseCase` (explicar notificaciones solo la primera vez, guardado en `PermissionPromptStore` sobre `SharedPreferences`; exactas cada vez que falten). "Programar alarmas" en Revisión recorre notificaciones ("Continuar" → diálogo de Android, "Ahora no") → exactas ("Abrir ajustes", "Continuar sin exactas") → conflicto → programar; al volver de ajustes sigue y, si se concedieron las exactas, llama antes a `RescheduleAll`. 15 tests nuevos (4 use case + 11 VM); probado en emulador con ambos permisos revocados: tras permitir notificaciones y conceder exactas en ajustes, la orden se programa y todas las alarmas quedan exactas (D-52).

## Fase 9 · Pulido y verificación

- [x] **T-31 · Pulido** — (R-13.3, R-13.4)
  - Revisión de strings y plurales, accesibilidad (contentDescription, 48 dp), modo oscuro, icono de notificación monocromo, icono de app.
  - _Hecho:_ revisados textos (ningún literal en composables; los 6 plurales con `one`/`other`), iconos accionables con `contentDescription` y botones de 48 dp, modo oscuro probado en emulador, icono de notificación monocromo (campana). Nuevo icono de app adaptativo (despertador con check, también como icono temático) en lugar del robot de la plantilla; se borran los `.webp` de mipmap (con minSdk 28 basta el adaptativo), `colors.xml` de la plantilla y la etiqueta redundante de la Activity. Lint queda sin avisos propios (solo versiones de dependencias). Sin tests nuevos: 483 en verde (D-53).
- [x] **T-32 · Cobertura y verificación final** — (R-13.5, R-13.6)
  - Añadir el plugin Kover (D-19) con regla `minBound(80)`; `koverVerify` ≥ 80 % en dominio + importer; `lintDebug` limpio; manifiesto sin `INTERNET`.
  - Prueba manual en dispositivo/emulador (checklist en `specs/manual-test.md`): importar JSON real del cachorro, disparo exacto, "Hecho", "+5 min", reinicio del emulador, cambio de zona horaria, revocar alarmas exactas, eliminar orden con alarmas disparadas.
  - _Hecho:_ plugin Kover 0.9.9 con filtro `domain` + `data.importer` (sin el código generado de Hilt y kotlinx.serialization) y regla `minBound(80)`: `koverVerify` pasa con un 99,2 % de líneas. `lintDebug` sin avisos propios, manifiesto final sin `INTERNET`. Checklist manual en `specs/manual-test.md`, los 11 pasos en verde en el emulador (D-54).

## Fase 10 · Rutinas de varios días (v1.1)

> Tratamientos y planes con fin en un solo JSON (D-55): `repeatDays` para días iguales, `dayOffset` hasta 30 para días distintos. Mismo DoD por tarea. D-55 y D-56 aprobadas el 2026-11-14.

- [x] **T-33 · Validador, `repeatDays` y plantilla para varios días** — (R-3.11, R-3.24–R-3.27, R-1.2)
  - Tests primero: `dayOffset` 0, 6 y 30 válidos; 31, −1 y 1.5 dan el error nuevo de R-3.11; `repeatDays` ausente/null = 1, 7 y 30 válidos, 0, 31 y "7" dan R-3.24; `repeatDays` 7 con 3 tomas → 21 actividades con `dayOffset` 0..6; madrugada del último día → `dayOffset` N; `repeatDays` > 1 con `dayOffset` 2 → R-3.26; 70 tomas × 3 días → R-3.27; errores numerados sobre el JSON original.
  - Fixtures `valid_repeat.json` (antibiótico cada 8 h, `repeatDays` 7) y `valid_multiday.json` (dosis que baja, días escritos a mano) importan sin avisos.
  - `MAX_DAY_OFFSET = 30` y `MAX_REPEAT_DAYS = 30` en `domain/importer`, usados por parser y `DraftItemRules`; `IssueKind.REPEAT_DAYS_OUT_OF_RANGE` y `REPEAT_WITH_DAY_OFFSET`; strings nuevos y `issue_day_offset` actualizado.
  - `prompt_template.txt`: "cada 8 h" se convierte en horas fijas; si todos los días son iguales, un solo día + `repeatDays`; si no, cada día con `dayOffset` 0, 1, 2… (máximo 30 días y 200 actividades en total). El ejemplo canónico sigue igual a `valid_v1.json`.
  - Test de dominio: `TriggerTimeCalculator` con `dayOffset` 6 cruzando un cambio de horario (Europe/Madrid) mantiene la hora de pared.
  - _Hecho:_ el parser lee `routine.repeatDays` (1–30) y expande las actividades al importar; `dayOffset` admite 0–30, y con repetición solo 0–1. El límite de 200 se aplica tras expandir. Los errores siguen numerados sobre el JSON original. Tres `IssueKind` nuevos con sus textos, constantes en `DraftItemRules` y plantilla del prompt con las dos formas de escribir varios días. 22 tests nuevos (17 del parser, 3 de mensajes, 2 de horas) y fixtures `valid_repeat.json` / `valid_multiday.json`: 505 en verde, `koverVerify` pasa (D-57).
- [x] **T-34 · Rango de la orden y conflictos** — (R-6.6, R-6.7)
  - `RoutineSpan(start, end)` en dominio, calculado desde fecha + mayor `dayOffset` (borrador y orden); helper `isMultiDay`.
  - `OrderDao.findOpenOverlapping(start, end)` (D-56) con tests Robolectric: solape parcial, contenido, adyacente sin solape, orden sin alarmas, canceladas excluidas. Sustituye a `findOpenByDate` en `ScheduleOrderUseCase`.
  - Diálogo de conflicto con rangos y el aviso de "Reemplazar" cuando hay varios días; tests de VM.
  - _Hecho:_ `RoutineSpan` y `ConflictingOrder` en el dominio. `findOpenOverlapping` en DAO y repositorio sustituye a `findOpenByDate`, sin migración. La usan la búsqueda de conflictos y "Reemplazar". Las rutinas de un día solo comparan su fecha (D-58). El diálogo muestra el rango de cada orden y avisa que reemplazar cancela rutinas completas; previews claro/oscuro con y sin rangos. 22 tests nuevos o reescritos (527 en verde).
- [x] **T-35 · Revisión de varios días** — (R-4.13, R-4.14, R-4.15)
  - `BuildSchedulePreviewUseCase` agrupa por día y el resumen informa el número de días con alarmas; tests (TDD).
  - `ReviewScreen`: cabeceras "Día {k} · {fecha}" en el `LazyColumn` (keys estables), resumen nuevo, previews claro/oscuro con `PreviewData` de varios días.
  - Rutinas de 0–1 días sin cambios visibles (tests de regresión del VM).
  - _Hecho:_ `SchedulePreview` añade `days` e `isMultiDay`, y `ScheduleSummary` añade `dayCount` (D-59). Revisión muestra "Día {k} · {fecha}" con keys estables, horas sin sufijo y el resumen "{n} alarmas en {d} días, del … al …" (o "el …" si queda un día). Al cambiar la fecha se desplaza todo. Las rutinas de 0–1 días no cambian. Previews claro/oscuro de varios días. 15 tests nuevos (542 en verde).
- [x] **T-36 · Inicio y detalle de varios días** — (R-8.2, R-9.6, R-10.7)
  - `observeSummaries` devuelve el mayor `dayOffset`; Inicio muestra el rango; detalle agrupa por día; duplicar conserva los `dayOffset` (test del use case).
  - Previews nuevos y tests de VM.
  - _Hecho:_ `observeSummaries` devuelve `max_day_offset` (sin migración) y `OrderSummary`/`OrderDetail` exponen su `span`. Inicio y la cabecera del detalle muestran "del sábado 14 al viernes 20 de noviembre de 2026" en órdenes de varios días (D-60). El detalle pone "Día {k} · {fecha}" sobre la primera alarma de cada día, sin "+1 día". Test de duplicar con `dayOffset` 0–6. Previews claro/oscuro de Inicio y detalle. 9 tests nuevos (551 en verde).
- [x] **T-37 · Verificación v1.1** — (R-13.5)
  - `koverVerify` ≥ 80 %; añadir a `specs/manual-test.md` los pasos del tratamiento de 7 días (importar, conflicto con la rutina del día, reinicio a mitad, duplicar) y ejecutarlos en el emulador.
  - README: quitar "una rutina cubre un solo día" de los límites, documentar `repeatDays` en el contrato y añadir el ejemplo de medicación de varios días.
  - _Hecho:_ Kover 99,2 % (782 de 788 líneas). Pasos 12–18 de `manual-test.md` ejecutados en el emulador: importar, conflicto con rango, Inicio y detalle, reinicio el día 4 (11 tomas exactas restauradas) y duplicar. Se corrigió el aviso de reemplazo, cuyas comillas quitaba Android («»). README actualizado. La fecha por defecto de rutinas de varios días se corrige en T-38.
- [x] **T-38 · Fecha por defecto en rutinas de varios días** — (R-4.5, D-61)
  - `DefaultDatePolicy`: en rutinas de varios días solo cuentan las actividades del Día 1 (`dayOffset` 0); tests (TDD).
  - Las rutinas de un día no cambian (tests existentes).
  - _Hecho:_ en rutinas de varios días `DefaultDatePolicy` solo mira `dayOffset` 0 (D-61); la madrugada del Día 1 no cuenta porque tras expandir `repeatDays` no se distingue del Día 2. 3 tests nuevos (554 en verde). En el emulador, el tratamiento importado a las 23:25 elige el lunes 16 y muestra "21 alarmas en 7 días".

## Fase 11 · Usable para otros (v1.2)

> Foco en planes de cuidado (D-62). Mismo DoD por tarea. D-64, D-66, D-67, D-68 y D-69 (opción C) aprobadas el 2026-11-16: la fase no tiene decisiones abiertas.

- [x] **T-39 · "Plan" en la interfaz** — (R-13.7)
  - Reescribir en `strings.xml` las cadenas visibles con "orden" ("Eliminar plan", "Plan no encontrado"…); sin tocar nombres internos, rutas ni deep links (D-63).
  - Test que lee el `strings.xml` español y falla si un texto visible contiene "orden".
  - ✅ Ningún texto visible dice "orden"; tests de VM que comparan textos actualizados; capturas del README sin cambios de flujo.
  - _Hecho:_ cuatro textos cambiados ("Detalle del plan", "Eliminar plan", "¿Eliminar el plan?" y "el plan quedará cancelado" en los tres mensajes de "Eliminar todas las alarmas"); las citas de KDoc también dicen "Eliminar plan". `VisibleTextsTest` lee `strings.xml` (textos y plurales, sin comentarios) y falla si aparece "orden". Los tests de VM comparan ids de recursos, así que no cambian. 2 tests nuevos (556 en verde).
- [x] **T-40 · Recibir texto compartido** — (R-14.1–R-14.5)
  - `intent-filter` `SEND` + `text/plain`, `MainActivity` `singleTop` con `onNewIntent`; `NewRoutineDestination(sharedText)` con `popUpTo(Home)` (D-64).
  - Tests de VM: texto válido → *Revisión*; texto con errores → errores y texto en el campo; vacío → "No llegó ningún texto para importar"; borrador anterior borrado de `DraftStore`. Test Robolectric del intent.
  - ✅ Compartir desde otra app abre *Revisión* sin más pasos cuando el JSON es válido.
  - _Hecho:_ `MainActivity` pasa a `singleTask` con el `intent-filter` de envío; `IncomingIntents` reconoce el envío y el texto espera en `SharedTextInbox` hasta que *Nueva rutina* (`fromShare = true`) lo toma, vacía `DraftStore` y valida. Tocar una notificación con la app abierta llega por `onNewIntent` y sigue abriendo el plan (D-64 actualizada). 10 tests nuevos (566 en verde). En el emulador, con `am start … SEND`: app cerrada → *Revisión* del tratamiento; app en *Revisión* + texto sin JSON → *Nueva rutina* con el error y "atrás" vuelve a Inicio; envío vacío → *Nueva rutina*; deep link de notificación con la app abierta → detalle del plan. Falta compartir desde ChatGPT y Gemini reales (pasos 25–26, T-50).
- [x] **T-41 · "Abrir en IA"** — (R-14.6–R-14.8)
  - Botón junto a "Copiar plantilla"; chooser "Enviar la plantilla a…"; `<queries>`; fallback al portapapeles (D-65).
  - Tests de VM del efecto y del fallback.
  - ✅ El chooser recibe la plantilla completa del escenario elegido.
  - _Hecho:_ botón "Abrir en IA" junto a "Copiar plantilla" ("Ver plantilla" pasa a su propia línea para caber en pantallas estrechas). `TextSharer` decide en el VM entre el efecto `ShareTemplate` y el fallback, que copia y muestra "No hay apps para enviar la plantilla; la copié al portapapeles". `<queries>` en el manifiesto; el chooser excluye a Rutinify. 2 tests nuevos (568 en verde). En el emulador, el chooser muestra la plantilla completa y no lista a Rutinify. Desde Android 10 el título lo pone el sistema ("Sharing text"), así que R-14.7 y D-65 se ajustaron. Hasta T-42 se envía la plantilla actual (genérica).
- [x] **T-42 · Plantillas por escenario** — (R-16.1–R-16.5, R-1.5)
  - Cinco recursos en `raw/` (D-70); `TemplateScenario`, `GetTemplateUseCase`; selector en *Nueva rutina* que recuerda la elección.
  - Test por plantilla: el ejemplo embebido parsea sin errores ni avisos; la de medicación contiene la instrucción de R-16.5.
  - ✅ "Ver", "Copiar" y "Abrir en IA" usan la plantilla elegida; previews claro/oscuro del selector.
  - _Hecho:_ cinco plantillas con las mismas instrucciones del contrato, consejos por caso y un ejemplo propio. El ejemplo canónico de `valid_v1.json` pasa a "Cachorro" y "Genérica" usa un día de mudanza (D-70 actualizada). La de medicación pide no proponer dosis ni cambiar la pauta. `TemplateScenario`, `PromptTemplateSource.load(scenario)` y `TemplateScenarioStore` en `SharedPreferences`. Selector "Plantilla para" con chips que pasan a la línea siguiente. 9 tests nuevos (577 en verde). En el emulador: elegir "Medicación" muestra su plantilla y se recuerda al reabrir la app.
- [x] **T-43 · Categorías críticas en datos y dominio** — (R-15.1, R-15.2)
  - Room v2: `category_setting` con `Migration(1, 2)` y test `MigrationTestHelper` (D-66, D-83).
  - `CategorySettingsRepository`, `SetCategoryCriticalUseCase` (reprograma las pendientes de la categoría); tests con fakes.
  - ✅ Migración probada con una base v1 real exportada; ningún plan existente cambia.
  - _Hecho:_ Room v2 con `category_setting` y `Migration(1, 2)`; `MigrationsTest` crea una base v1 con un plan, migra, valida el esquema y comprueba que el plan y su alarma siguen igual. `CategorySettingDao` lista las categorías usadas por los planes con su marca; `CategorySettingsRepository` y `SetCategoryCriticalUseCase` (reprograma las pendientes futuras de la categoría en planes activos; el modo crítico llega al scheduler en T-45). Esquemas como assets de `debug` para el test (D-83). 8 tests nuevos (585 en verde). En el emulador, instalar encima de la versión anterior migró la base a la versión 2 y los planes siguen ahí.
- [x] **T-44 · Pantalla *Ajustes* con "Alarmas críticas"** — (R-15.1, R-15.4, R-15.5)
  - `SettingsDestination`, entrada desde Inicio; interruptor por categoría; aviso de R-15.4; petición de R-15.5.
  - Tests de VM; previews claro/oscuro.
  - ✅ Marcar y desmarcar persiste y reprograma.
  - _Hecho:_ `SettingsDestination` desde el engranaje de Inicio; `SettingsViewModel` con `ObserveCategorySettingsUseCase` y `SetCategoryCriticalUseCase`, diálogo de R-15.5 al marcar la primera crítica sin exactas, aviso de R-15.4 con "Abrir ajustes" y `RescheduleAll` al volver con el permiso (D-84). 13 tests nuevos (598 en verde), previews claro/oscuro. En el emulador: marcar y desmarcar, el diálogo sin permiso, "Abrir ajustes" lleva a "Alarmas y recordatorios" y el aviso desaparece al conceder.
- [x] **T-45 · Programación y notificación de alarmas críticas** — (R-15.3, R-15.6, R-15.12–R-15.14)
  - `AlarmScheduler.schedule(…, critical)` con `setAlarmClock`; canal `alarm_critical` (`USAGE_ALARM`, `CATEGORY_ALARM`, `FLAG_INSISTENT`) y `alarm_critical_silent` (D-67); `RescheduleAllUseCase` pasa `critical`.
  - Actualizar `CLAUDE.md` §10 con la excepción de canal de D-67.
  - Tests Robolectric: `setAlarmClock` con permiso, `setWindow` sin él, cancelar con el mismo `PendingIntent`; tests de use case.
  - ✅ Las no críticas siguen exactamente como en v1.1 (tests existentes en verde).
  - _Hecho:_ `AlarmScheduler.schedule(…, critical: CriticalAlarm?)` con `setAlarmClock` (el `showIntent` abre el plan); los seis casos de uso que programan leen `criticalNames()`; `AlarmNotification.critical` lleva la alarma al canal `alarm_critical` (tono de alarma del usuario, `USAGE_ALARM`), `CATEGORY_ALARM` y `FLAG_INSISTENT`; `alarm_critical_silent` creado para T-46 (D-85). `CLAUDE.md` §10 actualizado. 16 tests nuevos (614 en verde). En el emulador: al marcar "medicación", Android muestra su alarma como próxima alarma; una alarma crítica sonó a su hora en `alarm_critical` y "Hecho" quitó la notificación.
- [x] **T-46 · Pantalla de alarma, "Silenciar" y corte a 10 min** — (R-15.7–R-15.10)
  - `AlarmActivity` (`showWhenLocked`, `turnScreenOn`) con "Hecho", "+5 min" y "Silenciar"; acciones `SILENCE` y corte programado (D-67).
  - Tests de VM y de receiver; previews claro/oscuro.
  - ✅ "Silenciar" deja la alarma *Disparada* con notificación muda; a los 10 min se silencia sola.
  - _Hecho:_ `AlarmActivity` + `AlarmViewModel` (hora, título, descripción, plan y los tres botones); `SilenceAlarmUseCase` vuelve a publicar en `alarm_critical_silent`; el corte lo programa `HandleAlarmFiredUseCase` y lo cancelan "Hecho", "+5 min" y "Silenciar"; `SilencedAlarms` cierra la pantalla si se silencia desde fuera (D-86). 25 tests nuevos (639 en verde), previews claro/oscuro. En el emulador, con la pantalla apagada: la pantalla se encendió con la alarma; "Silenciar" la cerró y dejó la notificación muda con dos botones; sin tocar nada, a los 10 min pasó sola al canal mudo. Encontrado K-13: con otra notificación de la app en la bandeja, Android 16 la agrupa y no suena.
  - _Arreglo K-13:_ grupo de notificaciones propio con resumen mudo (D-87); 5 tests nuevos (644 en verde).
- [x] **T-47 · Permiso de pantalla completa y fallback** — (R-15.11)
  - `USE_FULL_SCREEN_INTENT` (ya declarado en T-46, D-86); `canUseFullScreenIntent()` en cada disparo; fila en *Permisos* con `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (D-68).
  - Tests de `PermissionChecker` y del publicador con y sin permiso.
  - ✅ Sin permiso, la alarma suena como notificación emergente.
  - _Hecho:_ `PermissionChecker.fullScreenIntentGranted()`; el publicador lo mira en cada alarma crítica y sin él no pone `fullScreenIntent`; tarjeta "Alarmas a pantalla completa" ("Activado"/"Desactivado", "Corregir" → `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`); el banner de Inicio solo la cuenta si hay alguna categoría crítica (D-88). Además, el resumen del grupo (D-87) ya no se queda solo tras "Hecho" desde la app. 6 tests nuevos (650 en verde). En el emulador, sin el permiso: banner en Inicio, tarjeta "Desactivado", "Corregir" abre el ajuste del sistema, y una alarma crítica con la pantalla apagada sonó con sus tres botones sin abrir la pantalla de alarma.
- [x] **T-48 · Petición guiada de alarmas exactas** — (R-11.2, R-15.5; D-69 opción C)
  - Pedir el permiso al marcar la primera categoría crítica (R-15.5; hecho ya en T-44, D-84) y mostrar una tarjeta en Inicio tras programar el primer plan si falta el permiso; ambas abren `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
  - Sin `USE_EXACT_ALARM` en el manifiesto de v1.2.
  - ✅ Tests de VM de la petición y de la tarjeta; previews claro/oscuro; manifiesto sin `USE_EXACT_ALARM`.
  - _Hecho:_ `ExactAlarmsCardUseCase` y tarjeta "Activa las alarmas exactas" en Inicio con "Ahora no" y "Activar"; sustituye al banner cuando solo faltan las exactas (D-89). `ManifestPermissionsTest` comprueba que no hay `USE_EXACT_ALARM` ni `INTERNET`. 11 tests nuevos (661 en verde), previews claro/oscuro. En el emulador, sin el permiso: la tarjeta aparece sobre los planes, "Activar" abre "Alarmas y recordatorios" y al conceder desaparece.
- [x] **T-49 · Interfaz en inglés** — (R-13.3)
  - `values-en/`, `raw-en/` (cinco plantillas), patrones de fecha en recursos (D-71); lint `MissingTranslation` como error.
  - Tests de `SpanLabels`/`DateFormats` con locale `en`; los existentes en `es` siguen en verde.
  - ✅ Con el teléfono en inglés no queda ningún texto en español.
  - _Hecho:_ `values-en/strings.xml` con los 204 textos y plurales, cinco plantillas en `raw-en/`, patrones de fecha por idioma en recursos y `UiText.Date` (D-71). `MissingTranslation` es error de lint. `TestTexts` resuelve `UiText` en español o inglés en los tests de VM. 7 tests nuevos (668 en verde). En el emulador, con la app en inglés: Inicio ("Wednesday, November 18, 2026", "Pending: 0 of 1", "Finished") y *Nueva rutina* con la plantilla de medicación en inglés.
- [ ] **T-50 · Verificación v1.2** — (R-13.5)
  - `koverVerify` ≥ 80 %; pasos 19–28 de `manual-test.md` en dispositivo real con Android 14+ (bloqueo, "No molestar", reinicio, sin pantalla completa, ChatGPT y Gemini).
  - README: Roadmap y capturas actualizados.
  - ✅ Resultados anotados en `manual-test.md`.
  - _Avance (2026-11-18):_ `koverVerify` en verde. Pasos 19–24 y 28 superados en el emulador (API 36), incluidos "No molestar" (20) y reinicio (23). Falta lo que solo se puede hacer en un teléfono real: repetir 19–24 y los pasos 25–27 con ChatGPT y Gemini instalados. El README se actualiza al final de la serie. Por eso la casilla sigue sin marcar.

## Fase 12 · Validación con usuarios (v1.3)

> Sin funciones nuevas de producto salvo el diagnóstico opcional. Mide si el foco de D-62 funciona.

- [x] **T-51 · Contadores de diagnóstico** — (R-17.1–R-17.4, R-17.7)
  - Room v3: `diagnostic_counter` con migración y test (D-72, D-83); `DiagnosticsRecorder` y su uso en VMs y `ScheduleOrderUseCase`.
  - Tests: no cuenta con el interruptor apagado; cuenta cada `IssueKind`; desactivar borra.
  - ✅ El manifiesto sigue sin `INTERNET`.
  - _Hecho:_ Room v3 con `diagnostic_counter` y `Migration(2, 3)` (tests 2→3 y 1→3); `DiagnosticsRepositoryImpl` (apagado por defecto, apagar borra); registro en *Nueva rutina*, *Revisión* y `ScheduleOrderUseCase` (D-72). `ManifestPermissionsTest` sigue comprobando que no hay `INTERNET`. 12 tests nuevos (680 en verde).
- [x] **T-52 · Exportar y borrar diagnóstico** — (R-17.5, R-17.6)
  - Sección en *Ajustes*; `ExportDiagnosticsUseCase` con texto legible y versión de la app.
  - Tests de use case y VM; previews.
  - ✅ El texto exportado no contiene nombres ni contenido de rutinas.
  - _Hecho:_ sección "Diagnóstico" en *Ajustes* con interruptor, "Exportar diagnóstico" y "Borrar diagnóstico"; `ManageDiagnosticsUseCase`, `ExportDiagnosticsUseCase` y `AppInfo`; `DiagnosticsText` arma el texto (D-90). Textos en español e inglés. 7 tests nuevos (687 en verde), preview nueva. En el emulador: tras activarlo y programar un plan, el selector de Android recibe "Diagnóstico de Rutinify 1.0 (1)" con `plans.created: 1`, `step.new_routine: 1`, `step.review: 1`…
- [ ] **T-53 · Checklist de publicación** — (sin requisito de producto; D-68, D-69)
  - `specs/release-checklist.md`: ficha, política de privacidad, seguridad de datos, declaraciones de permisos, clasificación de contenido, `targetSdk`.
  - ✅ Checklist completa y revisada por Johan.
  - _Avance (2026-11-18):_ `specs/release-checklist.md` preparada: cuenta, compilación de release, ficha en los dos idiomas, política de privacidad propuesta, formulario de seguridad de datos, declaraciones de permisos (`USE_FULL_SCREEN_INTENT`, `USE_EXACT_ALARM` solo para T-54), clasificación y lo que hay que comprobar antes de cada subida. Marca como **Decisión** lo que depende de Johan (R8, correo público, dónde publicar la política, cómo presentar "medicación"). Falta su revisión: la casilla sigue sin marcar.
- [ ] **T-54 · Prueba cerrada** — (sin requisito de producto)
  - Pista cerrada con al menos 12 testers aceptados durante 14 días seguidos (cuenta personal posterior al 13-11-2023), con margen de testers.
  - Métrica: porcentaje de testers que programan un segundo plan; recoger diagnósticos exportados voluntarios.
  - D-69 (opción C): subir a la pista cerrada una versión con `USE_EXACT_ALARM` (`SCHEDULE_EXACT_ALARM` hasta `maxSdkVersion 32`) y su declaración en Play Console; anotar el resultado de la revisión y decidir con Johan si va a producción o se queda la petición guiada.
  - ✅ Resultado, aprendizajes y decisión final de D-69 anotados en `specs/` antes de empezar la Fase 13.
  - _Pendiente (2026-11-18):_ necesita Play Console, testers reales y 14 días; no se puede hacer desde el repositorio. La Fase 13 se empezó antes por petición de Johan: sus funciones no dependen del resultado de D-69, que solo cambia el manifiesto.

## Fase 13 · Nicho de cuidados (v1.4)

> Dependencias de D-75 (ZXing `core`, Google code scanner) y D-79 (Glance) aprobadas el 2026-11-16; D-73 y D-76 también.

- [x] **T-55 · Mover alarmas en bloque (dominio)** — (R-18.3–R-18.8)
  - `ShiftOrderAlarmsUseCase` (TDD, D-73): cruzar medianoche hacia delante y hacia atrás, pospuestas, pasado, fuera de 0..30, plan pausado, estados que no cambian, idempotencia de la reprogramación.
  - ✅ Tests en verde con `Clock.fixed` y zona explícita.
  - _Hecho:_ `ShiftOrderAlarmsUseCase` (vista previa y cambio, todo o nada) y `AlarmRepository.updateSchedules` en una transacción (D-73). 13 tests nuevos (700 en verde): medianoche hacia delante y hacia atrás, pospuestas, pasado, fuera del día 1–31, plan pausado sin programar, estados que no cambian, plan no permitido, vista previa sin cambios e idempotencia con alarmas críticas.
- [x] **T-56 · "Voy tarde" en el detalle** — (R-18.1, R-18.2)
  - Diálogo con sentido y minutos, vista previa y mensajes de error; mensaje "{n} alarmas movidas".
  - Tests de VM; previews claro/oscuro.
  - ✅ Acción visible solo en planes *Activos* o *Pausados*.
  - _Hecho:_ botón "Voy tarde" en la cabecera y diálogo "Mover las alarmas" con "Retrasar"/"Adelantar", minutos, vista previa y errores; snackbar "{n} alarmas movidas" (D-91). Textos en español e inglés. 6 tests nuevos (706 en verde), preview del diálogo. En el emulador: "1 alarma; la próxima pasa de las 23:21 a las 23:51" y, tras "Mover", la alarma queda a las 23:51.
- [x] **T-57 · Editar una alarma** — (R-18.9, R-18.10)
  - `UpdateAlarmUseCase` (D-74); formulario reutilizado de Revisión.
  - ✅ Solo *Pendientes*; hora pasada rechazada con su mensaje.
  - _Hecho:_ `UpdateAlarmUseCase` con las reglas de Revisión y "Esa hora ya pasó; elige una hora futura"; `AlarmRepository.getByOrder` y `updateDetails`; icono de editar en cada pendiente y `EditActivityDialog` compartido con Revisión (D-74). 13 tests nuevos (719 en verde). En el emulador, el icono abre "Editar actividad" con la hora, el título y la categoría de la alarma.
- [x] **T-58 · Exportar el plan como texto** — (R-19.1–R-19.3)
  - `ExportPlanUseCase`: el JSON exportado vuelve a importarse igual (test de ida y vuelta, también con `assignee` y varios días).
  - ✅ "Compartir como texto" abre el selector con el bloque de código.
  - _Hecho:_ `PlanExporter`/`JsonPlanExporter`, `ExportPlanUseCase` y "Compartir plan" → "Compartir como texto" en el detalle (D-75). Tests de ida y vuelta (con `assignee`, descripción, duración y siete días) y de VM. 4 tests nuevos (723 en verde). En el emulador el selector recibe "Importa este plan en Rutinify" con el JSON en un bloque de código.
- [x] **T-59 · Enlace y QR** — (R-19.4, R-19.5, R-19.7, R-19.8)
  - `EncodePlanLinkUseCase`/`DecodePlanLinkUseCase` (TDD: ida y vuelta, límite de 2.331 bytes, enlace dañado); `intent-filter` `rutinify://import`; QR con ZXing (D-75).
  - ✅ Un plan que no cabe muestra el mensaje de R-19.5.
  - _Hecho:_ `PlanLink`, `EncodePlanLinkUseCase`, `DecodePlanLinkUseCase`, ZXing `core` (§13, ya aprobada; `CLAUDE.md` §9 actualizado con las dependencias aprobadas) e `intent-filter` `rutinify://import`; "Mostrar QR" en "Compartir plan" y R-19.5 con "Compartir como texto" (D-75). 11 tests nuevos (734 en verde), entre ellos uno que lee el QR generado con el lector de ZXing. En el emulador: el QR del plan se ve; `am start … rutinify://import?…` abre *Revisión* con el plan y un enlace dañado muestra "Este código no contiene un plan de Rutinify".
- [x] **T-60 · Escanear QR y filtro por responsable** — (R-19.6, R-19.9, R-19.10)
  - "Escanear QR" con Google code scanner; filtro "Solo las tareas de…" en *Revisión* (D-76).
  - Tests de VM del filtro (con y sin `assignee`, "Todas" restaura).
  - ✅ El manifiesto no gana `CAMERA` ni `INTERNET`.
  - _Hecho:_ "Escanear QR" en *Nueva rutina* con Google code scanner (sin `CAMERA`; el `INTERNET` que trae la librería se quita del manifiesto, D-75) y filtro "Solo las tareas de…" en *Revisión* (D-76). 8 tests nuevos (742 en verde), preview del filtro. En el emulador, "Escanear QR" abre el escáner de Google ("Scanned by Google on behalf of Rutinify").
- [x] **T-61 · Eventos del historial** — (R-20.1)
  - Room v4: `alarm_event` con migración que crea eventos desde `done_at` y *Omitidas* (D-77, D-83); escritura en la misma transacción que el estado.
  - ✅ Test de migración y de cada use case que escribe eventos.
  - _Hecho:_ Room v4 con `alarm_event` y `Migration(3, 4)` que rellena desde `done_at` y las *Omitidas*; eventos en la misma transacción que el estado (D-77). 6 tests nuevos (748 en verde): migración, repositorio, cascada al borrar el plan y "Hecho", "+5 min" y reanudar. En el emulador, instalar encima migró la base a la versión 4 con los 20 planes y 28 eventos rellenados.
- [x] **T-62 · Resumen y exportación** — (R-20.2–R-20.6)
  - `BuildHistoryReportUseCase` (TDD); texto; PDF con `PdfDocument` y `FileProvider` (D-78).
  - ✅ "19 de 21 hechas" correcto con canceladas, omitidas y pospuestas; PDF abre en un visor.
  - _Hecho:_ `BuildHistoryReportUseCase`, `HistoryText`, `ExportHistoryPdfUseCase` con `PdfDocument` y `FileProvider`; resumen en la cabecera del detalle y "Exportar historial" → "Texto"/"PDF" (D-78). Textos en español e inglés. 11 tests nuevos (759 en verde). En el emulador: "0 de 2 hechas · 1 omitida · 0 pospuestas" en el detalle; "PDF" crea `historial.pdf` (31 KB), el selector lo ofrece y el PDF se lee con el plan, el resumen y cada alarma.
- [x] **T-63 · Widget "Lo siguiente"** — (R-21.1–R-21.5)
  - Glance (D-79), `ObserveNextAlarmUseCase`, `WidgetUpdater`; botón "Hecho".
  - Tests de use case; prueba manual de actualización.
  - ✅ El widget cambia al disparar, marcar, posponer y mover.
  - _Hecho:_ Glance 1.1.1 (§13, aprobada), `ObserveNextAlarmUseCase` sobre `AlarmItemDao.observeUpcoming()`, `NextAlarmWidget` con "Hecho" y `WidgetRefresher` (D-79, D-92). Textos en español e inglés. 6 tests nuevos (765 en verde). En el emulador: el widget se coloca desde el selector, muestra "Lo siguiente · 23:28 · Paseo · Prueba editar" y, tras "Hecho", pasa a la siguiente alarma.
- [ ] **T-64 · Verificación v1.4** — (R-13.5)
  - `koverVerify` ≥ 80 %; pasos 29–31 de `manual-test.md` (QR entre dos teléfonos, plan grande, filtro).
  - ✅ Resultados anotados.
  - _Avance (2026-11-18):_ `koverVerify` en verde. Pasos 30 y 31 superados en el emulador; el 29 necesita dos teléfonos reales. La casilla sigue sin marcar hasta repetirlos en teléfonos reales.

## Fase 14 · Diferenciación (v2.0)

> T-65 decide si R-22 sigue adelante tal como está (D-80, única decisión abierta del roadmap). D-81 aprobada el 2026-11-16. R-23 cambia el contrato: la plantilla y el README se actualizan en la misma tarea que el validador.

- [x] **T-65 · Spike de Gemini Nano** — (R-22.7)
  - Rama de prueba con `genai-prompt`: revisar el manifiesto fusionado (`INTERNET` y otros permisos), tamaño del APK y comportamiento en un dispositivo compatible y en uno no compatible.
  - ✅ Resultado anotado en D-80 y decisión de Johan registrada; la rama no se fusiona.
  - _Hecho:_ prueba sin commit (no se fusionó nada): `genai-prompt` no añade `INTERNET`, solo el permiso de AICore; +11,4 MB en el APK de depuración. Se cumple la condición que Johan puso el 2026-11-16, así que la dependencia queda aprobada (D-80). Falta probar en un teléfono compatible con Gemini Nano. De paso: `glance-appwidget` (T-63) trae WorkManager con `WAKE_LOCK` y `FOREGROUND_SERVICE`, solo para dibujar el widget; anotado en la checklist de publicación.
- [x] **T-66 · Convertir en el teléfono** — (R-22.1–R-22.6)
  - `OnDeviceRoutineImporter`; opción visible solo con `AVAILABLE`; cancelar; límite de entrada; mensajes de error.
  - Tests con un fake del modelo; prueba manual en dispositivo compatible.
  - ✅ Sin modelo, *Nueva rutina* se ve igual que antes.
  - _Hecho:_ `genai-prompt` 1.0.0-beta4 (aprobada tras T-65), `OnDeviceModel`/`MlKitOnDeviceModel`, `ConvertOnDeviceUseCase` y la tarjeta "Convertir en el teléfono" con "Convirtiendo…" y "Cancelar" (D-80, D-93). Textos en español e inglés. 9 tests nuevos (774 en verde) con un modelo falso. En el emulador (sin Gemini Nano) la tarjeta no aparece y no hay errores. Falta probarla en un teléfono compatible.
- [ ] **T-67 · Validador del schema v2** — (R-23.1–R-23.6)
  - TDD: v1 sin cambios; v2 con `weekdays` y `until`; cada error de R-23.3–R-23.6; R-3.4 con "1 o 2".
  - Plantillas con un ejemplo v2 opcional; README del contrato.
  - ✅ Fixtures v2 válidos e inválidos.
- [ ] **T-68 · Planes recurrentes y ventana de programación** — (R-23.7, R-23.10–R-23.14)
  - Room v5 (D-81, D-83); `MaterializeRecurringUseCase` (TDD: idempotente, ventana de 7 días, pausa y reanudación, `until`); alarma diaria `MATERIALIZE`; textos de recurrencia; *Revisión* con el día tipo y "{n} alarmas por semana" (R-23.14).
  - ✅ Reiniciar o cambiar de zona no duplica alarmas.
- [ ] **T-69 · Conflictos y límite con recurrencia** — (R-23.8, R-23.9)
  - `RoutineSpan` con fin abierto; conflictos por día de la semana; límite de 500 solo con la ventana.
  - ✅ Tests de conflicto recurrente contra plan de un día, de varios días y otro recurrente.
- [ ] **T-70 · Wear OS con notificaciones puenteadas** — (R-24.1–R-24.3)
  - Revisar `setLocalOnly` y acciones (D-82); prueba en emulador de Wear emparejado.
  - ✅ "Hecho", "+5 min" y "Silenciar" funcionan desde el reloj.
- [ ] **T-71 · Verificación v2.0** — (R-13.5, R-13.6)
  - `koverVerify` ≥ 80 %; manifiesto revisado contra R-13.6 y D-80; casos manuales nuevos de recurrencia.
  - ✅ Resultados anotados.

## Trazabilidad rápida

| Requisito | Tareas |
|---|---|
| R-1 Plantilla | T-11, T-21, T-42 |
| R-2 Entrada | T-21 |
| R-3 Validación | T-08, T-09, T-10, T-11, T-12, T-33 |
| R-4 Revisión | T-13, T-14, T-22, T-35, T-38 |
| R-5 Programación | T-13, T-15, T-16, T-20, T-22 |
| R-6 Reemplazo | T-06, T-20, T-22, T-34 |
| R-7 Notificación | T-16, T-17, T-18, T-24 |
| R-8 Inicio | T-04, T-06, T-23, T-36 |
| R-9 Detalle | T-24, T-25, T-36 |
| R-10 Gestión de orden | T-26, T-27, T-28, T-36 |
| R-11 Permisos | T-19, T-29, T-30, T-48 |
| R-12 Robustez | T-13, T-19 |
| R-13 No funcionales | T-11, T-12, T-31, T-32, T-37, T-39, T-49, T-50, T-64, T-71 |
| R-14 Entrada compartida (v1.2) | T-40, T-41 |
| R-15 Modo alarma real (v1.2) | T-43, T-44, T-45, T-46, T-47, T-48 |
| R-16 Plantillas por escenario (v1.2) | T-42 |
| R-17 Diagnóstico local (v1.3) | T-51, T-52 |
| R-18 Reprogramar en bloque (v1.4) | T-55, T-56, T-57 |
| R-19 Compartir sin servidor (v1.4) | T-58, T-59, T-60 |
| R-20 Historial y exportación (v1.4) | T-61, T-62 |
| R-21 Widget (v1.4) | T-63 |
| R-22 IA en el dispositivo (v2.0) | T-65, T-66 |
| R-23 Schema v2 y recurrencia (v2.0) | T-67, T-68, T-69 |
| R-24 Wear OS (v2.0) | T-70 |
