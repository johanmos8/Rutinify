# Diseño técnico — Rutinify (MVP, v1.1 y roadmap v1.2–v2.0)

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
| Kover | 0.9.9 (añadido en T-32) | se aplazó para no sumar riesgo de compatibilidad con AGP 9 en el setup; funciona con AGP 9 sin ajustes |

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
    val dayOffset: Int,               // 0..30 (D-55)
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
v1.1 añade `routine.repeatDays` (opcional, 1..30) y amplía `dayOffset` a 0..30 sin cambiar `schemaVersion` (D-55):

```json
{ "schemaVersion": 1,
  "routine": { "name": "Antibiótico de Luna", "date": "2026-11-14", "repeatDays": 7 },
  "items": [ { "time": "06:00", "title": "Antibiótico", "category": "medicación" },
             { "time": "14:00", "title": "Antibiótico", "category": "medicación" },
             { "time": "22:00", "title": "Antibiótico", "category": "medicación" } ] }
```

La copia canónica vive en `app/src/main/res/raw/prompt_template_puppy.txt` (hasta T-42, `prompt_template.txt`) y en el fixture de tests `src/test/resources/fixtures/valid_v1.json`;
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
| `routine.repeatDays` | ausente/null → 1; entero 1..30 (`MAX_REPEAT_DAYS`); otro tipo → error de rango (como D-33) | R-3.24 |
| `items` | array no vacío, ≤ 200 **tras expandir** `repeatDays` | R-3.7, R-3.18, R-3.27 |
| `time` | regex `^([01]?\d|2[0-3]):[0-5]\d$`, se normaliza a `HH:mm` | R-3.8 |
| `dayOffset` | ausente/null → 0; entero 0..30 (`MAX_DAY_OFFSET`, D-55); con `repeatDays` > 1, solo 0..1 | R-3.11, R-3.15, R-3.26 |
| `title` | string no vacío; > 80 → recorte + warning `TITLE_TRUNCATED` | R-3.9, R-3.10 |
| `description` | string opcional; vacío → null; ≤ 500 | `TEXT_TOO_LONG` (R-3.21) |
| `durationMin` | null o entero 1..1440 | R-3.12 |
| `category` | null/vacío → `general`; `trim().lowercase()`, espacios → `-`; ≤ 30 tras normalizar | R-3.14; `TEXT_TOO_LONG` (R-3.21) |
| categorías distintas | ≤ 10 en toda la rutina | `TOO_MANY_CATEGORIES` (R-3.22) |
| `assignee` | string opcional; vacío → null; ≤ 40 | `TEXT_TOO_LONG` (R-3.21) |

Se acumulan errores hasta el final; la UI muestra 20 como máximo (R-3.16).
`ErrorMessages.kt` (ui/common) traduce `ValidationIssue` → `UiText` (id de recurso + argumentos, que pueden ser otros `UiText`), y `UiText.asString()` lo resuelve en Compose; así el VM construye los mensajes y los tests los comprueban con Robolectric sin composables. `ErrorMessages.list` limita a 20 + "y {k} errores más" (D-35).

### 4.4 Mapeo

`JsonObject` → (v1.1: expansión de `repeatDays`, R-3.25, tras validar los items originales; cada copia es un `DraftItem` con clave propia y la `position` del original) → `RoutineDraft` (con `items` ordenados por `(dayOffset, time, position)`) → al confirmar, `RoutineOrderEntity` + `AlarmItemEntity` (vía `ScheduleOrderUseCase`).
`RoutineImporter` es la interfaz de dominio (`suspend fun import(text): ImportResult`); `RoutineJsonImporter` (data/importer) la implementa:
comprueba el tamaño (≤ 100 KB en UTF-8, si no `INPUT_TOO_LONG` con los KB redondeados hacia arriba en `received`), extrae (`NO_JSON_FOUND` si no hay objeto),
parsea con un `draftId` UUID nuevo y guarda el texto extraído en `RoutineDraft.rawJson`. Todo corre en `@DefaultDispatcher` (R-3.23).

## 5. Persistencia (Room)

`RutinifyDatabase` versión 5 desde v2.0 (T-68; versión 4 en v1.4, T-61; versión 3 en v1.3, T-51; versión 2 en v1.2, T-43; versión 1 hasta v1.1), `exportSchema = true`. Migraciones manuales en `data/local/Migrations.kt` (D-83).

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

### `category_setting` *(v1.2, versión 2)*

| Columna | Tipo | Notas |
|---|---|---|
| `name` | TEXT PK | categoría normalizada, igual que `alarm_item.category` (R-3.14) |
| `critical` | INTEGER NOT NULL | 1 = sus alarmas suenan como alarma real (R-15, D-66) |

Sin fila = no crítica. Sin FK: la marca se conserva aunque ningún plan use ya la categoría y vuelve a valer si otro la usa.

### `diagnostic_counter` *(v1.3, versión 3)*

| Columna | Tipo | Notas |
|---|---|---|
| `key` | TEXT PK | clave estable de D-72 (`issue.<IssueKind>`, `step.*`, `plans.created`); nunca nombres ni contenido |
| `count` | INTEGER NOT NULL | veces contadas mientras el interruptor estaba activado (R-17.2) |

### `alarm_event` *(v1.4, versión 4)*

| Columna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `alarm_id` | INTEGER NOT NULL | sin FK: un evento sobrevive a borrar la alarma (y vuelve a casar si se deshace el borrado) |
| `order_id` | INTEGER NOT NULL, FK → `routine_order.id` ON DELETE CASCADE, índice | |
| `type` | TEXT NOT NULL | `DONE`, `SKIPPED`, `SNOOZED` (R-20.1) |
| `at` | INTEGER NOT NULL | epoch millis del cambio |

### Planes recurrentes *(v2.0, versión 5)*

- `routine_order` gana `recurrence_weekdays INTEGER NULL` (máscara: lunes = bit 0 … domingo = bit 6) y `recurrence_until TEXT NULL`.
- `routine_activity(id PK, order_id FK → routine_order ON DELETE CASCADE, position, time, day_offset, title, description, duration_min, category, assignee)`: el día tipo.
- `alarm_item` gana `activity_id INTEGER NULL` con índice único `(activity_id, day_offset)`: una ocurrencia por actividad y día, así que crear la ventana otra vez nunca duplica.

**No se guarda la hora de disparo absoluta** (salvo `snoozed_until`): se calcula con `TriggerTimeCalculator` desde `order.date + day_offset + time` en la zona actual. Así un cambio de zona solo requiere reprogramar (R-12.2, D-02).

### DAOs (resumen)

- `OrderDao`: `observeSummaries(): Flow<List<OrderSummaryRow>>` (LEFT JOIN + `SUM(CASE WHEN status='PENDING')`, órdenes sin alarmas incluidas; v1.1 añade `MAX(day_offset)` para el rango), `observeWithAlarms(id): Flow<OrderWithAlarmsRow?>` (`@Relation`), `findOpenOverlapping(start, end)` (ACTIVE o PAUSED cuyos días de conflicto tocan el rango, con su `MAX(day_offset)`; D-56, D-58), `insert`, `getById`, `updateStatus(id, status, at)`, `delete`.
- `AlarmItemDao`: `insertAll`, `getById`, `getPendingForActiveOrders(): List<PendingAlarmRow>` (alarma + `order_date`, para calcular el disparo), `getPendingByOrder(id)`, `updateStatus(ids, status, at)` (rellena `fired_at`/`done_at` al pasar a FIRED/DONE), `snooze(id, until, at)` (vuelve a PENDING), `delete(id)`, `insert(entity)` (deshacer).
- Operaciones compuestas (crear orden + alarmas, reemplazar) en una transacción (`withTransaction`) dentro del repositorio: `OrderRepository.createOrder(order, alarms, replaceOrderIds)` → `Result<CreatedOrder>` (orden, alarmas con ids y ids de alarmas canceladas por el reemplazo). Si falla, se deshace todo y devuelve `Result.failure` (R-5.7).

## 6. Scheduler

```kotlin
// domain/scheduler
interface AlarmScheduler {
    fun canScheduleExact(): Boolean
    /** Idempotente: si ya existe, la reemplaza. */
    fun schedule(alarmId: Long, triggerAt: Instant, critical: CriticalAlarm?): ScheduleMode   // EXACT | WINDOWED; critical desde v1.2 (D-85)
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
| "Silenciar" y corte a 10 min *(v1.2)* | `NotificationActionReceiver` | `…action.SILENCE` | `rutinify://alarm/{id}/silence` | id |
| Pantalla de alarma *(v1.2)* | `AlarmActivity` | — | `rutinify://alarm/{id}/screen` | id |

- Flags: `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`. Cancelar: recrear con `FLAG_NO_CREATE or FLAG_IMMUTABLE`; si no es null → `alarmManager.cancel(pi)` y `pi.cancel()`.
- **Id de notificación** = `alarmId.toInt()` (tag `"alarm"`), lo que permite quitarla al eliminar/cancelar.
- `PendingIntentFactory` centraliza todo esto (testeable con Robolectric).

### 6.2 Modo de programación

```
if (canScheduleExact() && critical != null)
                         setAlarmClock(AlarmClockInfo(t, abrir plan), pi)    → EXACT   (R-15.3, v1.2)
else if (canScheduleExact())  setExactAndAllowWhileIdle(RTC_WAKEUP, t, pi)   → EXACT   (R-5.3)
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
| `DuplicateOrderUseCase(orderId, date)` | lee la orden con sus alarmas y guarda en `DraftStore` un borrador `DUPLICATE` con todas sus actividades (sin estados ni posposiciones) y la fecha elegida; devuelve el `draftId` (D-50). | R-10.7 |
| `PauseOrderUseCase` / `ResumeOrderUseCase` | orden → `PAUSED` y `cancel()` de sus pendientes, que siguen `PENDING` / pendientes con hora no futura → `SKIPPED` (sin tolerancia), orden → `ACTIVE`, programar las futuras; devuelve `ResumeResult(scheduled, skipped)` (D-49). | R-10.5, R-10.6 |
| `RescheduleAllUseCase()` | ver §7.2 (D-41). | R-11.6, R-11.7, R-12.* |
| `ObserveOrderSummariesUseCase()` | `Flow` de `OrderSummary` ya ordenado (fecha desc, creación desc); re-emite con cualquier cambio de orden o alarma (D-45). | R-8.1, R-8.6 |
| `ObserveOrderDetailUseCase(orderId)` | `Flow<OrderDetail?>`: línea de tiempo ordenada por hora efectiva (la pospuesta usa `snoozedUntil`) y `nextAlarmId` = primera *Pendiente* si la orden está *Activa*; null si la orden no existe (D-46). | R-9.1, R-9.2 |
| `CanScheduleExactAlarmsUseCase()` | Si las alarmas suenan exactas o con ventana; lo usa el aviso de R-5.4. | R-5.3, R-5.4 |
| `SchedulingPermissionsUseCase` | `shouldExplainNotifications()` (Android 13+, sin permiso y aún no explicado), `markNotificationsExplained()`, `exactAlarmsGranted()`: los pasos previos a programar en Revisión (D-52). | R-11.1, R-11.2 |
| `GetPermissionStatusUseCase()` | `PermissionStatus(notifications, exactAlarms)` desde `PermissionChecker`, para el banner de Inicio y la pantalla *Permisos* (D-51). | R-11.3–R-11.5 |

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
- **Grupo** (D-87, K-13): todas las alarmas van en el grupo `com.mirkwood.rutinify.ALARMS` con un resumen mudo (`GROUP_ALERT_CHILDREN`), para que Android 16 no las agrupe por su cuenta y las deje sin sonido.
- **Interfaz** (D-38): `ensureChannels(categories)`, `publish(AlarmNotification): Boolean`, `cancel(alarmId)`, `cancelForOrder(orderId)`. `publish` crea su canal si falta; `cancelForOrder` filtra las notificaciones activas por un extra con el `orderId`.
- Si `areNotificationsEnabled()` es falso, `AndroidNotificationPublisher.publish()` no lanza; devuelve `false` y el estado igualmente pasa a `FIRED` (R-7.8).

## 9. Permisos

`PermissionChecker` (dominio) → `AndroidPermissionChecker`: `notificationsGranted()`, `exactAlarmsGranted()`, `fullScreenIntentGranted()` (v1.2, D-88); `PermissionStatus` se expone como `StateFlow` refrescado en `ON_RESUME` (`LifecycleEventEffect`) (R-11.5).

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
@Serializable data object SettingsDestination // v1.2 (T-44)
```
Deep link `rutinify://order/{orderId}?alarm={highlightAlarmId}` → `OrderDetailDestination` (R-7.6).

