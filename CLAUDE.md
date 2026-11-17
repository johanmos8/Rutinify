# CLAUDE.md — Rutinify

Reglas del proyecto para cualquier agente (Claude Code, Kiro…) o persona que trabaje en este repo.
Léelo completo antes de tocar código. La fuente de verdad funcional está en `specs/`.

## 1. Qué es

**Rutinify** convierte una rutina en JSON (generada por una IA externa vía copiar/pegar) en alarmas
locales programadas con `AlarmManager`. Cada importación es una **Orden** (`RoutineOrder`) con sus
**Alarmas** (`AlarmItem`). Sin backend, sin login, sin llamadas de red.

- Paquete / applicationId: `com.mirkwood.rutinify`
- `minSdk 28`, `targetSdk 36`, `compileSdk 36`
- AGP 9.x (Kotlin integrado en AGP: **no** añadir el plugin `org.jetbrains.kotlin.android`)
- Spec: `specs/requirements.md` (QUÉ), `specs/design.md` (CÓMO), `specs/tasks.md` (ORDEN DE TRABAJO)

## 2. Forma de trabajo (Spec-Driven Development)

1. No se escribe código de producción que no esté respaldado por un requisito (`R-x.y`) y una tarea (`T-xx`).
2. Se implementa **una tarea a la vez**, en el orden de `specs/tasks.md`.
3. Ciclo por tarea: tests primero (cuando aplique) → implementación → compilar → ejecutar tests →
   marcar la casilla en `tasks.md` → resumen de 2–3 líneas → commit.
4. **Una tarea solo está hecha si** `assembleDebug`, `testDebugUnitTest` y `lintDebug` pasan.
   Si algo falla, no se avanza a la siguiente tarea.
5. Si algo es ambiguo y afecta al comportamiento visible o al modelo de datos → **preguntar**.
   Si es menor → decidir, anotarlo en `specs/design.md` §14 *Registro de decisiones* y seguir.
6. Si la implementación obliga a cambiar la spec, se actualiza la spec **en el mismo commit**.

### Comandos

```bash
./gradlew assembleDebug            # compilar   (Windows: gradlew.bat assembleDebug)
./gradlew testDebugUnitTest        # unit tests (JVM, incluye Room vía Robolectric)
./gradlew lintDebug                # lint
./gradlew koverVerify              # cobertura mínima (dominio + importer ≥ 80 %) — disponible desde T-32
./gradlew connectedDebugAndroidTest # instrumentados (solo cuando la tarea lo pida; requiere emulador)
```

## 3. Arquitectura

MVVM + capa de dominio (use cases). Clean Architecture **ligera**: un solo módulo `:app`, separación por paquetes.

```
com.mirkwood.rutinify
├── RutinifyApp.kt              @HiltAndroidApp
├── MainActivity.kt             única Activity, aloja el NavHost
├── di/                         módulos Hilt (DatabaseModule, AlarmModule, TimeModule, DispatchersModule…)
├── domain/                     Kotlin puro: SIN imports de android.*
│   ├── model/                  RoutineOrder, AlarmItem, AlarmStatus, OrderStatus, RoutineDraft…
│   ├── importer/               ImportResult, ValidationIssue, interfaz RoutineImporter
│   ├── repository/             interfaces OrderRepository, AlarmRepository
│   ├── scheduler/              interfaz AlarmScheduler, NotificationPublisher, PermissionChecker
│   ├── time/                   TriggerTimeCalculator, DefaultDatePolicy
│   └── usecase/                un caso de uso por acción (operator fun invoke)
├── data/
│   ├── local/                  RutinifyDatabase, DAOs, entidades, converters
│   ├── repository/             implementaciones de repositorios
│   ├── importer/               JsonExtractor, RoutineJsonParser (kotlinx.serialization), DTO→dominio
│   ├── alarm/                  AndroidAlarmScheduler (AlarmManager), PendingIntentFactory
│   ├── notification/           NotificationChannels, AndroidNotificationPublisher
│   ├── receiver/               AlarmReceiver, NotificationActionReceiver, RescheduleReceiver
│   └── system/                 ClipboardReader, AndroidPermissionChecker
└── ui/
    ├── theme/
    ├── navigation/             rutas type-safe (@Serializable) + RutinifyNavHost
    ├── common/                 componentes reutilizables, mapeo de errores → strings
    ├── home/  newroutine/  review/  orderdetail/  permissions/
```

