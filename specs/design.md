# Diseño técnico — Rutinify (MVP)

> Referencia: `requirements.md` (ids `R-x.y`). Las decisiones menores tomadas sin consultar están en §14 *Registro de decisiones*.

## 1. Visión general

```
┌──────────────────────────── ui (Compose + ViewModels) ────────────────────────────┐
│ Home · NewRoutine · Review · OrderDetail · Permissions                            │
└───────────────┬───────────────────────────────────────────────────────────────────┘
                │ use cases (suspend / Flow)
┌───────────────▼──────────── domain (Kotlin puro) ─────────────────────────────────┐
│ modelos · use cases · TriggerTimeCalculator · DefaultDatePolicy                   │
│ interfaces: RoutineImporter, OrderRepository, AlarmRepository,                    │
│             AlarmScheduler, NotificationPublisher, PermissionChecker, DraftStore  │
└───────▲───────────────▲──────────────────▲───────────────────▲────────────────────┘
        │               │                  │                   │
┌───────┴──────┐ ┌──────┴───────┐ ┌────────┴────────┐ ┌────────┴──────────┐
│ data/importer│ │ data/local   │ │ data/alarm      │ │ data/notification │
│ JsonExtractor│ │ Room DB/DAOs │ │ AlarmManager    │ │ canales, publish  │
│ Parser       │ │ repos impl   │ │ PendingIntents  │ │                   │
└──────────────┘ └──────────────┘ └────────▲────────┘ └───────────────────┘
                                           │ broadcasts
                                  ┌────────┴──────────────────────────────┐
                                  │ data/receiver                         │
                                  │ AlarmReceiver · NotificationAction-   │
                                  │ Receiver · RescheduleReceiver         │
                                  └───────────────────────────────────────┘
```

**Principio rector:** Room es la fuente de verdad. `AlarmManager` es una proyección de Room que
se puede reconstruir en cualquier momento (`RescheduleAllUseCase`) de forma idempotente.

## 2. Stack y configuración de build

| Ítem | Valor |
|---|---|
| Módulos | uno: `:app` |
| AGP / Gradle | 9.1.1 / 9.3.1 (Kotlin integrado en AGP, sin plugin `kotlin-android`) |
| Kotlin | 2.3.21 (subido desde 2.2.10 en T-01, ver D-18) · plugins `kotlin.compose`, `kotlin.serialization` |
| SDK | `minSdk 28`, `targetSdk 36`, `compileSdk 36` (ver D-01) |
| JVM target | 17 (subir desde 11; lo exigen Hilt/Robolectric recientes) |
| Annotation processing | KSP (Room, Hilt). Sin kapt. |
| DI | Hilt + `hilt-navigation-compose` |
| Persistencia | Room (runtime, ktx, compiler vía KSP), esquema exportado en `app/schemas/` |
| JSON | kotlinx.serialization-json (se usa `JsonElement`, ver §4.3) |
| Navegación | Navigation Compose con rutas type-safe (`@Serializable`) |
| Compose | BOM: la última estable compatible con `compileSdk 36` (ver D-20) |
| Tests | JUnit4, Truth, Turbine, kotlinx-coroutines-test, Robolectric, Room testing, Kover |

Versiones fijadas en T-01 (`gradle/libs.versions.toml`):

| Librería | Versión | Nota |
|---|---|---|
| KSP | 2.3.13 | desde 2.3.0 se versiona aparte de Kotlin; ≥ 2.3.1 soporta el Kotlin integrado de AGP 9 |
| Hilt (Dagger) | 2.60.1 | ≥ 2.59 exige AGP 9; 2.60 sube el minSdk de Hilt a 23 |
| androidx.hilt (navigation-compose) | 1.3.0 | 1.4.0 exige `compileSdk 37` (D-20) |
| Room (+ plugin `androidx.room`) | 2.8.5 | esquemas en `app/schemas` |
| Navigation | 2.9.8 | 2.10.x exige `compileSdk 37` (D-20) |
| Lifecycle | 2.10.0 | 2.11.0 exige `compileSdk 37` (D-20) |
| Compose BOM | 2026.06.01 | Compose 1.11.4 / Material3 1.4.0; Compose 1.12 (BOM ≥ 2026.08) exige `compileSdk 37` (D-20) |
| core-ktx | 1.18.0 | 1.19.x exige `compileSdk 37` (D-20) |
| kotlinx.serialization | 1.11.0 | |
| Coroutines | 1.10.2 | |
| Truth / Turbine / Robolectric | 1.4.4 / 1.2.1 / 4.16 | |
| Kover | se añade en T-32 | se aplaza para no sumar riesgo de compatibilidad con AGP 9 en el setup |

Ver riesgo K-1 en §16.

## 3. Modelo de dominio

```kotlin
// domain/model
data class RoutineOrder(
    val id: Long,
    val name: String,
    val date: LocalDate,
    val notes: String?,
    val status: OrderStatus,          // ACTIVE, PAUSED, CANCELLED
    val createdAt: Instant,
)
enum class OrderStatus { ACTIVE, PAUSED, CANCELLED }

// Estado de presentación derivado (R-8.3): no se guarda
enum class OrderDisplayStatus { ACTIVE, PAUSED, CANCELLED, FINISHED }

data class AlarmItem(
    val id: Long,
    val orderId: Long,
    val position: Int,                // índice original en el JSON (0-based)
    val time: LocalTime,              // hora de pared original
    val dayOffset: Int,               // 0..1
    val title: String,
    val description: String?,
    val durationMin: Int?,
    val category: String,             // normalizada, "general" por defecto
    val assignee: String?,
    val status: AlarmStatus,
    val snoozedUntil: Instant?,       // si no es null, manda sobre la hora calculada
    val statusChangedAt: Instant,
    val firedAt: Instant? = null,     // reflejo de las columnas fired_at / done_at (D-28)
    val doneAt: Instant? = null,
)
enum class AlarmStatus { PENDING, FIRED, DONE, SKIPPED, CANCELLED }

data class OrderSummary(val order: RoutineOrder, val pending: Int, val total: Int) {
    val displayStatus: OrderDisplayStatus get() = …   // ACTIVE && pending == 0 → FINISHED
}
data class OrderWithAlarms(val order: RoutineOrder, val alarms: List<AlarmItem>)

// Borrador (no persistido en Room)
data class RoutineDraft(
    val id: String,                   // UUID, clave en DraftStore
    val name: String,
    val date: LocalDate?,             // null si el JSON no la traía
    val notes: String?,
    val items: List<DraftItem>,
    val origin: DraftOrigin,          // IMPORT | DUPLICATE
)
data class DraftItem(
    val key: Int,                     // estable dentro del borrador (= posición original)
    val time: LocalTime, val dayOffset: Int,
    val title: String, val description: String?, val durationMin: Int?,
    val category: String, val assignee: String?,
)
```

### Transiciones de estado de `AlarmItem`

```
           ┌────────── snooze (+5) ──────────┐
           ▼                                 │
       PENDING ──dispara──► FIRED ──"Hecho"──► DONE
        │  │                  │
        │  │                  └──"Hecho" manual──► DONE
        │  ├──pasada/excluida al importar, >15 min tarde al reprogramar, reanudar tras pausa──► SKIPPED
        │  └──"Eliminar todas"/"Reemplazar"──► CANCELLED
        └──"Hecho" manual──► DONE
```
Eliminar una alarma individual la **borra** de Room (R-9.3); no es un estado.

## 4. Contrato JSON v1 y mapeo

