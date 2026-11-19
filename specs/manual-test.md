# Prueba manual — Rutinify

Checklist de T-32 y T-37 (R-13.5, R-13.6). Se repite antes de cada versión, en un dispositivo real o en el emulador
`Pixel_9_Pro_API_36` (Android 16), con la app instalada desde `assembleDebug`.

Cada paso dice qué hacer y qué se espera. La columna *Resultado* recoge la última ejecución.

## Preparación

- Rutina de prueba: `app/src/test/resources/fixtures/valid_v1.json` ("Rutina del cachorro") con `"date": null` y dos
  actividades añadidas a 4 y 6 minutos vista, pegada dentro de una respuesta típica de IA (texto antes y después y
  bloque ```` ```json ````).
- Tratamiento de 7 días (v1.1, T-37): una respuesta de IA con `"repeatDays": 7`, `"date": null` y tres tomas
  (06:00, 14:00 y 22:00), y una rutina de un día ("Paseos de Luna") con fecha dentro de esos 7 días.
- Para el reinicio a mitad: `adb shell settings put global auto_time 0` y `adb shell cmd alarm set-time <epoch ms>`;
  al terminar, `adb shell settings put global auto_time 1`.
- `adb shell dumpsys alarm | grep -A3 "com.mirkwood.rutinify}"` muestra las alarmas registradas (`origWhen`, `window=0` = exacta).

## Checklist

| # | Paso | Esperado | Resultado (2026-11-14, emulador Android 16) |
|---|---|---|---|
| 1 | Nueva rutina → "Pegar del portapapeles" con la respuesta de IA → "Validar" | Revisión con "Rutina del cachorro", aviso "No venía fecha; elegí sábado 14…" y las actividades pasadas desmarcadas | ✅ El extractor encontró el JSON dentro del texto; aviso de fecha mostrado; "4 alarmas, de 17:23 a 02:00 (+1 día)" |
| 2 | "Programar alarmas" | Detalle con "Pendientes: 4 de 8" y "4 alarmas programadas"; 4 alarmas exactas en `dumpsys alarm` | ✅ "Pendientes: 4 de 8", 4 alarmas con `window=0` |
| 3 | Esperar a la primera alarma (17:23) | Notificación a su hora exacta en el canal de su categoría ("Juego"), con "Hecho" y "+5 min" | ✅ Llegó a las 17:23:00 en `cat_juego`, con las dos acciones |
| 4 | Pulsar "Hecho" en la notificación | La notificación desaparece; en el detalle la alarma queda "Hecha" | ✅ |
| 5 | Segunda alarma (17:25) → "+5 min" | La notificación desaparece; la alarma vuelve a "Pendiente" y hay una alarma registrada 5 minutos después del toque | ✅ Tocado a las 17:25:30 → alarma exacta a las 17:30:30 |
| 6 | Reiniciar el emulador | Tras el arranque, las alarmas pendientes vuelven a estar en `dumpsys alarm` (`RescheduleReceiver`, `BOOT_COMPLETED`) | ✅ Las 10 pendientes de todas las órdenes, exactas |
| 7 | Cambiar la zona horaria (`cmd time_zone_detector` / Ajustes) | Las alarmas se recalculan: misma hora local en la nueva zona (R-12.2) | ✅ GMT → America/Bogota: las 22:30 siguen a las 22:30 locales; la pospuesta conserva su instante (12:30:30 -05 = 17:30:30 GMT, D-12) |
| 8 | Revocar "Alarmas y recordatorios" | Android cierra la app; al abrirla, banner "Faltan permisos…" y las pendientes registradas con ventana (R-11.7) | ✅ Android borró las 10 alarmas; al abrir, banner y alarmas con `window=+10m` |
| 9 | Eliminar una orden con alarmas disparadas y su notificación visible | La orden desaparece de Inicio y su notificación se quita (R-10.4) | ✅ Con "Agua fresca" disparada y visible: orden, notificación y alarmas eliminadas |
| 10 | Manifiesto final | Sin `android.permission.INTERNET` (R-13.6) | ✅ Solo `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` (y el permiso interno de receptores de AndroidX) |
| 11 | `./gradlew koverVerify` | ≥ 80 % de líneas en `domain` + `data.importer` (R-13.5) | ✅ 99,2 % (579 de 584 líneas) |

### Rutinas de varios días (v1.1, T-37)

| # | Paso | Esperado | Resultado (2026-11-15, emulador Android 16) |
|---|---|---|---|
| 12 | Programar "Paseos de Luna" para el miércoles 18 | Orden de un día con 2 alarmas | ✅ |
| 13 | Importar el tratamiento (`repeatDays` 7) y elegir el lunes 16 con "Cambiar fecha" | Revisión con "Día 1 · lunes 16 de noviembre" … "Día 7", horas sin "(+1 día)" y "21 alarmas en 7 días, del lunes 16 al domingo 22 de noviembre" (R-4.13–R-4.15) | ✅ Con la fecha que eligió la app (hoy a las 23:04) el resumen decía "18 alarmas en 6 días": el Día 1 ya había pasado entero; corregido en T-38 (ver nota) |
| 14 | "Programar alarmas" | Diálogo "Ya hay rutinas en esas fechas" con "Paseos de Luna (18 de noviembre)" y el aviso de reemplazo (R-6.6, R-6.7) | ✅ Tras corregir las comillas del aviso, que Android quitaba; "Mantener ambas" programa 21 alarmas exactas |
| 15 | Inicio y detalle | Inicio: "del lunes 16 al domingo 22 de noviembre de 2026"; detalle con el mismo rango y cabeceras por día (R-8.2, R-9.6) | ✅ |
| 16 | Adelantar el reloj al jueves 19 a las 12:00 y reiniciar | Tras el arranque quedan las 11 tomas futuras, exactas; la de las 06:00 del jueves y los paseos aparecen como *Omitidos*; el detalle baja hasta "Día 4" con la toma de las 14:00 como próxima | ✅ 11 alarmas con `window=0` |
| 17 | "Duplicar para otra fecha" → lunes 30 de noviembre | Revisión con "Día 1 · lunes 30 de noviembre", "Día 2 · martes 1 de diciembre" y "21 alarmas en 7 días, del lunes 30 de noviembre al domingo 6 de diciembre" (R-10.7) | ✅ |
| 18 | `./gradlew koverVerify` | ≥ 80 % (R-13.5) | ✅ 99,2 % (782 de 788 líneas) |

**Nota:** sin `date`, la app elegía hoy si quedaba alguna actividad futura en *toda* la rutina. En una rutina de
varios días importada por la noche eso daba un Día 1 ya pasado. Desde T-38 (D-61) solo cuentan las actividades del
Día 1, y el paso 13 debe elegir mañana sin tocar la fecha.

## Casos pendientes del roadmap (v1.2–v1.4)

Se ejecutan en las tareas de verificación (T-50, T-64). Requieren un **teléfono real con Android 14 o superior** (el
comportamiento de pantalla completa y de "No molestar" varía según el fabricante, K-9), las apps de ChatGPT y Gemini
instaladas y, para el QR, un segundo teléfono.

### Modo alarma real (v1.2, T-50)

| # | Paso | Esperado | Resultado |
|---|---|---|---|
| 19 | Marcar "medicación" como crítica en *Ajustes*; programar un plan con una toma de esa categoría a 2 minutos y bloquear el teléfono | A la hora, la pantalla se enciende con la pantalla de alarma ("Hecho", "+5 min", "Silenciar") y suena el tono de alarma repetido (R-15.6, R-15.7); en `dumpsys alarm` la alarma es de tipo alarm clock | Emulador API 36 (2026-11-17): pantalla de alarma con la pantalla apagada, tono de alarma repetido y alarma de tipo alarm clock. Falta teléfono real |
| 20 | Activar "No molestar" (dejando pasar alarmas, lo predeterminado) con una toma crítica y otra no crítica a la misma hora | La crítica suena; la no crítica no suena (R-15.12) | Emulador API 36 (2026-11-18): la crítica sonó con el tono de alarma; Android bloqueó la no crítica (`intercepted … !allowReminders`). Falta teléfono real |
| 21 | Pulsar "Silenciar" en la pantalla de alarma | Se corta el sonido; queda una notificación muda con "Hecho" y "+5 min"; la alarma sigue *Disparada* (R-15.9) | Emulador API 36 (2026-11-17): OK. Falta teléfono real |
| 22 | Dejar sonar una alarma crítica sin tocar nada | A los 10 minutos se silencia sola (R-15.10) | Emulador API 36 (2026-11-17): a los 10 min pasó sola al canal mudo. Falta teléfono real |
| 23 | Con una alarma crítica pendiente, reiniciar el teléfono | Tras desbloquear, sigue registrada como alarm clock y suena a su hora (R-15.13) | Emulador API 36 (2026-11-18): tras `adb reboot` seguía como próxima alarma y sonó a su hora. Falta teléfono real |
| 24 | Quitar el permiso de pantalla completa en Ajustes del sistema y repetir el paso 19 | Suena como notificación emergente con los mismos botones; *Permisos* muestra "Alarmas a pantalla completa: desactivado" (R-15.11) | Emulador API 36 (2026-11-18): sonó como notificación emergente con sus tres botones; *Permisos* mostró "Desactivado". Falta teléfono real |

### Entrada compartida (v1.2, T-50)

| # | Paso | Esperado | Resultado |
|---|---|---|---|
| 25 | En ChatGPT, pedir la rutina con la plantilla de "Cachorro" y compartir la respuesta → Rutinify | Se abre *Revisión* directamente con el plan (R-14.2, R-14.3) | Pendiente: necesita teléfono real con ChatGPT |
| 26 | Lo mismo desde Gemini | Igual que el paso 25; si Gemini comparte solo un enlace o texto recortado, anotarlo (K-12) | Pendiente: necesita teléfono real con Gemini |
| 27 | En *Nueva rutina*, elegir "Medicación" y pulsar "Abrir en IA" → ChatGPT | ChatGPT abre con la plantilla de medicación completa en la caja de mensaje (R-14.7) | Pendiente: el chooser envía la plantilla completa (emulador, T-41); falta comprobarlo con ChatGPT en un teléfono real |
| 28 | Compartir a Rutinify un texto sin JSON | *Nueva rutina* con el texto en el campo y el error de R-3.3 | Emulador API 36 (2026-11-16, T-40): OK |

### Compartir entre dos teléfonos (v1.4, T-64)

| # | Paso | Esperado | Resultado |
|---|---|---|---|
| 29 | Teléfono A: "Compartir plan" → "Mostrar QR" en un plan de 7 días con dos responsables. Teléfono B: *Nueva rutina* → "Escanear QR" | B abre *Revisión* con las mismas actividades, días y responsables (R-19.4, R-19.7) | Pendiente: necesita dos teléfonos reales. En el emulador, el QR generado se lee con el lector de ZXing (test) y un enlace `rutinify://import` abre *Revisión* con el plan |
| 30 | En A, un plan con 200 actividades → "Mostrar QR" | "Este plan es demasiado grande para un QR; compártelo como texto" y el botón funciona (R-19.5) | Emulador API 36 (2026-11-18): un plan de 200 actividades mostró el aviso y "Compartir como texto" abrió el selector con el JSON. Falta en teléfono real |
| 31 | En B, elegir "Solo las tareas de…" con uno de los responsables y programar | El plan de B solo tiene las tareas de ese responsable y las sin responsable (R-19.9) | Emulador API 36 (2026-11-18), con el plan recibido como texto en un solo teléfono: con "Ana" elegida, el plan quedó con sus dos tareas y la que no tiene responsable (3 de 3). Falta con dos teléfonos |

### Reloj Wear OS (v2.0, T-70)

| # | Paso | Esperado | Resultado |
|---|---|---|---|
| 32 | Con un reloj Wear OS emparejado (app Pixel Watch o Wear OS en el teléfono), programar un plan con una actividad dentro de 2 minutos | La alarma aparece en el reloj, que vibra, con los botones "Hecho" y "+5 min" (R-24.1) | Pendiente: no hay reloj ni imagen de Wear OS en este equipo. Emulador API 36 (2026-11-19): la notificación del teléfono no lleva `LOCAL_ONLY` y sus acciones se ven igual que antes, solo con texto |
| 33 | Pulsar "Hecho" en el reloj; repetir con otra alarma y "+5 min" | En el teléfono la alarma queda *Hecha* o se pospone 5 minutos, y la notificación desaparece de los dos (R-24.2) | Pendiente: necesita reloj |
| 34 | Con una categoría crítica, dejar que suene y pulsar "Silenciar" en el reloj | El teléfono deja de sonar y la notificación sigue con "Hecho" y "+5 min" (R-24.3) | Pendiente: necesita reloj |

### Planes recurrentes (v2.0, T-68, T-69)

| # | Paso | Esperado | Resultado |
|---|---|---|---|
| 35 | Compartir un JSON v2 con `recurrence` (todos los días, dos actividades) y programar | *Revisión* muestra "Se repite todos los días, desde…" y "14 alarmas por semana"; se crean solo las alarmas de los próximos 7 días (R-23.7, R-23.14) | Emulador API 36 (2026-11-18): "14 alarmas por semana"; 13 pendientes (la de las 07:30 de hoy ya había pasado) |
| 36 | Reiniciar la app (o el teléfono) dos veces | No se duplican alarmas (R-23.7) | Emulador API 36 (2026-11-18): siguieron 13 tras dos reinicios |
| 37 | Con planes de un día, de varios días y uno recurrente diario ya guardados, compartir un v2 "Gimnasio" que se repite los jueves desde hoy (jueves) y pulsar "Programar alarmas" | El aviso de conflicto lista los tres; el recurrente con su recurrencia, los demás con sus fechas (R-23.9, R-23.10) | Emulador API 36 (2026-11-19): "Coinciden con: Amoxicilina de Luna (del 16 al 22 de noviembre), Paseos diarios (Se repite todos los días, desde el 19 de noviembre), Reloj (19 de noviembre)" |
| 38 | Un plan recurrente cuya ventana superaría 500 alarmas pendientes | Mensaje de R-5.10 y nada programado (R-23.8) | Cubierto por tests de `ScheduleOrderUseCase` (T-69); no reproducido a mano por el volumen |
| 39 | Pausar un plan recurrente un día y reanudarlo | No suena nada de los días en pausa y al reanudar se crean las de la ventana desde ese momento (R-23.12) | Cubierto por tests (T-68); pendiente en teléfono real a lo largo de varios días |