Reglas de dependencia:
- `ui → domain`, `data → domain`. **`domain` no depende de nada** del proyecto ni de Android.
- La UI nunca habla con DAOs, `AlarmManager` ni `Context` de sistema directamente: siempre vía use case.
- Los receivers son finos: obtienen dependencias por Hilt y delegan en un use case.

## 4. Convenciones de nombres

| Elemento | Convención | Ejemplo |
|---|---|---|
| Entidad Room | `XxxEntity` | `AlarmItemEntity` |
| DAO | `XxxDao` | `AlarmItemDao` |
| DTO JSON | `XxxDto` | `RoutineItemDto` |
| Modelo de dominio | sustantivo sin sufijo | `AlarmItem` |
| Use case | verbo + objeto + `UseCase` | `CancelOrderAlarmsUseCase` |
| ViewModel | `XxxViewModel` | `ReviewViewModel` |
| Estado UI | `XxxUiState` (data class inmutable) | `ReviewUiState` |
| Eventos UI → VM | `XxxAction` (sealed interface) | `ReviewAction.RemoveItem(id)` |
| Efectos únicos VM → UI | `XxxEffect` (sealed interface) | `ReviewEffect.NavigateToOrder(id)` |
| Pantalla con estado | `XxxRoute` | `ReviewRoute(viewModel)` |
| Pantalla sin estado | `XxxScreen` | `ReviewScreen(state, onAction)` |
| Tests | `<Clase>Test`, método en backticks | `` `rechaza time con formato 25:00`() `` |

- Código, identificadores y commits en **inglés**. Textos de UI, mensajes de error y specs en **español**.
- **KDoc y comentarios en inglés** (también en tests, `build.gradle.kts` y XML de recursos). Los textos de UI que se citen
  van entre comillas tal cual (`"Deshacer"`), y las referencias a requisitos/decisiones (`R-5.7`, `D-28`) se mantienen.
  Excepción: los **nombres de los tests** siguen en español (§8).
- Todos los textos visibles en `res/values/strings.xml` (plurales en `plurals`). Nada de strings literales en composables.
- Las plantillas de prompt viven en `res/raw/prompt_template_<escenario>.txt` (una por escenario, R-16).

## 5. Manejo de errores

- Errores **esperados** (JSON inválido, permiso denegado, conflicto de fechas) se modelan como tipos, no como excepciones:
  - `sealed interface ImportResult { Success(draft, warnings) ; Failure(issues) }`
  - `ValidationIssue(itemIndex: Int?, field: String?, kind: IssueKind, severity: Severity)`
  - Para operaciones simples se usa `kotlin.Result<T>`; para dominios con varios fallos conocidos, `sealed interface`.
- El dominio **nunca** devuelve strings para mostrar: devuelve tipos; la UI los traduce con `ui/common/ErrorMessages.kt` a `stringResource`.
- Excepciones solo para errores de programación o de sistema inesperados. Se capturan en el borde (repositorio / receiver), se registran con `Log` (tag `Rutinify`) y se convierten a un error tipado.
- Prohibido `!!` salvo en tests. Prohibido `catch (e: Exception)` vacío.
- Las coroutines nunca tragan `CancellationException` (re-lanzarla si se usa `runCatching`).

## 6. Coroutines y Flow

- Coroutines + Flow. **No RxJava** en este proyecto.
- Dispatchers inyectados (`@IoDispatcher`, `@DefaultDispatcher`); nunca `Dispatchers.IO` hardcodeado en clases de producción.
- La hora se inyecta como `java.time.Clock` (nunca `System.currentTimeMillis()` / `LocalDateTime.now()` sin clock) para poder testear.
- Los DAOs exponen `Flow` para observar y `suspend` para escribir.
- ViewModels: `StateFlow<UiState>` con `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`;
  efectos únicos con `Channel<Effect>(Channel.BUFFERED).receiveAsFlow()`.
