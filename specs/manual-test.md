# Prueba manual — Rutinify

Checklist de T-32 (R-13.5, R-13.6). Se repite antes de cada versión, en un dispositivo real o en el emulador
`Pixel_9_Pro_API_36` (Android 16), con la app instalada desde `assembleDebug`.

Cada paso dice qué hacer y qué se espera. La columna *Resultado* recoge la última ejecución.

## Preparación

- Rutina de prueba: `app/src/test/resources/fixtures/valid_v1.json` ("Rutina del cachorro") con `"date": null` y dos
  actividades añadidas a 4 y 6 minutos vista, pegada dentro de una respuesta típica de IA (texto antes y después y
  bloque ```` ```json ````).
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