```
Home ──FAB──► NewRoutine ──Validar ok──► Review ──Programar──► OrderDetail (popUpTo Home)
  │                                         ▲
  ├──tap orden──► OrderDetail ──Duplicar────┘
  ├──banner───► Permissions
  └──engranaje─► Settings (v1.2)
```

| Pantalla | ViewModel / estado principal | Req. |
|---|---|---|
| **Home** | `HomeUiState(isLoading, orders: List<OrderCardUi>, missingPermissions)`; estado vacío con 3 pasos | R-8.*, R-11.3 |
| **NewRoutine** | `NewRoutineUiState(text, isValidating, issues, templateExpanded)`; `text` en `SavedStateHandle` | R-1.*, R-2.*, R-3.* |
| **Review** | `ReviewUiState(name, notes, date, dateWasDefaulted, items: List<ReviewItemUi>, summary, warnings, canSchedule, dialog)` | R-4.*, R-5.*, R-6.*, R-11.1-2 |
| **OrderDetail** | `OrderDetailUiState(isLoading, notFound, name, dateLabel, notes, status, pending, total, timeline: List<TimelineItemUi>, scrollToIndex, windowedWarning)` (+ acciones en T-25–T-28); snackbar "{n} alarmas programadas" / deshacer | R-9.*, R-10.* |
| **Permissions** | `PermissionsUiState(notifications, exactAlarms)` | R-11.4 |
| **Settings** *(v1.2)* | `SettingsUiState(isLoading, categories: List<CategoryRowUi>, exactAlarmsGranted, showExactAlarmsDialog)` | R-15.1, R-15.4, R-15.5 |

**Paso del borrador NewRoutine → Review:** `DraftStore` (`@Singleton`, en memoria, `Map<String, RoutineDraft>`); la ruta lleva solo `draftId`.
Si el proceso muere estando en *Review*, el borrador no existe → se muestra "El borrador se perdió, vuelve a validar" y se regresa a *NewRoutine*, que conserva el texto (R-2.5). Ver D-10.

**Cálculos de Review** en dominio, no en composables:
- `DefaultDatePolicy.defaultDate(draft, now, zone)` → `DefaultDate(date, chosenByApp)`: la fecha del JSON si venía; si no, hoy si alguna actividad dispara estrictamente después de `now` (en una rutina de varios días, alguna con `dayOffset` 0; D-61), y si no, mañana. `chosenByApp` activa el aviso "No venía fecha; elegí {fecha}" (R-4.4, R-4.5).
- `BuildSchedulePreviewUseCase(draft, date, excludedKeys, now, zone)` → `SchedulePreview(items: List<PreviewItem(item, triggerAt, isPast, isIncluded)>, days: List<PreviewDay(dayOffset, date, items)>, isMultiDay, summary: ScheduleSummary?, canSchedule, suggestTomorrow)`; `ScheduleSummary(count, first, firstDayOffset, last, lastDayOffset, dayCount)` (R-4.6–R-4.11, R-4.13–R-4.15, D-36, D-59).

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
| Plantillas | unit JVM (+ Robolectric para la carga por escenario) | lee los cinco `res/raw/prompt_template_*.txt` | cada ejemplo embebido parsea sin avisos, todas piden el mismo contrato, la de medicación incluye R-16.5 |
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

**Para v1.4–v2.0, aprobadas por Johan el 2026-11-16** (`CLAUDE.md` §9); se añaden en su tarea, no antes:

| Dependencia | Para qué | Por qué no basta lo existente |
|---|---|---|
| ZXing `core` | Generar el QR del plan (R-19.4, D-75) | Android no trae generador de QR; `core` es Java puro, sin permisos |
| `play-services-code-scanner` (Google code scanner) | Leer el QR (R-19.6, D-75) | Escanea sin permiso `CAMERA` ni pantalla propia de cámara; la alternativa (CameraX + ML Kit) pide `CAMERA` y mucho más código |
| `androidx.glance:glance-appwidget` | Widget "Lo siguiente" (R-21, D-79) | Escribir el widget con `RemoteViews` a mano duplica el trabajo de Compose |
| `com.google.mlkit:genai-prompt` | IA en el dispositivo (R-22, D-80) | No hay otra vía para Gemini Nano. **Aprobación condicionada**: solo si T-65 confirma que no añade `INTERNET` al manifiesto |

## 14. Registro de decisiones