- `BroadcastReceiver`: `goAsync()` + scope de aplicación inyectado (`@ApplicationScope`) + `withTimeout(9s)`; siempre `pendingResult.finish()` en `finally`.

## 7. Reglas de Compose

- **State hoisting**: `XxxRoute` recoge el estado del VM (`collectAsStateWithLifecycle`) y llama a `XxxScreen(state, onAction)`, que no tiene estado.
- **Sin lógica en composables**: nada de formateo de negocio, validaciones, cálculos de fechas ni acceso a repositorios. Solo layout y emitir acciones.
  El formateo de presentación (ej. `"06:45"`) se calcula en el VM o en un mapper de UI testeable.
- Cada `XxxScreen` y cada componente reutilizable tiene al menos un `@Preview` (claro y oscuro) con datos falsos de `ui/preview/PreviewData.kt`.
- Material 3, colores y tipografía solo desde `MaterialTheme`. Sin colores hardcodeados.
- Listas con `LazyColumn` y `key = { it.id }`.
- Accesibilidad: `contentDescription` en iconos accionables y objetivos táctiles ≥ 48 dp.
- `Modifier` como primer parámetro opcional de cada composable público.

## 8. Tests

- Frameworks: JUnit 4, **Truth** para aserciones, **Turbine** para Flows, `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`),
  Robolectric para DAO Room in-memory y receivers en JVM. Fakes escritos a mano (`FakeAlarmScheduler`, `FakeOrderRepository`) en vez de mocks.
- **TDD obligatorio** para: extractor/parser/validador JSON, cálculo de horarios, políticas de fecha, use cases.
- Cada caso borde de `requirements.md` tiene al menos un test que lo nombra (`// R-3.4` en el test).
- Cobertura objetivo ≥ 80 % en `domain/**` y `data/importer/**` (verificado con Kover). La UI se cubre con previews y algún test de Compose donde aporte.
- Tests deterministas: `Clock.fixed(...)` y `ZoneId` explícita. Prohibido `Thread.sleep`.
- Nombres descriptivos en español con backticks.

## 9. Dependencias

- Se gestionan **solo** en `gradle/libs.versions.toml`.
- **No añadir dependencias sin justificar**: cada nueva dependencia necesita una línea en `specs/design.md` §13 (qué aporta, por qué no basta lo existente).
- Aprobadas por la spec: Hilt (+ hilt-navigation-compose), KSP, Room, kotlinx.serialization, Navigation Compose, lifecycle-runtime-compose,
  Truth, Turbine, kotlinx-coroutines-test, Robolectric, Kover. Cualquier otra → preguntar.

## 10. Alarmas y notificaciones (reglas duras)

- Disparar avisos **solo** con `AlarmManager.setExactAndAllowWhileIdle`, o `setAlarmClock` si la categoría es crítica (R-15.3); con `setWindow` si no hay permiso de alarma exacta. **Nunca WorkManager** para disparar.
- El `requestCode` de cada `PendingIntent` = `AlarmItem.id` (ver `design.md` §6). No inventar otros esquemas.
- Room es la fuente de verdad: primero se escribe en Room, luego se toca `AlarmManager`. Toda operación de reprogramación debe ser **idempotente**.
- Toda notificación usa el canal de su categoría (`cat_<categoria>`). Excepción (D-67): las alarmas de categorías críticas usan `alarm_critical` (sonido de alarma) y, silenciadas, `alarm_critical_silent`, porque el sonido de un canal no se puede cambiar después de crearlo.

## 11. Git

- Commits pequeños, **uno por tarea** (o varios si la tarea es grande), formato Conventional Commits con la tarea:
  `feat(T-08): add tolerant JSON extractor`, `test(T-09): cover validator edge cases`, `chore(T-01): add Hilt and Room`.
- No mezclar refactors no relacionados con la tarea.
- Nunca commitear `local.properties`, keystores ni builds.

## 12. Definición de Hecho (DoD) por tarea

- [ ] Tests nuevos escritos y en verde
- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` en verde
- [ ] Sin warnings nuevos de compilación
- [ ] Strings en recursos, previews en pantallas nuevas
- [ ] Casilla marcada en `specs/tasks.md` + resumen de 2–3 líneas
- [ ] Spec actualizada si cambió algo