### 4.1 Contrato

Igual al definido en el prompt del producto (`schemaVersion`, `routine{name,date,notes}`, `items[]{time,dayOffset,title,description,durationMin,category,assignee}`).
La copia canónica vive en `app/src/main/res/raw/prompt_template.txt` y en el fixture de tests `src/test/resources/fixtures/valid_v1.json`;
un test verifica que el ejemplo embebido en la plantilla **parsea sin errores** (evita que diverjan).

### 4.2 Extracción tolerante — `JsonExtractor`

1. Si hay bloques ```` ``` ```` (con o sin `json`), probar primero su contenido, en orden.
2. Si no, escanear el texto: por cada `{` candidato, avanzar contando llaves **respetando strings y escapes** hasta cerrar el objeto; intentar `Json.parseToJsonElement`. El primero que sea un `JsonObject` gana.
3. Normalizar antes de parsear: quitar BOM, comillas tipográficas `“ ” ‘ ’` **fuera** de strings no se tocan (si el parse falla, error R-3.3).
4. **Límites (R-3.23, D-32):** como mucho 20 candidatos por texto (bloques + escaneo; cada `{` probado cuenta), y un candidato con más de 32 niveles de anidamiento se descarta sin pasarlo a `kotlinx`. El coste queda acotado a O(20·n). El límite de 100 KB (R-3.20) lo aplica el importer antes de llamar al extractor.
5. Si ningún bloque contiene un objeto válido, se escanea el texto completo. Un candidato **cerrado pero inválido** se salta entero (no se devuelven objetos anidados dentro de él); uno **sin cerrar** avanza un carácter, para poder encontrar un objeto válido posterior (D-30).
6. Resultado: `ExtractedJson(json: JsonObject, text: String)?`: el texto exacto del candidato se guarda como `raw_json`. Complejidad O(n·k) con k candidatos; para 200 items es despreciable (R-13.2).

### 4.3 Parser + validador — `RoutineJsonParser`

Se parsea a `JsonElement` y se recorre **a mano** en vez de deserializar directamente a DTOs con `@Serializable`, porque
la deserialización automática falla en el primer error con mensajes técnicos; aquí necesitamos **todos** los errores,
por item y campo, en español (R-3.13, R-3.16). Los campos desconocidos simplemente no se leen (R-3.2).

```kotlin
sealed interface ImportResult {
    data class Success(val draft: RoutineDraft, val warnings: List<ValidationIssue>) : ImportResult
    data class Failure(val issues: List<ValidationIssue>) : ImportResult
}
data class ValidationIssue(
    val itemIndex: Int?,            // 1-based; null = nivel rutina
    val itemTitle: String?,         // para el mensaje "Actividad 3 («Siesta»)"
    val itemTime: String?,
    val field: String?,             // "time", "routine.name"…
    val kind: IssueKind,
    val received: String? = null,   // valor recibido (recortado a 30 chars)
    val expected: ValueType? = null, // tipo esperado en WRONG_TYPE: TEXT, WHOLE_NUMBER, OBJECT, LIST (R-3.13, D-31)
    val limit: Int? = null,          // máximo permitido en TEXT_TOO_LONG / TOO_MANY_CATEGORIES (R-3.21, R-3.22, D-33)
    val severity: Severity = Severity.ERROR,
)
enum class IssueKind {
    NO_JSON_FOUND, UNSUPPORTED_SCHEMA_VERSION, MISSING_FIELD, BLANK_FIELD, WRONG_TYPE,
    INVALID_DATE, INVALID_TIME, DAY_OFFSET_OUT_OF_RANGE, DURATION_OUT_OF_RANGE,
    NO_ITEMS, TOO_MANY_ITEMS, TITLE_TRUNCATED, /* warnings */
    INPUT_TOO_LONG, TEXT_TOO_LONG, TOO_MANY_CATEGORIES,   // R-3.20–R-3.22 (D-32)
}
```

Reglas (→ requisito):

| Campo | Regla | Resultado |
|---|---|---|
| `schemaVersion` | entero == 1 | si no → `UNSUPPORTED_SCHEMA_VERSION`, se aborta el resto (R-3.4) |
| `routine` | objeto | si falta → `MISSING_FIELD routine`; si no es objeto → `WRONG_TYPE` |
| `routine.name` | string no vacío (trim) | R-3.5 |
| `routine.date` | `null`/ausente o `LocalDate.parse` ISO estricto | R-3.6 |
| `routine.notes` | string opcional; vacío → null; ≤ 500 | otro tipo → `WRONG_TYPE`; más largo → `TEXT_TOO_LONG` (R-3.21) |
| `items` | array no vacío, ≤ 200 | R-3.7, R-3.18 |
| `time` | regex `^([01]?\d|2[0-3]):[0-5]\d$`, se normaliza a `HH:mm` | R-3.8 |
| `dayOffset` | ausente/null → 0; entero 0..1 | R-3.11, R-3.15 |
| `title` | string no vacío; > 80 → recorte + warning `TITLE_TRUNCATED` | R-3.9, R-3.10 |
| `description` | string opcional; vacío → null; ≤ 500 | `TEXT_TOO_LONG` (R-3.21) |
| `durationMin` | null o entero 1..1440 | R-3.12 |
| `category` | null/vacío → `general`; `trim().lowercase()`, espacios → `-`; ≤ 30 tras normalizar | R-3.14; `TEXT_TOO_LONG` (R-3.21) |
| categorías distintas | ≤ 10 en toda la rutina | `TOO_MANY_CATEGORIES` (R-3.22) |
| `assignee` | string opcional; vacío → null; ≤ 40 | `TEXT_TOO_LONG` (R-3.21) |

Se acumulan errores hasta el final; la UI muestra 20 como máximo (R-3.16).
`ErrorMessages.kt` (ui/common) traduce `ValidationIssue` → `UiText` (id de recurso + argumentos, que pueden ser otros `UiText`), y `UiText.asString()` lo resuelve en Compose; así el VM construye los mensajes y los tests los comprueban con Robolectric sin composables. `ErrorMessages.list` limita a 20 + "y {k} errores más" (D-35).

### 4.4 Mapeo

`JsonObject` → `RoutineDraft` (con `items` ordenados por `(dayOffset, time, position)`) → al confirmar, `RoutineOrderEntity` + `AlarmItemEntity` (vía `ScheduleOrderUseCase`).
`RoutineImporter` es la interfaz de dominio (`suspend fun import(text): ImportResult`); `RoutineJsonImporter` (data/importer) la implementa:
comprueba el tamaño (≤ 100 KB en UTF-8, si no `INPUT_TOO_LONG` con los KB redondeados hacia arriba en `received`), extrae (`NO_JSON_FOUND` si no hay objeto),
parsea con un `draftId` UUID nuevo y guarda el texto extraído en `RoutineDraft.rawJson`. Todo corre en `@DefaultDispatcher` (R-3.23).

## 5. Persistencia (Room)

`RutinifyDatabase` versión 1, `exportSchema = true` (preparado para migraciones v2).

### `routine_order`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | `autoGenerate = true` (Room emite AUTOINCREMENT: los ids **nunca se reutilizan**) |
| `name` | TEXT NOT NULL | |
| `date` | TEXT NOT NULL | `LocalDate` ISO (`TypeConverter`); ordenable lexicográficamente. Índice. |
| `notes` | TEXT NULL | |
| `status` | TEXT NOT NULL | `ACTIVE`/`PAUSED`/`CANCELLED` |
| `schema_version` | INTEGER NOT NULL | 1 |
| `raw_json` | TEXT NULL | JSON original extraído (depuración / v2 compartir) |
| `created_at`, `updated_at` | INTEGER NOT NULL | epoch millis |

### `alarm_item`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | **base del requestCode** (§6.1) |
| `order_id` | INTEGER NOT NULL | FK → `routine_order.id` `ON DELETE CASCADE`, índice |
| `position` | INTEGER NOT NULL | |
| `time` | TEXT NOT NULL | `HH:mm` |
| `day_offset` | INTEGER NOT NULL | |
| `title`, `description`, `category`, `assignee`, `duration_min` | | |
| `status` | TEXT NOT NULL | índice compuesto `(status, order_id)` |
| `snoozed_until` | INTEGER NULL | epoch millis absoluto |
| `fired_at`, `done_at` | INTEGER NULL | |
| `status_changed_at` | INTEGER NOT NULL | |

**No se guarda la hora de disparo absoluta** (salvo `snoozed_until`): se calcula con `TriggerTimeCalculator` desde `order.date + day_offset + time` en la zona actual. Así un cambio de zona solo requiere reprogramar (R-12.2, D-02).

### DAOs (resumen)

- `OrderDao`: `observeSummaries(): Flow<List<OrderSummaryRow>>` (LEFT JOIN + `SUM(CASE WHEN status='PENDING')`, órdenes sin alarmas incluidas), `observeWithAlarms(id): Flow<OrderWithAlarmsRow?>` (`@Relation`), `findOpenByDate(date)` (ACTIVE o PAUSED), `insert`, `getById`, `updateStatus(id, status, at)`, `delete`.
- `AlarmItemDao`: `insertAll`, `getById`, `getPendingForActiveOrders(): List<PendingAlarmRow>` (alarma + `order_date`, para calcular el disparo), `getPendingByOrder(id)`, `updateStatus(ids, status, at)` (rellena `fired_at`/`done_at` al pasar a FIRED/DONE), `snooze(id, until, at)` (vuelve a PENDING), `delete(id)`, `insert(entity)` (deshacer).
- Operaciones compuestas (crear orden + alarmas, reemplazar) en una transacción (`withTransaction`) dentro del repositorio: `OrderRepository.createOrder(order, alarms, replaceOrderIds)` → `Result<CreatedOrder>` (orden, alarmas con ids y ids de alarmas canceladas por el reemplazo). Si falla, se deshace todo y devuelve `Result.failure` (R-5.7).

## 6. Scheduler

```kotlin
// domain/scheduler
interface AlarmScheduler {
    fun canScheduleExact(): Boolean
    /** Idempotente: si ya existe, la reemplaza. */
    fun schedule(alarmId: Long, triggerAt: Instant): ScheduleMode   // EXACT | WINDOWED
    fun cancel(alarmId: Long)
}
```

### 6.1 `requestCode` único y estable

- `requestCode = alarmId.toInt()` (el id de Room). Se valida `alarmId in 1..Int.MAX_VALUE` (con AUTOINCREMENT tardaría miles de millones de alarmas en desbordar).
- **Estable**: no cambia entre reprogramaciones, reinicios ni cambios de zona → se puede cancelar siempre recreando el mismo `PendingIntent`.
- **Único**: AUTOINCREMENT garantiza que no se reutiliza ni tras borrar.
- La identidad del `PendingIntent` además incluye acción + `data` URI, de modo que los distintos tipos de intent de la misma alarma no colisionan:

| Uso | Componente | Acción | Data URI | requestCode |
|---|---|---|---|---|
| Disparo | `AlarmReceiver` | `com.mirkwood.rutinify.action.FIRE` | `rutinify://alarm/{id}` | id |
| "Hecho" | `NotificationActionReceiver` | `…action.DONE` | `rutinify://alarm/{id}/done` | id |
| "+5 min" | `NotificationActionReceiver` | `…action.SNOOZE` | `rutinify://alarm/{id}/snooze` | id |
| Tocar notificación | `MainActivity` | `VIEW` | `rutinify://order/{orderId}?alarm={id}` | id |

- Flags: `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`. Cancelar: recrear con `FLAG_NO_CREATE or FLAG_IMMUTABLE`; si no es null → `alarmManager.cancel(pi)` y `pi.cancel()`.
- **Id de notificación** = `alarmId.toInt()` (tag `"alarm"`), lo que permite quitarla al eliminar/cancelar.
- `PendingIntentFactory` centraliza todo esto (testeable con Robolectric).

### 6.2 Modo de programación

```
if (canScheduleExact())  setExactAndAllowWhileIdle(RTC_WAKEUP, t, pi)        → EXACT   (R-5.3)
else                     setWindow(RTC_WAKEUP, t, 10 min, pi)                → WINDOWED (R-5.4)
```
En API 28–30 `canScheduleExact()` es siempre `true`. Ojo: `setWindow` no despierta en Doze profundo; es una degradación aceptada y avisada en UI (D-08).

### 6.3 Cálculo de hora — `TriggerTimeCalculator`

```kotlin
fun triggerAt(date: LocalDate, time: LocalTime, dayOffset: Int, zone: ZoneId): Instant =
    ZonedDateTime.of(date.plusDays(dayOffset.toLong()), time, zone).toInstant()
fun effectiveTrigger(alarm, orderDate, zone) = alarm.snoozedUntil ?: triggerAt(…)
```
`ZonedDateTime.of` resuelve huecos de DST desplazando la duración del salto y solapes con el offset anterior (R-5.8). Colombia no tiene DST, pero se testea con `Europe/Madrid`.

### 6.4 Casos de uso que tocan el scheduler

| Use case | Qué hace | Req. |
|---|---|---|
| `ScheduleOrderUseCase(draft, date, excludedKeys, policy)` (D-42) | comprueba que pendientes actuales (menos las que cancelaría el reemplazo) + nuevas ≤ 500; si no, devuelve `TooManyAlarms(n, m)` sin escribir nada (R-5.10). Después, transacción: (si REPLACE) cancela órdenes en conflicto → inserta orden + alarmas (pasadas/excluidas como `SKIPPED`) → crea canales → `schedule()` de cada `PENDING`. Si Room falla, no se programa nada. | R-5.*, R-6.* |
| `HandleAlarmFiredUseCase(id)` | carga alarma+orden; si no está `PENDING` o la orden no está `ACTIVE` → nada; si no → `FIRED` + publicar notificación. | R-7.1, R-7.7, R-7.8 |
| `MarkAlarmDoneUseCase(id)` | `DONE`, quitar notificación, cancelar si estaba pendiente. | R-7.4, R-9.5 |
| `SnoozeAlarmUseCase(id)` | `PENDING`, `snoozed_until = now + 5 min`, quitar notificación, `schedule()`. | R-7.5, R-12.7 |
| `DeleteAlarmUseCase(id)` / `RestoreAlarmUseCase(alarm)` | borrar + cancelar si estaba pendiente + quitar notificación, devolviendo la alarma tal cual / reinsertarla con el mismo id; si es *Pendiente* de una orden *Activa* se reprograma cuando su hora es futura y pasa a *Omitida* si ya pasó (D-47). | R-9.3, R-9.4 |
| `CancelOrderAlarmsUseCase(orderId)` | `OrderRepository.cancelOrder` (una transacción: pendientes → `CANCELLED`, orden → `CANCELLED`) → `cancel()` de esas pendientes → `cancelForOrder`. Devuelve cuántas canceló (D-48). | R-10.2, R-10.3 |
| `DeleteOrderUseCase(orderId)` | lee las pendientes, borra la orden (cascade) → `cancel()` de esas pendientes → `cancelForOrder` (D-48). | R-10.4 |
| `PauseOrderUseCase` / `ResumeOrderUseCase` | orden → `PAUSED` y `cancel()` de sus pendientes, que siguen `PENDING` / pendientes con hora no futura → `SKIPPED` (sin tolerancia), orden → `ACTIVE`, programar las futuras; devuelve `ResumeResult(scheduled, skipped)` (D-49). | R-10.5, R-10.6 |
| `RescheduleAllUseCase()` | ver §7.2 (D-41). | R-11.6, R-11.7, R-12.* |
| `ObserveOrderSummariesUseCase()` | `Flow` de `OrderSummary` ya ordenado (fecha desc, creación desc); re-emite con cualquier cambio de orden o alarma (D-45). | R-8.1, R-8.6 |
| `ObserveOrderDetailUseCase(orderId)` | `Flow<OrderDetail?>`: línea de tiempo ordenada por hora efectiva (la pospuesta usa `snoozedUntil`) y `nextAlarmId` = primera *Pendiente* si la orden está *Activa*; null si la orden no existe (D-46). | R-9.1, R-9.2 |
| `CanScheduleExactAlarmsUseCase()` | Si las alarmas suenan exactas o con ventana; lo usa el aviso de R-5.4 (en T-29 pasa a `PermissionChecker`). | R-5.3, R-5.4 |

Todos los use cases que modifican alarmas comparten un `Mutex` (`AlarmMutationLock`, singleton) para evitar carreras entre receiver y UI.

## 7. Receivers

Todos `@AndroidEntryPoint`, `exported` solo donde el sistema lo exige, y usan `goAsync()` + `@ApplicationScope` + `withTimeout(9 s)`.

### 7.1 Declaración

| Receiver | Acciones | exported |
|---|---|---|
| `AlarmReceiver` | `…action.FIRE` | false |
| `NotificationActionReceiver` | `…action.DONE`, `…action.SNOOZE` | false |
| `RescheduleReceiver` | `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` | true (broadcasts del sistema protegidos) |

Permisos en manifiesto: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. **No** `USE_EXACT_ALARM` (D-09), **no** `INTERNET` (R-13.6).
No se usa `LOCKED_BOOT_COMPLETED`: la BD está en almacenamiento cifrado por credencial; se reprograma tras el desbloqueo con `BOOT_COMPLETED`.

### 7.2 `RescheduleAllUseCase`

Se invoca desde `RescheduleReceiver`, desde `RutinifyApp.onCreate` (reconciliación, cubre "forzar detención", que borra las alarmas sin avisar) y desde la pantalla de permisos al volver a primer plano.

```
para cada alarma PENDING de órdenes ACTIVE:
    t = effectiveTrigger(alarma)
    si t > now                 → scheduler.schedule(id, t)             (R-12.1, R-12.2, R-12.7)
    si now - t ≤ 15 min        → HandleAlarmFiredUseCase(id)           (R-12.4)
    si now - t > 15 min        → status = SKIPPED                      (R-12.5)
```
Idempotente: `schedule()` reemplaza el `PendingIntent` con el mismo requestCode (R-12.6).

## 8. Notificaciones

- **Canales por categoría**: id `cat_<categoria>`, nombre = categoría capitalizada ("Siesta"), grupo `routines` ("Rutinas"), `IMPORTANCE_HIGH`, sonido y vibración por defecto.
  Se crean (idempotente) en `ScheduleOrderUseCase` antes de programar (R-5.9), y en `RutinifyApp.onCreate` para las categorías de órdenes activas. Nunca se borran automáticamente (D-15).
- **Contenido** (R-7.1): título `title`; texto `description` o `"{time}"`; `subText` = nombre de la orden; `category = CATEGORY_REMINDER`; `setWhen(trigger)`; `setAutoCancel(true)`; icono monocromo propio.
- **Acciones** (R-7.3): "Hecho" y "+5 min" (`PendingIntent` broadcast, §6.1). Content intent: deep link a detalle.
- **Interfaz** (D-38): `ensureChannels(categories)`, `publish(AlarmNotification): Boolean`, `cancel(alarmId)`, `cancelForOrder(orderId)`. `publish` crea su canal si falta; `cancelForOrder` filtra las notificaciones activas por un extra con el `orderId`.
- Si `areNotificationsEnabled()` es falso, `AndroidNotificationPublisher.publish()` no lanza; devuelve `false` y el estado igualmente pasa a `FIRED` (R-7.8).

## 9. Permisos

`PermissionChecker` (dominio) → `AndroidPermissionChecker`: `notificationsGranted()`, `exactAlarmsGranted()`; `PermissionStatus` se expone como `StateFlow` refrescado en `ON_RESUME` (`LifecycleEventEffect`) (R-11.5).

Flujo en *Revisión* al pulsar "Programar alarmas" (R-11.1, R-11.2):
1. Android 13+ sin notificaciones → diálogo explicativo → `rememberLauncherForActivityResult(RequestPermission)`. Si deniega, se continúa (R-7.8) y queda el banner.
2. Sin alarmas exactas → diálogo con "Abrir ajustes" (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM` con `package:` URI) o "Continuar sin exactas".
3. Comprobación de conflicto de fecha (R-6.1) → programar.

Denegación permanente de notificaciones → `Settings.ACTION_APP_NOTIFICATION_SETTINGS` (R-11.8).

## 10. Navegación y pantallas

Rutas type-safe:
```kotlin
// ui/navigation/Destinations.kt — sufijo Destination para no chocar con los composables XxxRoute (D-23)
@Serializable data object HomeDestination
@Serializable data object NewRoutineDestination
@Serializable data class ReviewDestination(val draftId: String)
@Serializable data class OrderDetailDestination(val orderId: Long, val highlightAlarmId: Long? = null, val scheduledCount: Int? = null)
@Serializable data object PermissionsDestination
```
Deep link `rutinify://order/{orderId}?alarm={highlightAlarmId}` → `OrderDetailDestination` (R-7.6).

```
Home ──FAB──► NewRoutine ──Validar ok──► Review ──Programar──► OrderDetail (popUpTo Home)
  │                                         ▲
  ├──tap orden──► OrderDetail ──Duplicar────┘
  └──banner───► Permissions
```

| Pantalla | ViewModel / estado principal | Req. |
|---|---|---|
| **Home** | `HomeUiState(isLoading, orders: List<OrderCardUi>)` (+ `missingPermissions` en T-29); estado vacío con 3 pasos | R-8.*, R-11.3 |
| **NewRoutine** | `NewRoutineUiState(text, isValidating, issues, templateExpanded)`; `text` en `SavedStateHandle` | R-1.*, R-2.*, R-3.* |
| **Review** | `ReviewUiState(name, notes, date, dateWasDefaulted, items: List<ReviewItemUi>, summary, warnings, canSchedule, dialog)` | R-4.*, R-5.*, R-6.*, R-11.1-2 |
| **OrderDetail** | `OrderDetailUiState(isLoading, notFound, name, dateLabel, notes, status, pending, total, timeline: List<TimelineItemUi>, scrollToIndex, windowedWarning)` (+ acciones en T-25–T-28); snackbar "{n} alarmas programadas" / deshacer | R-9.*, R-10.* |
| **Permissions** | `PermissionsUiState(notifications, exactAlarms)` | R-11.4 |

**Paso del borrador NewRoutine → Review:** `DraftStore` (`@Singleton`, en memoria, `Map<String, RoutineDraft>`); la ruta lleva solo `draftId`.
Si el proceso muere estando en *Review*, el borrador no existe → se muestra "El borrador se perdió, vuelve a validar" y se regresa a *NewRoutine*, que conserva el texto (R-2.5). Ver D-10.

**Cálculos de Review** en dominio, no en composables:
- `DefaultDatePolicy.defaultDate(draft, now, zone)` → `DefaultDate(date, chosenByApp)`: la fecha del JSON si venía; si no, hoy si alguna actividad dispara estrictamente después de `now`, y si no, mañana. `chosenByApp` activa el aviso "No venía fecha; elegí {fecha}" (R-4.4, R-4.5).
- `BuildSchedulePreviewUseCase(draft, date, excludedKeys, now, zone)` → `SchedulePreview(items: List<PreviewItem(item, triggerAt, isPast, isIncluded)>, summary: ScheduleSummary?, canSchedule, suggestTomorrow)`; `ScheduleSummary(count, first, firstDayOffset, last, lastDayOffset)` (R-4.6–R-4.11, D-36).

## 11. Tiempo

- `Clock` inyectado (`Clock.systemDefaultZone()` en prod); la zona se lee **en cada uso** con `ZoneId.systemDefault()` vía `ZoneProvider`, nunca cacheada (cambia en caliente, R-12.2).
- Comparaciones "ya pasó" con `Instant` (`<=` now) (R-4.7).
- Sin desugaring de `java.time`: no usar APIs posteriores a la 28 como `LocalDate.ofInstant` (API 31); usar `instant.atZone(zone).toLocalDate()`. Lint (`NewApi`) lo detecta.
- Formato de presentación con `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` y locale del sistema; horas siempre `HH:mm`.

## 12. Estrategia de testing

| Capa | Tipo | Herramientas | Qué cubre |
|---|---|---|---|
| `JsonExtractor` | unit JVM (TDD) | JUnit, Truth | Markdown fences, texto alrededor, llaves dentro de strings, varios objetos, sin JSON |
| `RoutineJsonParser` | unit JVM (TDD) | JUnit, Truth, fixtures `src/test/resources/fixtures/*.json` | cada fila de la tabla §4.3, errores acumulados, tipos erróneos, warnings |
| Plantilla | unit JVM | lee `res/raw/prompt_template.txt` | el ejemplo embebido parsea OK |
| `TriggerTimeCalculator`, `DefaultDatePolicy`, `BuildSchedulePreviewUseCase` | unit JVM (TDD) | `Clock.fixed`, `ZoneId.of("America/Bogota")`, `Europe/Madrid` | medianoche, dayOffset, DST, todo pasado, fecha por defecto |
| Use cases | unit JVM | fakes (`FakeAlarmScheduler`, repos fake, `FakeNotificationPublisher`), Turbine | reemplazo, pausa/reanudar, snooze, reprogramar (≤15/>15 min), idempotencia, eliminar con disparadas |
| DAOs | unit JVM con Robolectric | Room in-memory, Turbine | consultas de resumen, cascade, AUTOINCREMENT no reutiliza ids |
| `PendingIntentFactory` / `AndroidAlarmScheduler` | unit JVM con Robolectric | `ShadowAlarmManager` | requestCode = id, cancel encuentra el mismo PI, modo exacto vs ventana |
| Receivers | unit JVM con Robolectric | | delegan en el use case correcto |
| ViewModels | unit JVM | Turbine, `MainDispatcherRule` | estados y efectos |
| UI | previews + 2–3 tests Compose instrumentados (opcional) | | flujo feliz NewRoutine → Review |

Cobertura: Kover con regla `minBound(80)` sobre `com.mirkwood.rutinify.domain.*` y `com.mirkwood.rutinify.data.importer.*` (R-13.5).

## 13. Dependencias justificadas

| Dependencia | Por qué |
|---|---|
| Hilt, hilt-navigation-compose, KSP | DI exigida por el stack; `hiltViewModel()` en destinos de navegación; KSP sustituye kapt (más rápido, requerido por Room moderno). |
| Room (+ room-testing) | Persistencia exigida; fuente de verdad de alarmas. |
| kotlinx-serialization-json (+ plugin) | Parseo de JSON y rutas type-safe de Navigation. |
| navigation-compose | Navegación exigida. |
| lifecycle-runtime-compose, lifecycle-viewmodel-compose | `collectAsStateWithLifecycle`, `LifecycleEventEffect`. |
| Truth, Turbine, kotlinx-coroutines-test | Exigidos por la spec de tests. |
| Robolectric | Ejecutar tests de Room in-memory, `AlarmManager` y receivers en JVM con `./gradlew test`, sin emulador, para que la DoD sea verificable en cada tarea. |
| Compose ui-test-junit4 (también en `testImplementation`) | Tests de navegación/Compose en JVM con Robolectric, sin emulador. Ya estaba en el catálogo para `androidTest`. |
| Kover | Medir y forzar la cobertura ≥ 80 % (más simple de configurar que JaCoCo con AGP 9). |

## 14. Registro de decisiones

| Id | Decisión | Motivo | ¿Validar con Johan? |
|---|---|---|---|
| D-01 | `minSdk 28` (el del proyecto creado) en vez de 26 (el prompt) | El proyecto ya está en 28; Android 8.x es < 1 % del parque. Nada del diseño depende de ello. | **Sí** |
| D-02 | Semántica de **hora de pared**: si cambia la zona, "06:45" sigue siendo 06:45 local | Una rutina de cachorro sigue el reloj local, no un instante absoluto. | **Sí** |
| D-03 | "Eliminar todas las alarmas" = cancelar + marcar `CANCELLED` y conservar historial; "Eliminar orden" (menú) = borrado total | El prompt decía "marca/borra"; ofrecer ambas cubre los dos usos. | **Sí** |
| D-04 | Alarmas perdidas al reprogramar: ≤ 15 min tarde → disparar ya; > 15 min → `SKIPPED` | Evita una avalancha de avisos viejos tras un reinicio largo. | **Sí** (umbral) |
| D-05 | `dayOffset` permitido 0..1 | El caso real solo cruza una medianoche; ampliar es trivial. | No |
| D-06 | `title` > 80 → recortar con aviso, no error | Menos fricción para el usuario no técnico. | No |
| D-07 | Parseo manual sobre `JsonElement` en vez de DTOs `@Serializable` | Permite acumular errores por item/campo en español. | No |
| D-08 | Fallback `setWindow` con ventana de 10 min | Pedido en el prompt; aviso visible. | No |
| D-09 | No declarar `USE_EXACT_ALARM` | Google Play lo restringe a apps de despertador/calendario; arriesga el rechazo. | No |
| D-10 | Borrador en memoria (`DraftStore`), no en Room | Simplicidad; el texto pegado sí sobrevive a la muerte del proceso. | No |
| D-11 | Robolectric para DAO/scheduler en JVM | DoD ejecutable sin emulador. | No |
| D-12 | Snooze guarda un instante absoluto (`snoozed_until`) | "+5 min" es relativo al momento en que se pulsó. | No |
| D-13 | Se acepta `H:mm` (ej. `6:45`) y se normaliza a `HH:mm` | Las IAs a veces omiten el cero inicial. | No |
| D-14 | "Terminada" es un estado derivado, no persistido | Evita estados inconsistentes. | No |
| D-15 | Los canales de notificación nunca se borran automáticamente | Borrarlos perdería la configuración del usuario. | No |
| D-16 | "Reemplazar la anterior" pasa las órdenes anteriores a `CANCELLED` (no las borra) | Coherente con D-03; el usuario puede borrarlas después. | No |
| D-17 | Sin `LOCKED_BOOT_COMPLETED` | La BD no está disponible antes del primer desbloqueo. | No |
| D-18 | Kotlin 2.2.10 → 2.3.21 | Hilt 2.60.1 y kotlinx.serialization 1.11 se compilan con Kotlin 2.3; KSP 2.3.x está pensado para él. | No |
| D-19 | Kover se añade en T-32, no en T-01 | Reduce el riesgo del setup inicial con AGP 9. | No |
| D-20 | Se fijan las versiones AndroidX más nuevas compatibles con `compileSdk 36` (Compose BOM 2026.06.01, core 1.18.0, lifecycle 2.10.0, navigation 2.9.8, androidx.hilt 1.3.0) | Las últimas (Compose 1.12, core 1.19, lifecycle 2.11, navigation 2.10, hilt 1.4) declaran `minCompileSdk=37` y `checkDebugAarMetadata` falla; `compileSdk 36` está fijado en CLAUDE.md. Subir a 37 es un cambio aislado cuando se quiera. | No |
| D-21 | `ZoneProvider` es una `fun interface` en `domain/time`; su implementación `SystemZoneProvider` vive en `data/system`. Los qualifiers (`@IoDispatcher`, `@DefaultDispatcher`, `@ApplicationScope`) están en `di/Qualifiers.kt`; `MainDispatcherRule` en `src/test/.../testutil` | Dominio sin dependencias de implementación; un único sitio para qualifiers y utilidades de test. | No |
| D-22 | Paquetes aún vacíos de §3 de CLAUDE.md se versionan con `.gitkeep`, que se borra al añadir el primer fichero | Git no guarda directorios vacíos. | No |
| D-23 | Las claves de navegación se llaman `XxxDestination` (no `XxxRoute` como decía §10) | CLAUDE.md §4 reserva `XxxRoute` para el composable con estado; con el mismo nombre chocarían en `RutinifyNavHost`. | No |
| D-24 | Iconos como vector drawables propios (`res/drawable/ic_*.xml`) en vez de `material-icons` | Material3 1.4 ya no trae los iconos; evita añadir una dependencia por 2–3 iconos. | No |
| D-25 | En T-05 los DAOs solo tienen `insert`/`insertAll`, `getById` y `delete` (los necesarios para probar la BD); T-06 añade el resto de design §5. `DatabaseModule` (Hilt) se añade ya en T-05 | Probar cascade y AUTOINCREMENT sin SQL crudo; el grafo Hilt queda validado en compilación. | No |
| D-26 | Conversores explícitos para `OrderStatus`/`AlarmStatus` (por `name`) en vez del conversor de enums implícito de Room | El formato en disco queda fijado en código y testeado; renombrar un valor exige migración consciente. | No |
| D-27 | `observeWithAlarms` no ordena las alarmas (`@Relation` no lo permite); el repositorio las ordena por `(dayOffset, time, position)` al mapear a dominio (T-07). `getPendingForActiveOrders` devuelve también la fecha de la orden | Mantener el `@Relation` de §5; `RescheduleAll` necesita la fecha para calcular el disparo sin otra consulta. | No |
| D-28 | `AlarmItem` de dominio gana `firedAt`/`doneAt` (opcionales), que ya existían en Room | "Deshacer" (R-9.4) reinserta la fila idéntica sin perder esas marcas. No cambia el esquema. | No |
| D-29 | Los repositorios ponen las marcas de tiempo con el `Clock` inyectado; solo `createOrder` devuelve `Result` (R-5.7), el resto deja pasar excepciones inesperadas al borde (receiver / VM) | Una única fuente de "ahora" testeable; el único fallo de escritura con requisito propio es crear la orden. | No |
| D-30 | `JsonExtractor`: si los bloques ``` no contienen un objeto válido se busca en todo el texto; un candidato cerrado pero inválido se salta entero y uno sin cerrar avanza un carácter. Devuelve también el texto exacto extraído | Más tolerante sin devolver por error un item suelto de un JSON roto (que daría un error confuso de versión en vez de R-3.3); `raw_json` necesita el texto. | No |
| D-31 | `ValidationIssue` gana `expected: ValueType?` y `parse(json, draftId)` recibe el id del borrador. `schemaVersion` solo vale como número `1` (no `"1"` ni `1.0`); una fecha no textual es `INVALID_DATE` | R-3.13 pide el tipo esperado; el importer (T-11) genera el UUID, así el parser es determinista en tests; "entero == 1" de §4.3. | No |
| D-32 | Límites anti-abuso: texto ≤ 100 KB, ≤ 20 candidatos JSON, anidamiento ≤ 32, notas/descripción ≤ 500, responsable ≤ 40, categoría ≤ 30, ≤ 10 categorías distintas, ≤ 500 alarmas pendientes en la app | Un texto pegado (posiblemente de terceros) no debe poder congelar la app (escaneo cuadrático), llenar Room/notificaciones con textos enormes, crear canales basura (D-15) ni provocar la excepción de Android por superar 500 alarmas. | ✅ Aprobada por Johan el 2026-11-05 |
| D-33 | `ValidationIssue` gana `limit: Int?` (máximo en `TEXT_TOO_LONG` y `TOO_MANY_CATEGORIES`; en este último `received` = nº de categorías). Las longitudes se miden en caracteres tras `trim` (la categoría, tras normalizarla); `general` cuenta como categoría; el recuento de categorías se hace sobre los items válidos, para informarlo junto a los errores de items. `dayOffset`/`durationMin` de tipo no numérico dan su error de rango, no `WRONG_TYPE` | R-3.21 pide mostrar el máximo sin duplicar constantes en la UI; `general` también crea un canal (D-15); R-3.11/R-3.12 ya describen el valor esperado. | No |
| D-34 | `RoutineDraft` gana `rawJson: String?` (null en borradores duplicados). El límite de 100 KB se mide en bytes UTF-8 (1 KB = 1024 B). El texto de la plantilla (`res/raw/prompt_template.txt`) lo redacta el agente a partir del contrato §4.1 y los límites de D-32, con el ejemplo canónico dentro de un bloque ```json; un test comprueba que ese ejemplo es idéntico a `valid_v1.json` | `raw_json` (§5) necesita llegar desde la importación hasta `ScheduleOrderUseCase`; bytes porque es lo que ocupa en memoria/Room; el prompt original del producto no está en el repo. | No |
| D-35 | Mensajes de validación: prefijo "Actividad {n} («título»)", si no hay título "(a las {hora})", si no solo el número; el título del contexto se acorta a 30 caracteres con "…". Los tipos esperados se nombran "un texto entre comillas", "un número entero", "un bloque entre llaves { }" y "una lista entre corchetes [ ]"; el valor recibido se añade como "(se recibió «…»)". Los campos se citan con su nombre JSON (`description`, `routine.notes`), como ya hacen R-3.5 y R-3.7 | R-3.8/R-3.9 piden ese contexto; R-3.13 pide tipos no técnicos; citar el nombre JSON ayuda a corregirlo en la IA. | No |
| D-36 | `ScheduleSummary` lleva el `dayOffset` de la primera y la última actividad (en vez de `crossesMidnight`), elegidas por hora de disparo entre las incluidas. Una actividad pasada nunca se incluye, aunque no esté en `excludedKeys`. "Sugerir mañana" (R-4.11) solo si la fecha es anterior a hoy y **todas** las actividades ya pasaron (no si el usuario excluyó las futuras) | La UI puede escribir "(+1 día)" en cualquiera de los dos extremos; programar algo pasado dispararía en el acto; se sigue R-4.11 al pie de la letra. | No |
| D-37 | T-15 declara `AlarmReceiver` (no exportado) como receptor vacío para que el `PendingIntent` de disparo tenga un componente estable; su lógica llega en T-18. `PendingIntentFactory` solo crea por ahora el intent de disparo (los de "Hecho", "+5 min" y tocar la notificación llegan con T-16) y expone `alarmIdOf(intent)` para leer el id de `rutinify://alarm/{id}`. Si `setExactAndAllowWhileIdle` lanza `SecurityException` (permiso revocado entre la comprobación y la llamada), se registra y se programa con ventana (`WINDOWED`) | Cada tarea añade solo lo que usa; mejor una alarma algo tarde que perderla. La rama API 28–30 de `canScheduleExact()` no se prueba en Robolectric (solo está el runtime de API 36) | No |
| D-38 | `NotificationPublisher` (dominio) expone `ensureChannels(categories)`, `publish(AlarmNotification): Boolean`, `cancel(alarmId)` y `cancelForOrder(orderId)`. `publish` también crea su canal si falta. `ensureChannels` solo crea los canales que no existen (no reescribe los ajustes del usuario). Nombre del canal: la categoría con los guiones como espacios y la primera letra en mayúscula ("paseo-largo" → "Paseo largo"). `cancelForOrder` busca entre las notificaciones activas con tag `"alarm"` las que llevan el extra con el `orderId`. Las acciones "Hecho"/"+5 min" van a `NotificationActionReceiver`, vacío hasta T-18; el deep link del cuerpo apunta explícitamente a `MainActivity` y el NavHost lo atenderá en T-24 | Que una alarma nunca se quede sin notificación por no tener canal; no deshacer lo que el usuario cambió en Ajustes; no hace falta guardar ids de notificación por orden | No |
| D-39 | Disparo y acciones (T-17): `HandleAlarmFiredUseCase` devuelve `FireOutcome` (`NOTIFIED`, `FIRED_WITHOUT_NOTIFICATION`, `IGNORED`) y escribe `FIRED` antes de publicar. `MarkAlarmDoneUseCase` solo cambia alarmas `PENDING`/`FIRED` (y cancela en `AlarmManager` solo si estaba `PENDING`). "+5 min" solo pospone una alarma `FIRED` de una orden `ACTIVE`; con la orden pausada o cancelada, o si la alarma ya no está disparada (doble toque, notificación vieja), solo se quita la notificación. Ambas acciones quitan siempre la notificación aunque no cambien nada. `AlarmMutationLock` no es reentrante | Pulsar dos veces es idempotente; posponer durante una pausa programaría una alarma de una orden pausada; una notificación huérfana no debe quedarse en pantalla | No |
| D-40 | Los receivers (T-18) solo traducen el intent a un `ReceiverCommand` (`Fire`/`Done`/`Snooze`, la acción elige el comando y el id sale del data URI), llaman a `goAsync()` y delegan en `ReceiverCommandHandler`, que llama al use case. `launchReceiverWork` aplica el `withTimeout(9 s)`, registra los fallos y siempre llama a `finish()`. Se prueban esas piezas por separado (Robolectric y fakes) en vez del receiver completo, y se verificó en el bytecode que Hilt inyecta en `onReceive` | Probar el receiver entero con inyección exigiría `hilt-android-testing`, una dependencia no aprobada (CLAUDE.md §9); así cada receiver queda en unas pocas líneas sin lógica | No |
| D-41 | `RescheduleAllUseCase()` no recibe `reason` (no cambia la lógica y el dominio no registra logs) y devuelve `RescheduleResult(scheduled, firedNow, skipped)`. Bloquea `AlarmMutationLock` durante toda la pasada y dispara las atrasadas con `HandleAlarmFiredUseCase.fireLocked` (el lock no es reentrante). Las omitidas también se cancelan en `AlarmManager`. Exactamente 15 min tarde → se dispara; más → `SKIPPED`. `RutinifyApp.onCreate` lanza la reconciliación en el scope de aplicación. Los tests JVM usan `application=android.app.Application` (`robolectric.properties`) | Con el reloj adelantado, el sistema dispararía en el acto alarmas ya omitidas; sin el `Application` simple, cada test Robolectric lanzaría una reprogramación contra la base de datos real | No |
| D-42 | `ScheduleOrderUseCase(draft, date, excludedKeys, policy: ConflictPolicy)` recibe las exclusiones (igual que `BuildSchedulePreviewUseCase`) y recalcula la vista previa con la hora actual antes de guardar. Devuelve `ScheduleOrderResult`: `Scheduled(orderId, scheduledCount, windowed)`, `TooManyAlarms(requested, alreadyScheduled)`, `NothingToSchedule` o `StorageFailure`. El límite de 500 cuenta solo las pendientes de órdenes `ACTIVE` (las pausadas no están en `AlarmManager`), sin las de las órdenes que se van a reemplazar. Con REPLACE también se quitan las notificaciones visibles de las órdenes reemplazadas. Todo ocurre dentro de `AlarmMutationLock` | Entre la vista previa y el toque en "Programar" puede pasar una actividad; el límite real de Android es de alarmas registradas; una orden cancelada no debe dejar avisos en pantalla | No |
| D-43 | Nueva rutina (T-21): el portapapeles es la interfaz de dominio `Clipboard` (`readText`/`copyText`), implementada en `data/system/AndroidClipboard` (el nombre `ClipboardReader` de la tarea se queda corto porque también copia). La plantilla llega por `PromptTemplateSource`. `DraftStore` guarda `DraftEntry(draft, warnings)` para que Revisión muestre los avisos. Un texto solo con espacios cuenta como vacío ("Validar" deshabilitado y "El portapapeles está vacío"). Editar el texto borra los errores anteriores. "Plantilla copiada" se muestra siempre en un snackbar. Los errores (como mucho 21 líneas) van en un `Column` dentro del scroll de la pantalla, no en un `LazyColumn`. `RutinifyNavHost` recibe un `newRoutineContent` opcional para que el test de navegación en JVM use la pantalla sin estado | Los errores viejos confundirían tras corregir; `hiltViewModel()` necesita una activity de Hilt y probarlo exigiría `hilt-android-testing` (no aprobada) | No |
| D-44 | Revisión (T-22): las reglas de campo (hora, título, categoría) se movieron a `domain/importer/DraftItemRules`, que usan el parser y el editor, para que editar aplique exactamente las reglas de importación (R-4.2). Al guardar una edición también se comprueba el máximo de 10 categorías. "Deshacer" solo recupera la última actividad quitada. "Ya pasó" se recalcula en cada cambio (fecha, exclusión, edición) con la hora actual; no hay un reloj que lo refresque solo mientras la pantalla está abierta. La fecha se muestra como "jueves 5 de noviembre de 2026" (`es-ES`). Al pulsar "Programar" se buscan los conflictos; "Reintentar" repite la última opción elegida. `OrderDetailDestination` gana `scheduledCount` para que el detalle muestre "{n} alarmas programadas" (R-5.6). Los permisos al programar (R-11.1, R-11.2) quedan para T-30 | Una sola fuente de reglas evita que edición e importación diverjan; el mensaje de R-5.6 debe verse en el detalle, adonde se navega | No |
| D-45 | Inicio (T-23): la lista llega por `ObserveOrderSummariesUseCase` (la UI no habla con repositorios) y el orden de R-8.1 lo da la consulta del DAO; el VM solo mapea a `OrderCardUi(id, name, dateLabel, pending, total, status)`. `HomeUiState.isLoading` empieza en `true` y el estado vacío solo se muestra cuando Room ya respondió con una lista vacía. La fecha usa el mismo formato largo que Revisión ("jueves 5 de noviembre de 2026"), ahora compartido en `ui/common/DateFormats`. Pendientes se muestran como "Pendientes: 3 de 5" y el estado como etiqueta de color del tema (Activa = primario, Pausada = terciario, Cancelada = error, Terminada = variante de superficie). `RutinifyNavHost` gana el hueco `homeContent` para los tests JVM, igual que `newRoutineContent` (D-43) | Sin `isLoading`, el estado vacío parpadearía al abrir la app con órdenes; un único formato de fecha evita que la misma orden se lea distinto en dos pantallas | No |
| D-46 | Detalle de orden (T-24): la línea de tiempo se ordena por la hora efectiva de cada alarma, así una pospuesta aparece en su nueva hora; "+1 día" se calcula contra la fecha de la orden. La "próxima" (R-9.2) es la primera *Pendiente* y solo en órdenes *Activas* (en una pausada o cancelada no va a sonar nada). El aviso de R-5.4 se muestra si ahora mismo no hay permiso de alarmas exactas y queda alguna alarma por sonar; se vuelve a comprobar en `ON_RESUME`. No se guarda el modo por alarma porque Android cancela las exactas al retirar el permiso y `RescheduleAllUseCase` las reprograma con ventana. El deep link `rutinify://order/{orderId}?alarm={id}` lo resuelve el NavHost (`ORDER_DEEP_LINK`); una alarma resaltada que ya no existe se ignora. La lista se desplaza una vez a la alarma resaltada o, si no hay, a la próxima. "{n} alarmas programadas" se anuncia una sola vez (marca en `SavedStateHandle`). Si la orden no existe se muestra "Esta rutina ya no existe" | El permiso actual describe mejor el retraso real que un modo guardado en el momento de programar; resaltar alarmas de órdenes pausadas confundiría | No |
| D-47 | Acciones por alarma (T-25): cada tarjeta del detalle tiene "Eliminar" y, si está *Pendiente* o *Disparada*, "Marcar como hecha" (icono con descripción accesible). El VM guarda las alarmas borradas por id mientras vive la pantalla, y el snackbar "Alarma eliminada · Deshacer" lleva ese id; un borrado nuevo descarta el snackbar anterior en lugar de encolarse, aunque su alarma sigue guardada. Al deshacer, una alarma *Pendiente* cuya hora pasó mientras estaba borrada queda *Omitida* (como al reanudar, R-10.6) en vez de sonar tarde; en una orden pausada vuelve *Pendiente* sin programar; si la orden se borró entretanto no se restaura nada. La notificación quitada al borrar no se vuelve a mostrar | Reprogramar solo lo futuro es lo que pide R-9.4; dejar una pendiente pasada sin programar la dejaría pendiente para siempre | No |
| D-48 | Gestión de la orden (T-26): "Eliminar todas las alarmas" es un botón en la cabecera del detalle y "Eliminar orden" va en el menú ⋮ de la barra (R-10.4). Ambas piden confirmación; la de cancelar dice cuántas pendientes se cancelan, o que no queda ninguna, y que se conservan las hechas y disparadas (R-10.1). En una orden cancelada solo se oculta "Eliminar todas"; "Eliminar orden" sigue (R-10.8). Cancelar se hace en una única transacción nueva, `OrderRepository.cancelOrder`, que también usa el reemplazo de R-6.2. Al borrar la orden se sale del detalle y el VM conserva la última pantalla mientras sale, para no mostrar "Esta rutina ya no existe" | El menú evita borrar la orden por accidente y deja la cabecera para las acciones habituales (Pausar en T-27); una sola transacción evita órdenes canceladas con alarmas aún pendientes | No |
| D-49 | Pausar / reanudar (T-27): botón en la cabecera del detalle junto a "Eliminar todas las alarmas", sin confirmación porque es reversible. Se ofrece "Pausar" solo si la orden está Activa con pendientes (una Terminada no tiene nada que pausar) y "Reanudar" si está Pausada; ninguno en órdenes canceladas (R-10.8). Al reanudar se omite toda pendiente cuya hora efectiva no sea futura, sin la tolerancia de 15 min de §7.2: el usuario pidió silenciarlas. Si se omitió alguna, un snackbar lo dice ("Rutina reanudada. Se omitieron N alarmas…"). Pausar escribe primero la orden y luego cancela en el sistema; reanudar marca las omitidas y activa la orden antes de programar, así una interrupción a medias se repara repitiendo la acción o con `RescheduleAll` | Pausa reversible y barata; avisar de lo omitido evita que el usuario espere alarmas que no sonarán | No |
| D-01..D-04 | Aprobadas por Johan el 2026-11-04 | | ✅ |

## 15. Preparación para v2

- **Alarmas relativas ("me desperté")**: añadir a `AlarmItem` un `anchor` opcional (`ABSOLUTE` | `RELATIVE_TO_EVENT`) y `offsetMin`; `TriggerTimeCalculator` ya es el único punto que calcula horas.
- **Rutinas recurrentes**: `routine_order` puede ganar `recurrence_rule`; `DuplicateOrderUseCase` ya genera borradores a partir de una orden.
- **Widget "lo siguiente"**: `AlarmItemDao` expondrá `observeNextPending()`; Glance consumirá un use case.
- **API de Claude**: `RoutineImporter` es una interfaz; un `ClaudeRoutineImporter` produciría el mismo `ImportResult`.
- **Exportar .ics / compartir**: `raw_json` y `schema_version` se guardan desde v1; `schemaVersion` del contrato permite evolucionar.
- Esquemas de Room exportados para migraciones.

## 16. Riesgos

| Id | Riesgo | Mitigación |
|---|---|---|
| K-1 | Compatibilidad de Hilt/KSP con AGP 9 (Kotlin integrado) | T-01 verifica primero; si no compila, documentar la combinación de versiones que funcione o usar `android.builtInKotlin=false` temporalmente y anotarlo aquí. |
| K-2 | Fabricantes con optimización de batería agresiva (Xiaomi, Huawei…) matan alarmas | Fuera de MVP; en *Permisos* se añade un enlace informativo a dontkillmyapp.com (solo texto). |
| K-3 | ~30 alarmas/día exactas → impacto en batería | Aceptable: `setExactAndAllowWhileIdle` es el mecanismo previsto; cada disparo hace trabajo mínimo. |
| K-4 | El usuario fuerza la detención → Android borra las alarmas sin aviso | Reconciliación en `RutinifyApp.onCreate` (§7.2). |
| K-5 | Texto pegado malicioso o accidentalmente enorme (no se puede saber quién generó el JSON) | No se confía en el origen sino en la validación: límites de D-32, Revisión obligatoria antes de programar (R-4), sin permiso `INTERNET` (R-13.6), Room con parámetros enlazados, texto plano en UI y notificaciones. |