| Id | Decisión | Motivo | ¿Validar con Johan? |
|---|---|---|---|
| D-01 | `minSdk 28` (el del proyecto creado) en vez de 26 (el prompt) | El proyecto ya está en 28; Android 8.x es < 1 % del parque. Nada del diseño depende de ello. | **Sí** |
| D-02 | Semántica de **hora de pared**: si cambia la zona, "06:45" sigue siendo 06:45 local | Una rutina de cachorro sigue el reloj local, no un instante absoluto. | **Sí** |
| D-03 | "Eliminar todas las alarmas" = cancelar + marcar `CANCELLED` y conservar historial; "Eliminar orden" (menú) = borrado total | El prompt decía "marca/borra"; ofrecer ambas cubre los dos usos. | **Sí** |
| D-04 | Alarmas perdidas al reprogramar: ≤ 15 min tarde → disparar ya; > 15 min → `SKIPPED` | Evita una avalancha de avisos viejos tras un reinicio largo. | **Sí** (umbral) |
| D-05 | `dayOffset` permitido 0..1 *(sustituida por D-55 en v1.1)* | El caso real solo cruza una medianoche; ampliar es trivial. | No |
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
| D-50 | Duplicar (T-28): "Duplicar para otra fecha" va en el menú ⋮ del detalle, también en órdenes canceladas (R-10.8), y se oculta si la orden no tiene alarmas. El selector de fecha abre en mañana y su botón dice "Duplicar"; se pueden elegir fechas pasadas, porque Revisión ya avisa de las actividades que no sonarán. Se copian todas las alarmas, cualquiera que sea su estado, a su hora original (la posposición no se copia), en el orden de la línea de tiempo. El borrador no lleva `rawJson`, porque no viene de una importación, así que la nueva orden guarda `raw_json` nulo. Tras programar, Revisión vuelve a Inicio como en una importación. El selector de fecha de Revisión pasa a `ui/common/RoutineDatePickerDialog` para compartirlo | Duplicar suele ser para repetir la rutina al día siguiente; copiar todo deja al usuario decidir en Revisión qué excluir; un `raw_json` del original con otra fecha y otras ediciones sería engañoso | No |
| D-51 | Permisos (T-29): `AndroidPermissionChecker` no repite lógica: las notificaciones usan la misma función que `AndroidNotificationPublisher` (permiso de Android 13+ y el interruptor de la app) y las exactas preguntan a `AlarmScheduler.canScheduleExact()`, así la UI nunca contradice lo que hace el scheduler; `CanScheduleExactAlarmsUseCase` se mantiene para el aviso de R-5.4. El banner de Inicio es un aviso con "Revisar" encima de la lista (o del estado vacío). En *Permisos*, "Corregir" de notificaciones pide el permiso en Android 13+ y, si queda denegado para siempre (sin diálogo de justificación), abre los ajustes de notificaciones de la app (R-11.8); por debajo de 13, o con el permiso concedido pero las notificaciones apagadas, va directo a ajustes. Ese código depende de la Activity y vive en `ui/permissions/PermissionRequests.kt`; los VM solo deciden si falta algo. Al volver con las exactas recién concedidas, *Permisos* llama a `RescheduleAll` (R-11.6) además del broadcast del sistema que recibe `RescheduleReceiver`: es idempotente. La nota de K-2 sobre dontkillmyapp.com es solo texto, sin enlace | Una sola fuente por permiso; el VM es testeable sin Android | No |
| D-52 | Flujo de permisos al programar (T-30): la explicación de notificaciones (R-11.1) se muestra una sola vez en la vida de la instalación, recordada con un booleano en `SharedPreferences` (`PermissionPromptStore`, sin dependencia nueva), y solo en Android 13+ con el permiso aún sin conceder; si luego sigue faltando, queda el banner de Inicio. "Ahora no" o denegar en el diálogo de Android no impide programar (R-7.8). La de alarmas exactas sale cada vez que faltan. Cerrar cualquiera de los dos diálogos cancela la programación. Al volver de "Abrir ajustes" se programa siempre, con exactas o en ventana según lo elegido, y si se concedieron se llama antes a `RescheduleAll` para el resto de órdenes (R-11.6). `SchedulingPermissionsUseCase` expone varios métodos en vez de `invoke`, porque el VM necesita preguntar y marcar por separado | Pedir permiso de notificaciones en cada programación cansa y Android deja de mostrarlo tras dos rechazos; el banner ya recuerda lo que falta | No |
| D-53 | Pulido (T-31): icono de app propio, un despertador con check en blanco sobre `#6650A4` (el primario de reserva del tema), dibujado en vectores dentro de la zona segura y reutilizado como capa monocroma para los iconos temáticos de Android 13+. Con minSdk 28 todos los dispositivos usan el icono adaptativo, así que se eliminan los `.webp` por densidad. Se borra `colors.xml` de la plantilla (los colores viven en `ui/theme`). No se crean canales al arrancar la app para las órdenes activas: R-5.9 ya los crea al programar y `publish` crea el suyo si falta (D-38), así que un canal borrado reaparece en el siguiente aviso | Identidad propia sin recursos rasterizados; menos código de arranque | No |
| D-54 | Verificación final (T-32): Kover 0.9.9 mide `com.mirkwood.rutinify.domain` y `com.mirkwood.rutinify.data.importer` excluyendo `*_Factory`, `*_MembersInjector` y `*$$serializer`, que genera el compilador junto a las clases medidas; la regla es `minBound(80)` sobre líneas. La prueba manual vive en `specs/manual-test.md` con el resultado de cada paso y se repite antes de cada versión | Medir solo el código escrito a mano; dejar constancia reproducible de la prueba en dispositivo | No |
| D-55 | Rutinas de varios días (v1.1): `dayOffset` admite 0..30 (`MAX_DAY_OFFSET` en `domain/importer`), así un tratamiento con fin ("antibiótico cada 8 h durante 7 días") llega en un solo JSON con una actividad por toma. `schemaVersion` sigue en `1`: el cambio solo amplía lo aceptado y todo JSON v1 anterior sigue siendo válido. No cambia el esquema de Room (`day_offset` ya es INTEGER) ni `TriggerTimeCalculator`, `RescheduleAllUseCase`, pausa o duplicado, que ya trabajan con `fecha + dayOffset`; el cálculo es por día, así que un cambio de horario de verano a mitad del tratamiento mantiene la hora de pared (D-02). El rango de la orden no se guarda: se deriva de `date` y `MAX(day_offset)`. Para el caso habitual (todos los días iguales) el contrato gana `routine.repeatDays` (1..30): la IA escribe un solo día y el parser lo expande en el borrador, así el JSON es corto y la IA no puede saltarse una toma a mitad de lista; `dayOffset` hasta 30 queda para planes irregulares (dosis que bajan, toma extra el primer día). No se mezclan: con `repeatDays` > 1 solo se admite `dayOffset` 0..1 (R-3.26), y el máximo efectivo sigue siendo 30. La expansión ocurre al importar: el borrador, la orden y Room solo ven actividades concretas (no se guarda `repeatDays`; sí queda en `raw_json`), así Revisión, pausa, duplicado y reprogramación no cambian. Una rutina es "de varios días" si su mayor `dayOffset` (tras expandir) es ≥ 2; por debajo, la UI no cambia. R-4.5 (fecha por defecto) no cambia: en un tratamiento sin fecha elige hoy si queda alguna toma futura y desmarca las pasadas. Los límites de 200 actividades por rutina y 500 pendientes en la app siguen (6 tomas × 31 días = 186). Sustituye a D-05 | Cubre los tratamientos y planes con fin sin recurrencia real (§15), que exigiría un esquema nuevo y programar la siguiente repetición en cada disparo. `repeatDays` + `dayOffset` elegido por Johan el 2026-11-14 | ✅ Aprobada por Johan el 2026-11-14 (máximo 30) |
| D-56 | Conflictos con rutinas de varios días: hay conflicto si los rangos comparten algún día (R-6.6), con la consulta `findOpenOverlapping(start, end)`: `date <= :end AND date(date, '+' \|\| (SELECT IFNULL(MAX(day_offset), 0) FROM alarm_item WHERE order_id = id) \|\| ' days') >= :start` (las fechas son texto ISO, así que `date()` de SQLite y la comparación de cadenas funcionan sin migración). "Reemplazar" sigue cancelando órdenes completas (R-6.2); el diálogo muestra los rangos y avisa (R-6.7). Alternativa descartada: reemplazar solo los días que coinciden, que partiría una orden en dos estados y complica pausa y duplicado | Con "misma fecha" un tratamiento que empezó ayer no avisaría al importar la rutina de hoy; el aviso evita cancelar un tratamiento entero por error; "Mantener ambas" es lo normal para pastillas + rutina diaria | ✅ Aprobada por Johan el 2026-11-14 |
| D-57 | `repeatDays` en el parser (T-33): el exceso tras expandir es un `IssueKind` propio, `TOO_MANY_REPEATED_ITEMS`, además de `REPEAT_DAYS_OUT_OF_RANGE` y `REPEAT_WITH_DAY_OFFSET`, porque R-3.27 pide otro texto. Igual que R-3.18, se comprueba antes de leer las actividades y corta la validación. Un `repeatDays` inválido se informa y las actividades se validan como un solo día, así sus errores salen en la misma pasada (R-3.16). La clave de cada copia es `día × nº de actividades + posición`: única y en el orden del JSON dentro de cada día. Las constantes `MAX_DAY_OFFSET`, `MAX_REPEAT_DAYS` y `MAX_DAY_OFFSET_WITH_REPEAT` viven en `DraftItemRules`. `dayOffset` 31 con repetición da solo el error de rango | Un único texto claro por error; claves estables para excluir o quitar una toma concreta (R-4.16) | No |
| D-58 | Conflictos (T-34): para comparar, una rutina que no es de varios días ocupa solo su fecha (`RoutineSpan.forConflicts`), tanto la nueva como las guardadas; la consulta aplica la misma regla con `CASE WHEN max_day_offset >= 2`. El rango se calcula con **todas** las actividades del borrador (también las excluidas, que se guardan como *Omitidas*), igual que el `MAX(day_offset)` de la orden guardada. `RoutineSpan` y `ConflictingOrder` viven en `domain/model`; `findOpenByDate` se sustituye por `findOpenOverlapping(span)`. En el diálogo, cada orden muestra "14 de noviembre" o "del 14 al 20 de noviembre" (el mes o el año en los dos extremos solo si cambian), con un título propio ("Ya hay rutinas en esas fechas") y el aviso de reemplazo. `UiText` gana `Plain` para textos ya formateados | Con el rango real, la madrugada (02:00, `dayOffset` 1) de la rutina diaria de hoy chocaría cada día con la de mañana, y "Reemplazar" la cancelaría; así las rutinas de un día se comportan como antes de v1.1 | No |
| D-59 | Revisión de varios días (T-35): `SchedulePreview` agrupa siempre por `dayOffset` (`days`, solo días con actividades) y dice si es de varios días con `RoutineDraft.spanOn(date).isMultiDay`; la UI solo usa los grupos en ese caso. Una actividad de madrugada va en el grupo de su `dayOffset`, que es su día de calendario. `ScheduleSummary.dayCount` cuenta los `dayOffset` distintos de las incluidas; el rango va del día de la primera incluida al de la última, aunque haya días sin alarmas en medio. El resumen se compone con plurales separados ("21 alarmas" + "7 días") y `SpanLabels.withWeekdays` ("del sábado 14 al viernes 20 de noviembre", con las reglas de mes y año de D-58); si solo queda un día con alarmas dice "3 alarmas en 1 día, el sábado 7 de noviembre". Las cabeceras no llevan año. En el `LazyColumn` la cabecera usa la key `"day-{dayOffset}"` y las filas su `key` de actividad | "del sábado 7 al sábado 7" no se lee bien; los días de la semana ayudan a ubicar un tratamiento; las keys de cabecera y fila no pueden chocar | No |
| D-60 | Inicio y detalle de varios días (T-36): `observeSummaries` añade `COALESCE(MAX(a.day_offset), 0) AS max_day_offset` y `OrderSummary` gana `maxDayOffset` (por defecto 0) y `span`; `OrderDetail` deriva su `span` de la línea de tiempo. `SpanLabels.long` da "jueves 5 de noviembre de 2026" o "del sábado 14 al viernes 20 de noviembre de 2026" (año solo al final, y en los dos extremos si cambia) y se usa en Inicio **y en la cabecera del detalle**, por coherencia. En el detalle, la cabecera "Día {k} · {fecha}" va dentro del elemento de la primera alarma de cada día (`TimelineItemUi.dayTitle`), así `scrollToIndex` sigue apuntando a alarmas; el día es la fecha local del disparo efectivo, de modo que una alarma pospuesta pasada la medianoche aparece en el día siguiente. Duplicar ya conservaba los `dayOffset`; se añade el test | Mismo rango en las dos pantallas que muestran la orden; no rehacer el desplazamiento a la próxima alarma | No |
| D-61 | Fecha por defecto de rutinas de varios días (T-38, aprobada por Johan el 2026-11-15): sin `date`, `DefaultDatePolicy` mira solo las actividades con `dayOffset` 0 cuando la rutina es de varios días (`RoutineDraft.spanOn(today).isMultiDay`). La madrugada del Día 1 no cuenta: tras expandir `repeatDays` tiene `dayOffset` 1, igual que todo el Día 2, y no se puede distinguir (con `dayOffset` ≤ 1 un tratamiento importado a las 23:00 seguiría empezando hoy por las 06:00 del Día 2). Las de un día no cambian. Se detectó en la prueba manual de T-37: importado a las 23:04, el tratamiento de 7 días empezaba hoy con el Día 1 ya pasado ("18 alarmas en 6 días") | Un tratamiento importado por la noche debe empezar mañana completo; el usuario puede elegir hoy con "Cambiar fecha" | ✅ |
| D-62 | Foco de producto desde v1.2: **planes de cuidado con horas fijas y duración limitada** (cachorro, bebé, medicación, posoperatorio). La productividad general no es objetivo | Revisión de producto del 2026-11-16: Tiimo, Structured, ChatGPT Scheduled Tasks y Gemini ya cubren la productividad; el hueco defendible es el cuidado con horas exactas | No (registro) |
| D-63 | "Plan" en la interfaz (strings `es` y `en`); `RoutineOrder`, `OrderDetail*`, `OrderDao`, `routine_order`, rutas `OrderDetailDestination` y el deep link `rutinify://order/…` no cambian. Los requisitos anteriores conservan "orden" | "Orden" no se entiende en la interfaz; renombrar el código sería un refactor grande sin valor para el usuario | No (pedido) |
| D-64 | Texto compartido (R-14): `MainActivity` declara el `intent-filter` `SEND` + `text/plain` y es **`singleTask`** (no `singleTop`: un envío llega en la tarea de la app que comparte, y solo `singleTask` recupera la tarea de Rutinify con su borrador). `IncomingIntents.classify` reconoce el envío (ignora los relanzados desde recientes, `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, y acepta texto con formato vía `getCharSequenceExtra`). El texto no viaja en la ruta: va a `SharedTextInbox` (memoria, `@Singleton`) y se navega a `NewRoutineDestination(fromShare = true)` con `popUpTo(Home)`. `NewRoutineViewModel` lo toma una sola vez (marca en `SavedStateHandle`), vacía `DraftStore` (`clear()`, nuevo) y valida como si se hubiera pegado. Con `singleTask`, tocar una notificación con la app abierta llega por `onNewIntent`, que ahora llama a `navController.handleDeepLink`. Implementado en T-40. | El objetivo es quitar pasos; una respuesta compartida es una intención clara de importar. Conservar un borrador a medias junto a uno nuevo complicaría la navegación | ✅ |
| D-65 | "Abrir en IA" (R-14.7): `Intent.createChooser(ACTION_SEND text/plain, "Enviar la plantilla a…")`. Si `resolveActivity` no encuentra destino, se copia al portapapeles (R-14.8). Requiere `<queries>` con `SEND text/plain` para que la comprobación funcione con la visibilidad de paquetes de Android 11+. Implementado en T-41: `TextSharer.canShareText()` (`AndroidTextSharer`, no cuenta la propia app) decide en el VM; el chooser excluye `MainActivity` con `EXTRA_EXCLUDE_COMPONENTS`, porque desde T-40 Rutinify también recibe texto y enviarse la plantilla la importaría. Desde Android 10 el chooser de `ACTION_SEND` ignora el título propio | ChatGPT, Gemini y Claude aceptan texto compartido en su caja de mensaje; el chooser deja elegir cualquiera sin acoplarse a ninguna | No |
| D-66 | Categorías críticas (R-15.1): tabla Room `category_setting(name TEXT PK, critical INTEGER NOT NULL)`, base de datos 1 → 2 con `Migration(1, 2)` manual. La marca es global por nombre normalizado de categoría. Se edita en una pantalla nueva *Ajustes* (icono de engranaje en la barra de Inicio), sección "Alarmas críticas", con un interruptor por cada categoría que aparece en algún plan guardado Aprobada por Johan el 2026-11-16 (recomendación aplicada). Implementado en T-43: `CategorySettingEntity`, `CategorySettingDao.observeUsed()` (categorías distintas de `alarm_item` con su marca, por `LEFT JOIN`), `CategorySettingsRepository` y `SetCategoryCriticalUseCase`, que guarda la marca y reprograma las pendientes futuras de esa categoría en planes activos. | Una categoría ("medicación") significa lo mismo en todos los planes; una tabla aparte no toca `alarm_item`. Falta confirmar el modelo (global frente a por plan) y dónde se marca | ✅ |
| D-67 | Mecanismo de la alarma crítica (R-15.6–R-15.10): `setAlarmClock(AlarmClockInfo(t, showIntent = detalle del plan), firePi)`; al disparar, notificación en el canal único `alarm_critical` ("Alarmas críticas", `IMPORTANCE_HIGH`, sonido = tono de alarma del sistema con `AudioAttributes.USAGE_ALARM`), `CATEGORY_ALARM`, `FLAG_INSISTENT` (repite el sonido) y `fullScreenIntent` a `AlarmActivity`. "Silenciar" y el corte a los 10 minutos republican la notificación con el mismo id en el canal `alarm_critical_silent` (sin sonido); el corte se programa con una alarma `…action.SILENCE` (requestCode = id, data `rutinify://alarm/{id}/silence`). Sin servicio en primer plano. Excepción a `CLAUDE.md` §10 ("canal de su categoría"): el sonido de un canal no se puede cambiar después de crearlo, así que las críticas no pueden usar `cat_<categoria>`; §10 se actualiza en T-45 Aprobada por Johan el 2026-11-16 (recomendación aplicada). | `CATEGORY_ALARM` con `USAGE_ALARM` pasa "No molestar" cuando este deja pasar alarmas, sin pedir acceso a la política de "No molestar"; `FLAG_INSISTENT` evita un servicio en primer plano y su declaración de tipo en Play. 10 minutos es el corte habitual de los despertadores | ✅ |
| D-68 | Pantalla completa (R-15.7, R-15.11): se declara `USE_FULL_SCREEN_INTENT`. En Android 14+ se comprueba `NotificationManager.canUseFullScreenIntent()` en cada disparo y en *Permisos*; si es falso, la notificación sale emergente (heads-up) con el mismo canal y botones, y *Permisos* ofrece `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. En Android 13 o menos el permiso se concede al instalar. Exige la declaración de Play Console: desde el 22-01-2025 Play solo lo concede por defecto a apps cuya función principal es llamadas o alarmas; en el resto lo revoca al instalar y hay que pedirlo al usuario En la declaración de Play Console se indica que las alarmas críticas son una función principal de alarma. Aprobada por Johan el 2026-11-16 (recomendación aplicada). | Con el foco de D-62 las alarmas críticas son función principal, pero Play puede no aceptarlo; el fallback mantiene el sonido aunque no haya pantalla completa | ✅ |
| D-69 | Revisión de D-09 (`USE_EXACT_ALARM`). En Android 14+ `SCHEDULE_EXACT_ALARM` está denegado por defecto en instalaciones nuevas, y `setAlarmClock` también lo necesita. Opciones: **A)** mantener D-09 y mejorar la petición (pedir el permiso al marcar la primera categoría crítica, R-15.5, y una tarjeta en Inicio tras el primer plan); **B)** declarar `USE_EXACT_ALARM` (concedido al instalar, el usuario no lo puede quitar) con `SCHEDULE_EXACT_ALARM` limitado a `maxSdkVersion 32`, más la declaración de Play como app de alarmas; **C)** A en v1.2 y probar B en la pista cerrada de v1.3, viendo si Play lo aprueba antes de producción. Pros de B: casi todos los usuarios tendrían alarmas exactas sin pasos. Contras de B: Play lo restringe a despertadores y calendarios, con revisión y riesgo de rechazo o retirada; un recordatorio de cuidados puede no encajar. Recomendación: **C**. **Elegida C** por Johan el 2026-11-16: v1.2 sale sin `USE_EXACT_ALARM` y con la petición guiada de A (R-15.5 y la tarjeta en Inicio); en la pista cerrada de v1.3 (T-54) se sube una versión con B y su declaración, y antes de pedir producción se decide con el resultado de la revisión de Play (si la rechaza, se queda A) | Es un cambio de la política de permisos: se pregunta antes de cerrar | ✅ |
| D-70 | Plantillas (R-16), implementado en T-42: `res/raw/prompt_template_generic.txt`, `…_puppy.txt`, `…_medication.txt`, `…_baby.txt` y `…_study.txt` sustituyen a `prompt_template.txt`. Todas comparten las instrucciones 1–5 del contrato, palabra por palabra; cada una añade un bloque "Consejos para…" y su propio ejemplo. El ejemplo canónico (el fixture `valid_v1.json`, un cachorro) pasa a la plantilla "Cachorro"; la "Genérica" lleva un ejemplo neutro (un día de mudanza). `TemplateScenario` (en `domain/model`, en el orden del selector: Cachorro, Medicación, Bebé, Estudio, Genérica) y `PromptTemplateSource.load(scenario)`; no hizo falta un `GetTemplateUseCase`. La última elección se guarda en `SharedPreferences` (`TemplateScenarioStore`, sin dependencia nueva). El selector es una fila de chips que pasa a la línea siguiente, para que el chip elegido siempre se vea. `raw-en/` llega con T-49 | Recursos separados para que cada escenario se ajuste sin tocar código; DataStore no aporta nada para un solo valor | No |
| D-71 | Inglés (R-13.3): `values-en/strings.xml` y `raw-en/` con las plantillas; `values/` sigue en español (idioma por defecto). `DateFormats` y `SpanLabels` dejan el `Locale` fijo `es-ES`: los patrones con palabras ("d 'de' MMMM") pasan a recursos (`date_pattern_*`) y se formatea con el `Locale` de la configuración actual. Los nombres de los tests siguen en español Implementado en T-49: los patrones van por papel en la etiqueta (`date_pattern_short`, `date_pattern_start_same_month`, `date_pattern_weekday_end_same_month_year`…), porque en inglés el orden cambia ("November 14–20" se escribe "from November 14 to 20"). `UiText.Date(date, patternId)` formatea en la UI con el `Locale` de la configuración; `SpanLabels` devuelve esos `UiText` y `DateFormats` queda solo con la hora. Plantillas en `raw-en/` con las mismas instrucciones traducidas y ejemplos propios. Lint trata `MissingTranslation` como error. Los tests de Robolectric fijan `qualifiers=es-rES` y los de VM leen el texto con `TestTexts` (`.es()`, `.en()`), que resuelve `strings.xml` sin Android. | Los patrones en español no se traducen con `ofLocalizedDate`, que no da "del … al …"; con recursos cada idioma tiene su orden | No |
| D-72 | Diagnóstico (R-17): tabla `diagnostic_counter(key TEXT PK, count INTEGER NOT NULL)`, base de datos 2 → 3. Claves estables: `issue.<IssueKind>`, `step.new_routine`, `step.validate`, `step.review`, `step.schedule_tap`, `step.scheduled`, `plans.created`. Interfaz `DiagnosticsRecorder` en `domain`; los VM y `ScheduleOrderUseCase` la llaman; no hace nada si el interruptor está apagado. Interruptor en `SharedPreferences`, apagado por defecto Aprobada por Johan el 2026-11-16 (recomendación aplicada). Implementado en T-51: `DiagnosticEvent` (claves de arriba), `DiagnosticsRecorder` y `DiagnosticsRepository` en `domain/diagnostics`; `DiagnosticsRepositoryImpl` guarda los contadores en Room y el interruptor en `SharedPreferences` (`diagnostics`); apagarlo borra los contadores. Cuentan *Nueva rutina* al abrirse, "Validar" y cada tipo de error (una vez por validación, solo errores, no avisos), *Revisión* al abrirse con borrador, "Programar alarmas", y `ScheduleOrderUseCase` al programar (`step.scheduled` y `plans.created`). | Opt-in y sin red: no cambia la ficha de seguridad de datos de Play ni R-13.6 | ✅ |
| D-73 | "Voy tarde" (R-18): `ShiftOrderAlarmsUseCase(orderId, minutes)` reescribe `time` y `day_offset` (hora de pared) de cada *Pendiente* y suma los minutos a `snoozed_until` si existe; todo en una transacción y bajo `AlarmMutationLock`, luego `schedule()` de cada una si la orden está `ACTIVE`. Rechaza el cambio entero si alguna queda ≤ ahora (al adelantar) o con `day_offset` fuera de 0..30. Diálogo con dos botones de sentido y un campo numérico de minutos (1–720). No vuelve a comprobar conflictos Aprobada por Johan el 2026-11-16 (recomendación aplicada). Implementado en T-55: `ShiftOrderAlarmsUseCase` con `preview()` e `invoke()` y resultado `ShiftResult` (`Ok(count, nextFrom, nextTo)`, `InPast(k)`, `OutOfRange`, `NotAllowed`); `AlarmRepository.updateSchedules` escribe todo en una transacción y solo toca *Pendientes*. La "próxima" de la vista previa es la pendiente que antes sonaba primero, con su hora pospuesta si la tiene. Al reprogramar mantiene el modo crítico (D-85). | Guardar la hora de pared mantiene la semántica de D-02 (cambio de zona) en vez de pasar a instantes absolutos; un cambio parcial confundiría | ✅ |
| D-74 | Editar una alarma (R-18.9): solo *Pendientes*; mismo formulario y reglas que Revisión (`DraftItemRules`); `UpdateAlarmUseCase` crea el canal si la categoría es nueva y reprograma Implementado en T-57: icono "Editar «…»" en cada tarjeta *Pendiente* de un plan *Activo* o *Pausado*; el diálogo "Editar actividad" pasa a `ui/common/EditActivityDialog` y lo usan Revisión y el detalle. `UpdateAlarmUseCase` recibe el texto del formulario y devuelve `Updated`, `Invalid` (por campo), `PastTime` o `NotEditable`; mantiene el día de la alarma, quita la posposición (la hora nueva la sustituye), crea el canal si la categoría es nueva, respeta el límite de 10 categorías del plan y reprograma con el modo crítico de la categoría nueva. | Reutiliza R-4.2 sin reglas nuevas | No |
| D-75 | Compartir (R-19): `ExportPlanUseCase` construye el JSON del contrato desde la orden (como `DuplicateOrderUseCase`). Enlace: `rutinify://import?v=1&d=<base64url(deflate(JSON minificado))>`; el QR contiene ese enlace y se genera en el teléfono. Límite: 2.331 bytes, capacidad en bytes de un QR versión 40 con corrección M. `MainActivity` declara un `intent-filter` `VIEW` para `rutinify://import`. Lectura con *Google code scanner* (`play-services-code-scanner`: el escaneo lo hacen los servicios de Google Play, sin permiso `CAMERA` ni `INTERNET` en la app) y generación con ZXing `core`. Las dos dependencias están en §13 ZXing `core` y `play-services-code-scanner` quedan aprobadas (§13). Aprobada por Johan el 2026-11-16 (recomendación aplicada). En T-58: `PlanExporter` (dominio) y `JsonPlanExporter` escriben el contrato v1 con todos los campos (nulos incluidos) y cada día con su `dayOffset`, sin reconstruir `repeatDays`, así se conservan los días editados; exporta todas las actividades del plan, ordenadas por día y hora. "Compartir plan" en el menú abre un diálogo con las opciones; el texto lleva "Importa este plan en Rutinify" y el JSON en un bloque de código, y el selector excluye a Rutinify. En T-59: `PlanLink` (dominio, `java.util.zip` y `Base64`, sin Android) codifica con deflate sin cabecera y base64url sin relleno, mide el enlace en bytes UTF-8 y decodifica con un tope de 100 KB para no inflar datos sin fin. `QrEncoder`/`ZxingQrEncoder` (ZXing `core` 3.5.3, corrección M, sin margen) devuelve una matriz que la pantalla dibuja en negro sobre blanco con zona tranquila de 4 módulos, también en tema oscuro. El enlace entra por `MainActivity` (`VIEW`, `rutinify://import`) y sigue el mismo camino que el texto compartido: *Nueva rutina* lo reconoce, lo decodifica y valida, o muestra R-19.8. El mismo camino servirá para el escáner (T-60). En T-60: `play-services-code-scanner` 16.1.0 mete `INTERNET` y `ACCESS_NETWORK_STATE` en el manifiesto fusionado para su telemetría; el manifiesto de la app los quita con `tools:node="remove"`, porque el escaneo lo hacen los servicios de Google Play en su proceso, y `ManifestPermissionsTest` lo vigila (R-13.6). "Escanear QR" en *Nueva rutina* es un efecto del VM que la pantalla convierte en `GmsBarcodeScanning.startScan()`; un QR que no es de Rutinify muestra R-19.8 y si el escáner no abre (sin servicios de Google Play), "No se pudo abrir el escáner de QR". | Comprimir hace que un plan típico (10–30 actividades) quepa de sobra; un esquema propio funciona sin dominio ni servidor. La cámara del sistema no siempre abre esquemas propios, por eso hay escáner dentro de la app | ✅ |
| D-76 | Filtro por responsable (R-19.9): quita del borrador las actividades de otros responsables (no las marca como excluidas, para que no aparezcan como *Omitidas* en el plan del otro cuidador) y conserva las que no tienen `assignee`, que se entienden como de todos Aprobada por Johan el 2026-11-16 (recomendación aplicada). Implementado en T-60: `Model.visibleDraft` en `ReviewViewModel` aplica el filtro a la vista, a la comprobación de conflictos y a la programación; el borrador completo se conserva para "Todas". Chips "Solo las tareas de…": "Todas" y cada responsable, ordenados. | "Solo mis tareas" no debe llenar el detalle de alarmas ajenas | ✅ |
| D-77 | Historial (R-20): tabla `alarm_event(id PK, alarm_id, order_id, type DONE/SKIPPED/SNOOZED, at)` con FK a `routine_order` en cascada, base de datos 3 → 4. La migración crea un evento `DONE` por cada alarma con `done_at` y `SKIPPED` por cada *Omitida* (con `status_changed_at`); las posposiciones previas a v1.4 no se conocen y cuentan 0. Los eventos se escriben en la misma transacción que el cambio de estado Implementado en T-61: `AlarmRepositoryImpl.updateStatus` escribe el evento `DONE`/`SKIPPED` y `snooze` el `SNOOZED` en la misma transacción, con un `INSERT … SELECT` que toma el plan de la propia alarma; así lo cumplen todos los casos de uso sin tocarlos. No se registra como evento la omisión al programar (actividades pasadas o excluidas en Revisión), porque no es un cambio de estado. | Los estados actuales no guardan cuántas veces se pospuso; una tabla de eventos permite el resumen y la exportación sin tocar `alarm_item` | No |
| D-78 | PDF (R-20.5) con `android.graphics.pdf.PdfDocument` del framework (sin dependencia), escrito en `cache/exports/` y compartido con `FileProvider`; se borra la exportación anterior al crear una nueva Implementado en T-62: `BuildHistoryReportUseCase` (resumen observable para el detalle e informe por día; un plan de un día con madrugada queda bajo su fecha, D-58) y `HistoryText`, que da las mismas líneas a "Texto" y a "PDF". El VM manda las líneas como `UiText`; la pantalla las traduce y, para el PDF, se las devuelve al VM, que llama a `ExportHistoryPdfUseCase` → `DocumentExporter` (`AndroidDocumentExporter`: A4, título en negrita, líneas partidas al ancho, `cache/exports/historial.pdf`, `FileProvider` con autoridad `${applicationId}.fileprovider`). El detalle muestra "{h} de {t} hechas" y, si hay omitidas o posposiciones, "· {o} omitidas · {p} pospuestas" en la misma línea. | Basta con texto y tablas simples; una librería de PDF no se justifica | No |
| D-79 | Widget (R-21) con Jetpack Glance (`glance-appwidget`, §13). `AlarmItemDao.observeNextPending()`; interfaz `WidgetUpdater` en `domain` que llaman los use cases que cambian alarmas u órdenes (R-21.4); el botón "Hecho" usa un `ActionCallback` que llama a `MarkAlarmDoneUseCase` `glance-appwidget` queda aprobada (§13). Aprobada por Johan el 2026-11-16 (recomendación aplicada). | Glance es la vía recomendada con Compose; la interfaz mantiene el dominio sin Android | ✅ |
| D-80 | IA en el dispositivo (R-22): ML Kit GenAI Prompt API (`com.google.mlkit:genai-prompt`, 1.0.0-beta4 a 2026-11-16, aprobación condicionada a T-65 en §13); requiere API 26 (el proyecto es 28) y no funciona con el bootloader desbloqueado. Solo se ofrece con `FeatureStatus.AVAILABLE`; con `DOWNLOADABLE` no se ofrece descargar en la primera versión. `OnDeviceRoutineImporter` implementa `RoutineImporter` y devuelve el mismo `ImportResult`. **`INTERNET` sin verificar**: la documentación oficial no dice si la app debe declararlo ni si la librería lo añade al manifiesto; el modelo lo gestiona AICore, un servicio del sistema. T-65 lo verifica con el manifiesto fusionado. Si aparece `INTERNET`: o se quita con `tools:node="remove"` (riesgo: que falle la descarga del modelo) o se acepta y se cambia R-13.6. Decisión abierta **Resultado de T-65 (2026-11-18):** con `genai-prompt` 1.0.0-beta4 el manifiesto fusionado solo gana `com.google.android.apps.aicore.service.BIND_SERVICE` (conectarse a AICore); la librería **no** añade `INTERNET`. El único `INTERNET` del árbol viene de la telemetría `transport-backend-cct`, que el manifiesto de la app ya quita desde T-60 (D-75), y el modelo lo descarga AICore, no la app. El APK de depuración pasa de 40,5 MB a 52,0 MB (+11,4 MB, sin R8). No se pudo probar en un dispositivo compatible (el emulador no tiene AICore); en uno no compatible el estado será `UNAVAILABLE` y la opción no se muestra (R-22.4). Se cumple la condición de la aprobación del 2026-11-16, así que la dependencia queda aprobada y R-13.6 no cambia; la prueba en un teléfono compatible queda para T-66 y T-71. | No se puede afirmar que cumple R-13.6 sin comprobar el APK |  ✅ (condición de T-65 cumplida) |
| D-81 | Schema v2 (R-23): `recurrence` propio (`weekdays` + `until`), no RRULE completo, que las IAs escriben mal y no hace falta para "todos los lunes". Modelo: tabla `routine_activity` (las actividades del día tipo) y columnas `recurrence_weekdays INTEGER NULL` (máscara de bits) y `recurrence_until TEXT NULL` en `routine_order`, base de datos 4 → 5. `alarm_item` sigue siendo una alarma concreta; `MaterializeRecurringUseCase` crea las de la ventana de 7 días (idempotente: no duplica una actividad y fecha ya creada) y la llaman `RescheduleAllUseCase` y una alarma diaria inexacta (`…action.MATERIALIZE`, `setAndAllowWhileIdle`, sin WorkManager). La regla de `requestCode` = id no cambia. Falta decidir cómo se ve un plan recurrente en *Revisión* (R-23.14): propuesta, el día tipo con la recurrencia en la cabecera y el resumen "{n} alarmas por semana" Se adopta la propuesta para *Revisión* (R-23.14). Aprobada por Johan el 2026-11-16 (recomendación aplicada). En T-67: `Recurrence(weekdays: Set<DayOfWeek>, until: LocalDate?)` en `RoutineDraft.recurrence`; el parser acepta `schemaVersion` 1 y 2, lee `routine.recurrence` solo en 2 (con 1 da R-23.3) y añade los errores `RECURRENCE_WEEKDAYS`, `RECURRENCE_UNTIL` y `RECURRENCE_COMBINATION`; los códigos de día se aceptan sin distinguir mayúsculas. Un v2 sin `recurrence` es igual que un v1. Las diez plantillas añaden el punto 6 con un ejemplo de `recurrence` en el texto (no en el bloque de código, para que el ejemplo v1 siga siendo el que se extrae). Hasta T-68 un borrador recurrente se programa como su día tipo. Implementado en T-68: tablas de arriba (§5), `MaterializeRecurringUseCase` (ventana de 7 días desde hoy, idempotente por el índice único, sin crear lo ya pasado ni nada de planes pausados), `ScheduleOrderUseCase` guarda el día tipo sin las actividades excluidas y crea solo la ventana, `ResumeOrderUseCase` la crea desde ese momento (R-23.12), Inicio y detalle muestran "Se repite lunes, miércoles y viernes, desde el 16 de noviembre" (R-23.10), un plan sin `until` no pasa a "Terminada" (R-23.11), duplicar conserva la recurrencia (R-23.13), exportar lo hace en v2 (R-19.1) y *Revisión* muestra el día tipo, la recurrencia y "{n} alarmas por semana" (R-23.14). | Programar sin fin rompería el límite de 500 de Android; una ventana fija mantiene acotadas las pendientes | ✅ |
| D-82 | Wear OS (R-24): primera iteración solo con notificaciones puenteadas: las notificaciones no son `setLocalOnly`, y las acciones "Hecho", "+5 min" y "Silenciar" son `PendingIntent` de broadcast que el reloj ejecuta en el teléfono. La pantalla completa no se muestra en el reloj; el reloj vibra con la notificación | Cubre el caso de uso sin módulo `:wear` ni Data Layer | No |
| D-83 | Versiones de Room: v2 en v1.2 (`category_setting`), v3 en v1.3 (`diagnostic_counter`), v4 en v1.4 (`alarm_event`), v5 en v2.0 (`routine_activity` y columnas de recurrencia). Migraciones manuales, sin `fallbackToDestructiveMigration`, con test `MigrationTestHelper` por paso y de 1 a la última En T-43: los esquemas exportados se añaden como assets de `debug` (Robolectric solo ve los assets fusionados de la variante probada, no `src/test`; nunca llegan al APK de release) y el test usa la ruta absoluta de la base de datos, porque en Windows el driver de prueba de Room compara el nombre con la ruta del archivo. | Los planes en curso de los usuarios no se pueden perder al actualizar | No |
| D-84 | Pantalla *Ajustes* (T-44): el engranaje de Inicio abre `SettingsDestination`. Sección "Alarmas críticas" con un texto que explica qué cambia (suena como despertador hasta que respondas, pasa "No molestar" si este deja pasar alarmas, y Android la muestra como próxima alarma, K-8) y una fila por categoría con su nombre legible ("paseo-largo" → "Paseo largo", la misma función que el nombre del canal, ahora `categoryDisplayName` en dominio) y un interruptor; toda la fila es pulsable. Sin categorías: "Aún no hay categorías. Aparecerán aquí cuando programes un plan." "La primera categoría crítica" de R-15.5 se mide sobre la lista visible: si no había ninguna marcada y faltan las exactas, sale el diálogo "Permiso de alarmas exactas" con "Abrir ajustes" y "Ahora no"; la marca se guarda igual (las alarmas suenan en ventana, R-15.4). El aviso de R-15.4 lleva también "Abrir ajustes". Al volver con las exactas recién concedidas se llama a `RescheduleAll`, como en *Permisos* (D-51). La petición de R-15.5 queda hecha aquí; T-48 añade solo la tarjeta de Inicio | Reutiliza el diálogo de R-11.2 y el patrón de *Permisos*; un botón junto al aviso evita volver a *Permisos* para corregirlo | No |
| D-85 | Alarmas críticas en el scheduler (T-45): `AlarmScheduler.schedule(alarmId, triggerAt, critical: CriticalAlarm?)`, donde `CriticalAlarm(orderId)` da el plan que abre el `showIntent` de `AlarmClockInfo` (el mismo `PendingIntent` que el cuerpo de la notificación, §6.1); null = alarma normal. El parámetro no tiene valor por defecto, así que ningún caso de uso puede olvidarlo: `ScheduleOrder`, `RescheduleAll`, `Resume`, `Restore`, `Snooze` y `SetCategoryCritical` leen `criticalNames()` (una consulta por operación). `setAlarmClock` devuelve `EXACT`. El canal `alarm_critical` suena con el tono de alarma del usuario (`TYPE_ALARM`; si no hay ninguno, el de alarma del sistema) y vibra; `alarm_critical_silent` (importancia normal, sin sonido ni vibración) se crea a la vez, aunque no se usa hasta T-46. Ambos canales quedan fuera del grupo "Rutinas" y se crean al publicar la primera alarma crítica. Con `FLAG_INSISTENT` el sonido para cuando la notificación desaparece: "Hecho", "+5 min", tocarla o deslizarla. La pantalla completa llega en T-46/T-47 | Un tipo con el `orderId` evita un segundo parámetro suelto; sin valor por defecto, el compilador encuentra cada llamada | No |
| D-86 | Pantalla de alarma y "Silenciar" (T-46): `AlarmActivity` propia (`showWhenLocked`, `turnScreenOn`, `taskAffinity=""`, fuera de recientes), abierta por el `fullScreenIntent` de la notificación crítica con `alarmId` y `orderId` como extras; muestra hora, título, descripción y plan, con "Hecho", "+5 min" y "Silenciar". La notificación que suena también lleva "Silenciar" (hace falta para el fallback de R-15.11). "Silenciar" y el corte usan el mismo `PendingIntent` (`…action.SILENCE` → `NotificationActionReceiver` → `SilenceAlarmUseCase`), que vuelve a publicar la notificación con el mismo id en `alarm_critical_silent`, sin `FLAG_INSISTENT` ni "Silenciar"; la alarma sigue *Disparada*. El corte se programa en `HandleAlarmFiredUseCase` a los 10 min del disparo real (no de la hora prevista), solo si la notificación se publicó, con `setExactAndAllowWhileIdle` (o `setWindow`); "Hecho", "+5 min" y "Silenciar" lo cancelan, y un corte tardío sobre una alarma que ya no está *Disparada* no hace nada. La pantalla se cierra cuando la alarma deja de estar *Disparada* (Room) o cuando entra en `SilencedAlarms`, un conjunto en memoria del proceso que avisa si se silenció desde la notificación o por el corte; no se guarda en Room porque la pantalla no sobrevive al proceso. Los botones corren en `@ApplicationScope` para terminar aunque la pantalla se cierre antes. Atrás cierra la pantalla y la alarma sigue sonando en la notificación. `USE_FULL_SCREEN_INTENT` se declara ya aquí, porque sin él la pantalla no se abre; T-47 añade la comprobación `canUseFullScreenIntent()` y la fila de *Permisos* | Mismo `PendingIntent` para botón y corte: un solo camino; sin cambiar el modelo de datos | No |
| D-87 | Grupo de notificaciones propio (K-13, tras T-46): toda notificación de alarma, crítica o no, lleva `setGroup("com.mirkwood.rutinify.ALARMS")` y `GROUP_ALERT_CHILDREN`. Tras publicar cada una se publica o actualiza un resumen (tag `alarm_summary`, id 0, título "Rutinify", en el canal de la alarma recién publicada) con `GROUP_ALERT_CHILDREN`, así que el resumen nunca suena y cada alarma suena por su canal. `cancel` y `cancelForOrder` quitan el resumen cuando ya no queda ninguna alarma, sin contar las que acaban de cancelar (`NotificationManager.cancel` es asíncrono y siguen apareciendo como activas un instante; visto en T-47). En la bandeja se ve igual que la agrupación automática de Android (cabecera "Rutinify" con las alarmas dentro); en Android 9–15 también se agrupan | Android 16 agrupa por su cuenta las notificaciones de una app sin grupo y les pone `FLAG_SILENT`, que la app no puede quitar; un grupo propio con resumen queda fuera de esa agrupación | No |
| D-88 | Pantalla completa (T-47, R-15.11): `PermissionChecker.fullScreenIntentGranted()` (`canUseFullScreenIntent()` en Android 14+, `true` por debajo) y `PermissionStatus.fullScreenIntent`. `AndroidNotificationPublisher` lo consulta en cada alarma crítica y, si falta, no pone `fullScreenIntent`: la notificación sale emergente con el mismo canal, sonido y botones. *Permisos* muestra siempre la tarjeta "Alarmas a pantalla completa" con "Activado"/"Desactivado" y "Corregir" → `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`; cuenta para su texto "Todo listo". El banner de Inicio (`PermissionStatus.allGranted`) no la cuenta salvo que haya alguna categoría crítica, porque sin críticas no afecta a nada y sin el banner no se llega a *Permisos* | Mismo patrón que los otros permisos (D-51); el banner solo avisa de lo que de verdad falla | No |
| D-89 | Tarjeta de alarmas exactas en Inicio (T-48, R-11.2, D-69 opción C): `ExactAlarmsCardUseCase` la muestra cuando existe al menos un plan y faltan las alarmas exactas, hasta que el usuario pulsa "Ahora no" (recordado en `PermissionPromptStore`, el mismo `SharedPreferences` de D-52). Título "Activa las alarmas exactas", texto que explica el retraso y botones "Ahora no" y "Activar" (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`). Mientras se ve y lo único que falta son las exactas, sustituye al banner de permisos; si falta algo más, se ven los dos. Tras "Ahora no" vuelve el banner. La petición de R-15.5 ya estaba en T-44 (D-84). El manifiesto sigue sin `USE_EXACT_ALARM`, comprobado por `ManifestPermissionsTest`, que también comprueba que no se pide `INTERNET` (R-13.6) | Un solo aviso a la vez para lo mismo; "Ahora no" respeta al usuario y el banner sigue recordando lo que falta | No |
| D-90 | Diagnóstico en *Ajustes* (T-52, R-17.1, R-17.5, R-17.6): sección "Diagnóstico" bajo "Alarmas críticas" con el interruptor "Guardar diagnóstico en este teléfono", un texto que explica que no se envía nada y que apagarlo lo borra, y los botones "Exportar diagnóstico" y "Borrar diagnóstico" (snackbar "Diagnóstico borrado"). El texto exportado es una cabecera traducida con la versión de la app (`AppInfo`, del `PackageManager`, sin `BuildConfig`) y una línea `clave: número` por contador, con las claves técnicas de D-72 para poder sumar exportaciones de varios teléfonos; sin contadores dice "Sin datos todavía". Se exporta aunque el interruptor esté apagado (entonces no hay datos) | Claves estables y sin traducir: son datos para quien analiza, no textos de la interfaz | No |
| D-91 | "Voy tarde" en el detalle (T-56, R-18.1, R-18.2): botón en la cabecera junto a "Pausar"/"Reanudar", visible si el plan está *Activo* o *Pausado* y tiene pendientes. Diálogo "Mover las alarmas" con dos chips ("Retrasar", seleccionado al abrir, y "Adelantar"), campo "Minutos" vacío y solo con cifras (máximo 3), y debajo la vista previa o el motivo en rojo: "Escribe entre 1 y 720 minutos", R-18.5, R-18.6 o "No hay alarmas pendientes que mover". "Mover" solo se activa con una vista previa válida; al moverse, snackbar "{n} alarmas movidas". Si el plan cambió entre la vista previa y "Mover", se muestra el motivo en vez de cerrar | Siempre ves qué va a pasar antes de tocar nada; el campo vacío evita mover sin querer | No |
| D-92 | Actualizar el widget (T-63, R-21.4), en lugar del `WidgetUpdater` llamado desde cada caso de uso de D-79: `WidgetRefresher` se arranca en `RutinifyApp.onCreate` y sigue `ObserveNextAlarmUseCase` (Room); en cada cambio de la próxima alarma llama a `updateAll`. Todo cambio de alarmas u órdenes pasa por Room en el mismo proceso, y un receiver que despierta el proceso también arranca la app, así que cubre disparar, marcar, posponer, mover, reprogramar, pausar y borrar sin tocar los casos de uso. `updatePeriodMillis` de 30 min queda como respaldo (por ejemplo, el paso de "mañana" a "hoy" a medianoche). El widget lee los casos de uso por un `@EntryPoint` de Hilt; "Hecho" es un `ActionCallback` que llama a `MarkAlarmDoneUseCase`; tocar fuera del botón abre `rutinify://order/{id}?alarm={id}` (R-21.5). Hora "14:00" si es hoy, "02:00 · mañana" o "06:00 · lunes 16 de noviembre" | Un solo punto que sigue los datos no se olvida de ningún caso de uso nuevo | No |
| D-93 | IA en el teléfono (T-66, R-22): en vez de un `OnDeviceRoutineImporter`, `ConvertOnDeviceUseCase` sobre la interfaz `OnDeviceModel` (dominio) y `MlKitOnDeviceModel` (datos), porque la conversión tiene salidas que el importador no tiene (demasiado largo, fallo del modelo) y la respuesta se valida con el mismo `RoutineImporter` que el texto pegado. Firmas comprobadas con `javap` en el AAR 1.0.0-beta4: `checkStatus`, `countTokens`, `getTokenLimit` y `generateContent` son `suspend`; `GenAiException` está en `com.google.mlkit.genai.common`. Un cliente por llamada, siempre cerrado con `close()`, así no queda un modelo cargado. La plantilla del escenario (sin la línea del marcador) va como `PromptPrefix` (caché implícita del prefijo, porque pasa de 200 palabras) y la rutina como `TextPart`; temperatura 0,2 y hasta 2.048 tokens de salida. R-22.6 usa `countTokens` + salida ≤ `getTokenLimit`. *Nueva rutina* solo muestra la tarjeta "Convertir en el teléfono" con `FeatureStatus.AVAILABLE`; una respuesta inválida aparece en el campo JSON con sus errores. Sin probar en un teléfono con Gemini Nano | El dominio no depende de la librería y se prueba con un modelo falso | No |
| D-94 | Repaso diario de los planes recurrentes (T-68, R-23.7): `RescheduleAllUseCase` llama primero a `RecurringMaterializer` (implementado por `MaterializeRecurringUseCase`, sin lock propio porque se llama con `AlarmMutationLock` ya tomado desde reprogramar, programar y reanudar) y programa con `setAndAllowWhileIdle` (inexacta) la próxima 03:00 local un broadcast `…action.MATERIALIZE` (`rutinify://materialize`) a `RescheduleReceiver`, que vuelve a reprogramar todo. Ese `PendingIntent` usa `requestCode` 0: no es de ninguna alarma, y su acción y data no las comparte ningún otro (excepción a `CLAUDE.md` §10, que se actualiza). Solo se crean ocurrencias futuras de planes *Activos*. "Voy tarde" y "Editar" en un plan recurrente cambian solo las alarmas ya creadas; las siguientes salen del día tipo | Sin WorkManager ni alarmas exactas para algo que no necesita precisión; un único punto de entrada idempotente | No |
| D-95 | Conflictos con recurrencia (T-69, R-23.8, R-23.9): en vez de dar a `RoutineSpan` un fin opcional, que rompería las etiquetas y el número de días que la necesitan cerrada, el tipo de dominio `ConflictDays(start, end?, weekdays)` describe los días que reclama un plan: un rango normal reclama todos los días de la semana (con `forConflicts`, D-58) y uno recurrente solo los suyos, sin fin si no tiene `until`. Dos se cruzan si en la primera semana de sus fechas comunes hay un día de la semana compartido. El SQL sigue filtrando por fecha los planes normales (fin abierto = 9999-12-31) y excluye los recurrentes, que se leen aparte (`getOpenRecurring`) y se comprueban en Kotlin; su rango es su día de inicio, porque los `day_offset` guardados solo reflejan las ocurrencias ya creadas. En *Revisión* un plan recurrente en conflicto se describe con su recurrencia ("Gimnasio (Se repite jueves, desde el 29 de octubre)"). El límite de 500 cuenta las alarmas que crearía la ventana (`windowOffsets`, la misma función que usa `MaterializeRecurringUseCase`) | La comparación por día de la semana vive en el dominio y se prueba sin Room; los planes son pocos, así que leer los recurrentes abiertos no cuesta | No |
| D-96 | Notificaciones en el reloj (T-70, R-24, implementa D-82): las notificaciones de alarma siguen sin `setLocalOnly` y sin acciones propias del reloj (`WearableExtender`), así que el reloj muestra las mismas del teléfono. Cada acción lleva icono (`ic_action_done`, `ic_action_snooze`, `ic_action_silence`, blancos de 24 dp): el reloj las dibuja como botones y el teléfono, desde Android 7, sigue mostrando solo el texto (comprobado en el emulador). Las tres son broadcasts a `NotificationActionReceiver`, que corren en el teléfono pulse donde se pulse. El resumen mudo del grupo (D-87) también se puentea; el reloj lo usa como cabecera del grupo. Sin probar con un reloj: no hay imagen de Wear OS en el equipo y emparejarlo necesita la app complementaria con sesión de Google | Sin módulo `:wear` ni dependencias nuevas | No |
| D-97 | Revisión del manifiesto v2.0 (T-71, R-13.6): `RescheduleReceiver` sigue exportado porque recibe `BOOT_COMPLETED` y los cambios de hora, y desde T-68 también acepta la acción propia `…action.MATERIALIZE` del repaso diario. Otra app podría enviarle esa acción de forma explícita y provocar un reprogramado completo; se acepta porque es idempotente (Room manda, D-94), no borra ni crea nada que no tocara y como mucho gasta algo de batería. Alternativa descartada por ahora: un receiver no exportado solo para el repaso, que añade un componente y otro `PendingIntent` | El riesgo es bajo y la reprogramación ya está pensada para repetirse | No |
| D-98 | Textos que no se parten (revisión de diseño, 2026-11-19): en el detalle del plan la hora crece con la letra (`widthIn(min = 64.dp)`) y las etiquetas "categoría · estado · Próxima" van en un `FlowRow`, así que bajan enteras en vez de cortarse ("Próx / ima"); con la letra a partir de 1,5× los botones editar/hecho/borrar pasan a una fila bajo el texto para que el título no se rompa. `ActionButtonRow` (ui/common) pone los botones de *Nueva rutina* con el mismo ancho si caben así, con su ancho natural y el sobrante repartido si solo caben así, y uno por línea a todo el ancho si no; ninguna etiqueta de botón se parte. Comprobado en el emulador al 100 % y al 200 % | Las reglas de accesibilidad piden que el texto crezca y el diseño se reorganice, sin cortar palabras | No |
| D-99 | Reorganización tras la revisión de diseño (2026-11-19, sin dependencias nuevas): Inicio empieza con la tarjeta "Próxima alarma" (`primaryContainer`, hora en `displaySmall`, "Hecho" relleno a la derecha; usa `ObserveNextAlarmUseCase`, `MarkAlarmDoneUseCase` y el texto de hora del widget) y separa "En curso" (tarjetas con pendientes) de "Anteriores" (filas `ListItem` sin contenedor ni recuento), para que lo que sigue en marcha mande y lo terminado quede en segundo plano (R-8.1, R-8.7). En el detalle, "Eliminar todas las alarmas" pasa al menú ⋮ junto a "Eliminar plan": una acción destructiva no debe ser lo más llamativo de la cabecera. En *Nueva rutina*, "Abrir en IA" es el botón relleno y va primero, porque manda la plantilla a la app de IA en un paso; "Copiar plantilla" queda al lado (R-14.6) | La pantalla dice primero lo que importa ahora; el tema Expressive queda para cuando se apruebe `material3` 1.5 | No |
| D-100 | Validación con las skills *adaptive* y *compose-performance* (2026-11-19, sin dependencias nuevas): en ventanas grandes el contenido de cada pantalla queda centrado y con un máximo de 840 dp (`contentSideMargin()` en `ui/common`, a partir de `LocalWindowInfo`, sin subcomposición) e Inicio usa `LazyVerticalGrid` con `GridCells.Adaptive(360.dp)`: una columna en el teléfono, dos en tablet, con la próxima alarma y las cabeceras a todo el ancho. Todas las listas *lazy* indican `contentType` (en el detalle, la alarma que abre un día es otro tipo porque lleva su cabecera); el detalle lee `LocalDensity` una vez para toda la lista y no en cada tarjeta; `UiText.Date` reutiliza los `DateTimeFormatter` por patrón e idioma. Pendiente y con dependencias por aprobar: lista-detalle con Navigation 3, Baseline Profiles y pruebas de capturas por tamaño. El giro conserva el texto de *Nueva rutina* (comprobado) | Arreglos baratos que quitan lo peor en tablet y en listas largas sin tocar la navegación | No |
| D-101 | Selector de hora al editar (2026-11-19, R-4.2, R-18.9; antes en `backlog.md`): en el diálogo "Editar actividad" el campo de hora es de solo lectura y al tocarlo abre `RoutineTimePickerDialog` (`TimePicker` de Material 3 en 24 h, con un botón para pasar a `TimeInput` y escribir horas y minutos en dos casillas que solo aceptan valores válidos). El formulario sigue guardando la hora como texto "HH:mm" (`ActivityForm.timeText`) y los ViewModels la validan igual, así que el error de hora ya pasada (R-18.9) se sigue mostrando. Si el texto no es una hora válida, el selector abre a mediodía. Sin dependencias nuevas: viene con `material3` 1.4 | El selector no deja elegir una hora imposible ni obliga a teclear el formato | No |
| D-102 | «Inicio automático» en Xiaomi (2026-11-19, R-11.9, R-11.10): en un Redmi con HyperOS 3, con la app cerrada desde Recientes, las alarmas normales no aparecían y las críticas sí. `appops` mostraba `MIUIOP(10008): ignore` y un rechazo reciente: HyperOS no deja arrancar una app cerrada sin ese permiso, y el broadcast de `setExactAndAllowWhileIdle` necesita arrancarla; `setAlarmClock` sí pasa. La app no puede concederlo. `PermissionChecker.autoStartStatus()` devuelve `NOT_APPLICABLE` fuera de Xiaomi/Redmi/POCO y, en ellos, lee la operación 10008 con el `AppOpsManager.checkOpNoThrow(int, int, String)` oculto por reflexión (`XiaomiAutoStart`): permitido → `ALLOWED`, ignorado o error → `DENIED`, cualquier otra cosa o un fallo → `UNKNOWN`, nunca una suposición. Inicio muestra la tarjeta tras el primer plan, como la de alarmas exactas (D-89), con dos textos según se pudo comprobar o no; *Permisos* tiene la fila fija. "Abrir ajustes" abre la página de la app (`ACTION_APPLICATION_DETAILS_SETTINGS`), donde MIUI y HyperOS ponen el interruptor junto a "Ahorro de batería"; su pantalla propia de inicio automático es una actividad privada que cambia entre versiones. Otros fabricantes con restricciones parecidas (Huawei, Oppo, Vivo) quedan en `backlog.md` | El fallo deja sin alarmas a una marca muy extendida, y avisar con una comprobación prudente es lo único que puede hacer la app | No |
| D-01..D-04 | Aprobadas por Johan el 2026-11-04 | | ✅ |

## 15. Preparación para v2

Desde v1.2 el roadmap de §17 cubre la recurrencia sin fin (R-23), el widget (R-21), compartir sin servidor (R-19) y la conversión en el dispositivo (R-22). Queda preparado para después:

- **Alarmas relativas ("me desperté")**: añadir a `AlarmItem` un `anchor` opcional (`ABSOLUTE` | `RELATIVE_TO_EVENT`) y `offsetMin`; `TriggerTimeCalculator` ya es el único punto que calcula horas.
- **API de una IA en la nube**: `RoutineImporter` es una interfaz; un importador en la nube produciría el mismo `ImportResult`, igual que `OnDeviceRoutineImporter` en v2.0. Choca con R-13.6, así que sigue fuera de alcance.
- **Exportar .ics**: `ExportPlanUseCase` (v1.4) y el historial de eventos (D-77) dan los datos; falta el formato.
- **App propia de Wear OS**: las acciones ya pasan por use cases; un módulo `:wear` usaría la Data Layer API.
- Esquemas de Room exportados para migraciones.

## 16. Riesgos

| Id | Riesgo | Mitigación |
|---|---|---|
| K-1 | Compatibilidad de Hilt/KSP con AGP 9 (Kotlin integrado) | T-01 verifica primero; si no compila, documentar la combinación de versiones que funcione o usar `android.builtInKotlin=false` temporalmente y anotarlo aquí. |
| K-2 | Fabricantes con optimización de batería agresiva (Xiaomi, Huawei…) matan alarmas | Fuera de MVP; en *Permisos* se añade un enlace informativo a dontkillmyapp.com (solo texto). |
| K-3 | ~30 alarmas/día exactas → impacto en batería | Aceptable: `setExactAndAllowWhileIdle` es el mecanismo previsto; cada disparo hace trabajo mínimo. |
| K-4 | El usuario fuerza la detención → Android borra las alarmas sin aviso | Reconciliación en `RutinifyApp.onCreate` (§7.2). |
| K-5 | Texto pegado malicioso o accidentalmente enorme (no se puede saber quién generó el JSON) | No se confía en el origen sino en la validación: límites de D-32, Revisión obligatoria antes de programar (R-4), sin permiso `INTERNET` (R-13.6), Room con parámetros enlazados, texto plano en UI y notificaciones. |
| K-6 | Play no concede `USE_FULL_SCREEN_INTENT` por defecto (solo a apps de llamadas o alarmas, desde el 22-01-2025) o rechaza la declaración | Fallback a notificación emergente con el mismo sonido (R-15.11) y fila en *Permisos*; declarar las alarmas críticas como función principal (D-68) |
| K-7 | Sin `SCHEDULE_EXACT_ALARM` (denegado por defecto en Android 14+), las alarmas críticas tampoco son exactas: `setAlarmClock` lo necesita | Petición guiada (R-15.5) y la estrategia C de D-69 (elegida); aviso visible (R-15.4) |
| K-8 | `setAlarmClock` muestra el icono de alarma en la barra de estado y aparece como "próxima alarma" en el reloj del sistema y en algunos widgets | Es el comportamiento esperado de una alarma real; se documenta en *Ajustes* junto al interruptor |
| K-9 | El comportamiento de "No molestar" con `CATEGORY_ALARM` varía entre fabricantes | Casos manuales en dispositivo real (manual-test, pasos 20–23); si falla en alguno, documentarlo como limitación conocida |
| K-10 | Gemini Nano solo existe en algunos teléfonos (Pixel 9+, Galaxy S25+…), la API está en beta y puede cambiar, y la dependencia podría exigir `INTERNET` | Opción oculta si no hay modelo (R-22.4); verificación previa (T-65, D-80); el flujo de copiar y pegar nunca se quita |
| K-11 | Un plan grande no cabe en un QR (2.331 bytes con corrección M) | Compresión deflate (D-75) y "Compartir como texto" cuando no cabe (R-19.5) |
| K-12 | Algunas apps de IA comparten texto con formato o recortado, o no aceptan `ACTION_SEND` de entrada | La extracción tolerante (R-3.1) ya acepta texto alrededor; casos manuales con ChatGPT y Gemini (manual-test, pasos 25–28); "Copiar plantilla" sigue disponible |
| K-13 | Android 16 agrupa por su cuenta las notificaciones de una app en cuanto hay dos o más sin grupo (`g:Aggregate_AlertingSection`) y les pone `FLAG_SILENT`: una alarma que llega con otra notificación de Rutinify en la bandeja no suena. Comprobado en el emulador (API 36) en T-46 con dos alarmas críticas; con una sola notificación sí suena. Afecta también a los recordatorios normales | Resuelto tras T-46 (D-87): grupo propio con un resumen mudo que deja sonar a cada alarma (`GROUP_ALERT_CHILDREN`), así el sistema no las agrupa |

## 17. Evolución v1.2–v2.0

> Roadmap posterior a v1.1. Nada de esta sección está implementado. Cada apartado dice cómo encaja en la arquitectura de §1–§11; las decisiones abiertas están marcadas en §14.

### 17.1 v1.2 · Usable para otros

**Entrada compartida (R-14, D-64, D-65).**
- Manifiesto: `MainActivity` con `android:launchMode="singleTask"` y un `intent-filter` para `SEND` + `text/plain`; `<queries>` para `SEND text/plain` (T-41).
- `NewRoutineDestination(fromShare: Boolean = false)`; el texto espera en `SharedTextInbox` para no viajar en la ruta (D-64).
- `NewRoutineViewModel` lo toma una vez, lo coloca en el campo (`SavedStateHandle`) y lanza la misma validación que "Validar".
- Si el texto falta o está vacío, se emite el efecto con "No llegó ningún texto para importar".
- "Abrir en IA" es un efecto `NewRoutineEffect.ShareTemplate(text)` que la `Route` convierte en el chooser.
- No hay use case nuevo: la validación reutiliza `RoutineImporter`.

**Modo alarma real (R-15, D-66–D-69).**
- Dominio:
  - `CategorySetting(name, critical)`.
  - `CategorySettingsRepository` (`observe()`, `setCritical(name, critical)`, `criticalNames()`).
  - `SetCategoryCriticalUseCase`: guarda la marca y reprograma las pendientes de esa categoría (R-15.2).
- `AlarmScheduler.schedule(alarmId, triggerAt, critical: CriticalAlarm?)` (T-45, D-85). Con `critical` y permiso exacto → `setAlarmClock`; sin permiso → `setWindow` (R-15.4). El `requestCode` y el `PendingIntent` de disparo no cambian (§6.1), así que cancelar sigue igual.
- `HandleAlarmFiredUseCase` pregunta al repositorio si la categoría es crítica y publica `AlarmNotification(…, critical = true)`.
- `AndroidNotificationPublisher` decide la pantalla completa con `canUseFullScreenIntent()`.
- Datos: Room v2 con `category_setting` (D-66, D-83).
- `NotificationActionReceiver` gana la acción `…action.SILENCE` y `AlarmReceiver` la del corte a 10 min (D-67).
- `RescheduleAllUseCase` pasa `critical` en cada `schedule()` (R-15.13).
- Pantallas nuevas:
  - `AlarmActivity` (`showWhenLocked`, `turnScreenOn`, sin historial, tema propio): muestra el estado del VM y llama a los mismos use cases que los botones de la notificación.
  - *Ajustes* (`SettingsDestination`): sección "Alarmas críticas".
- *Permisos* gana la fila de pantalla completa (Android 14+).
- Permisos en manifiesto: `USE_FULL_SCREEN_INTENT`. `USE_EXACT_ALARM` no entra en v1.2 (D-69, opción C); solo en la versión de prueba de v1.3 (T-54).

**Plantillas (R-16, D-70).** `TemplateScenario { GENERIC, PUPPY, MEDICATION, BABY, STUDY }`; `GetTemplateUseCase(scenario)`; el selector vive en `NewRoutineUiState.scenario`. El test de plantilla (§12) pasa a recorrer los cinco recursos en `raw/` y `raw-en/`.

**"Plan" en la interfaz (R-13.7, D-63).** Solo `strings.xml`: se buscan las cadenas con "orden" y se reescriben; un test lee el `strings.xml` español y falla si queda la palabra "orden" en un texto visible.

**Inglés (R-13.3, D-71).** `values-en/`, `raw-en/`, patrones de fecha en recursos. `ErrorMessages` no cambia (ya devuelve ids de recursos). Lint `MissingTranslation` como error.

### 17.2 v1.3 · Validación con usuarios

**Diagnóstico (R-17, D-72).**
- Room v3 con `diagnostic_counter`.
- `DiagnosticsRecorder` (dominio) → `RoomDiagnosticsRecorder`.
- `ExportDiagnosticsUseCase` devuelve el texto.
- En *Ajustes*: el interruptor, "Exportar diagnóstico" y "Borrar diagnóstico".
- No añade permisos.

**Publicación (sin código).** La checklist vive en `specs/release-checklist.md` (T-53):
- ficha de Play;
- política de privacidad publicada (por ejemplo, en GitHub Pages: la app no la necesita en tiempo de ejecución);
- formulario de seguridad de datos ("no se recogen datos");
- declaraciones de `USE_FULL_SCREEN_INTENT` y, si aplica, `USE_EXACT_ALARM`;
- clasificación de contenido;
- `targetSdk` vigente.

Prueba cerrada: si la cuenta de desarrollador personal es posterior al 13-11-2023, hacen falta al menos 12 testers con la prueba aceptada de forma continua durante los 14 días anteriores a pedir producción. Conviene reclutar más de 12 como margen. Métrica de éxito: el usuario programa un segundo plan (`plans.created ≥ 2` en el diagnóstico exportado, o preguntándolo).

### 17.3 v1.4 · Nicho de cuidados

**Reprogramar (R-18, D-73, D-74).**
- `ShiftOrderAlarmsUseCase(orderId, minutes): ShiftResult` con los casos `Shifted(n)`, `WouldBePast(k)` y `OutOfRange`.
- `UpdateAlarmUseCase(alarmId, time, title, category)`.
- `AlarmItemDao`: `updateTimeAndOffset(ids…)` y `update(entity)`.
- Detalle: acción "Voy tarde" en la cabecera y "Editar" en cada tarjeta *Pendiente*.
- Sin migración.

**Compartir (R-19, D-75, D-76).**
- Use cases:
  - `ExportPlanUseCase(orderId): String` (JSON);
  - `EncodePlanLinkUseCase(json): String?` (null si supera 2.331 bytes);
  - `DecodePlanLinkUseCase(uri): String?`.
- `QrEncoder` (interfaz en dominio, ZXing en `data/system`).
- `CodeScanner` (interfaz; implementación con Google code scanner).
- `MainActivity`: `intent-filter` `VIEW` para `rutinify://import`, que reutiliza el camino de R-14.
- *Revisión*: `ReviewAction.FilterAssignee(name?)`.
- Sin migración.

**Historial (R-20, D-77, D-78).**
- Room v4 con `alarm_event`.
- `MarkAlarmDoneUseCase`, `SnoozeAlarmUseCase`, `ResumeOrderUseCase` y `RescheduleAllUseCase` escriben el evento en la misma transacción que el cambio de estado.
- `BuildHistoryReportUseCase(orderId): HistoryReport` (dominio, testeable).
- `HistoryTextFormatter` y `PdfHistoryExporter` (en `data`).
- `FileProvider` con `cache/exports/`.

**Widget (R-21, D-79).**
- `NextAlarmWidget` (Glance) en `ui/widget/`.
- `ObserveNextAlarmUseCase`.
- `WidgetUpdater` en dominio → `GlanceWidgetUpdater`.
- Receiver del widget declarado en el manifiesto.

### 17.4 v2.0 · Diferenciación

**IA en el dispositivo (R-22, D-80).**
- `OnDeviceRoutineImporter` (en `data/importer/ondevice`): comprueba `checkStatus()`, genera con el texto de la plantilla y pasa la respuesta por `JsonExtractor` + `RoutineJsonParser`.
- `NewRoutineUiState.onDeviceAvailable`.
- Antes de nada, T-65 verifica el manifiesto fusionado (`INTERNET`).

**Schema v2 (R-23, D-81).**
- Parser: `schemaVersion` 1 o 2 y lectura de `recurrence` con sus errores (R-23.3–R-23.6).
- `RoutineDraft.recurrence: Recurrence?`.
- Room v5 con `routine_activity` y columnas de recurrencia.
- `MaterializeRecurringUseCase` (ventana de 7 días), llamado por `RescheduleAllUseCase` y por `…action.MATERIALIZE`.
- `RoutineSpan` acepta un fin abierto para los conflictos (R-23.9).
- `ScheduleOrderUseCase` cuenta para el límite de 500 solo las de la ventana (R-23.8).
- `OrderSummary` y `SpanLabels` muestran la recurrencia (R-23.10).

**Wear OS (R-24, D-82).** Sin módulo nuevo: se revisa que ninguna notificación sea `setLocalOnly` y se añaden las acciones a `WearableExtender` si hace falta para el orden en el reloj.
