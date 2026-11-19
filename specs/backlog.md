# Backlog — Rutinify

Lo que queda abierto tras completar la Fase 14 (v2.0) y las revisiones del 2026-11-19 con las skills
*android-design*, *adaptive* y *jetpack-compose-performance*. Son candidatos para próximas versiones o iteraciones: aquí
no hay requisitos (`R-x.y`) ni tareas (`T-xx`). Cuando se elija uno, se le da requisito y tarea en `requirements.md` y
`tasks.md` antes de escribir código (CLAUDE.md §2).

**Columnas.** *Necesita*: lo que hay que decidir o conseguir antes de empezar ("—" si nada); toda dependencia nueva pasa
por la aprobación de CLAUDE.md §9. *Prioridad*: **Alta** (bloquea publicar o afecta a quien usa la app hoy), **Media**
(mejora clara), **Baja** (pulido).

## 1. Verificación en dispositivos reales

Tareas ya definidas que no se pueden cerrar desde el repositorio ni desde el emulador.

| Pendiente | Origen | Necesita | Prioridad |
|---|---|---|---|
| Repetir en teléfono real los pasos 19–27 (alarmas críticas, "No molestar", reinicio, compartir con ChatGPT y Gemini instalados) | T-50 | Teléfono real con las apps de IA | Alta |
| QR entre dos teléfonos y "Solo las tareas de…" con dos personas (pasos 29–31) | T-64 | Dos teléfonos reales | Alta |
| "Hecho", "+5 min" y "Silenciar" desde un reloj emparejado (pasos 32–34) | T-70, D-96 | Reloj Wear OS o imagen de Wear OS y app complementaria con cuenta de Google | Media |
| Conversión con Gemini Nano en un teléfono compatible | T-66, D-93 | Teléfono con AICore y Gemini Nano | Media |
| Plan recurrente pausado varios días y reanudado (paso 39) | T-68, R-23.12 | Teléfono real durante varios días | Media |

## 2. Publicación

| Pendiente | Origen | Necesita | Prioridad |
|---|---|---|---|
| Decisiones de la lista de publicación: R8, correo público, URL de la política de privacidad | T-53, `release-checklist.md` | Decisión del propietario | Alta |
| Prueba cerrada en Google Play (12+ testers, 14 días) | T-54 | Play Console y testers | Alta |
| Activar R8 en release y revisar reglas de Hilt, Room, kotlinx.serialization y ML Kit | Revisión de rendimiento; T-53 | Decisión de R8 | Alta |
| Capturas del README y de la ficha de Play actualizadas (las del README son de v1.1) | Revisión de diseño | — | Media |

## 3. Diseño (revisión *android-design*)

| Pendiente | Origen | Necesita | Prioridad |
|---|---|---|---|
| Explorar antes de implementar: propuestas en vivo con *android-design-explore* y elegir dirección visual | Revisión de diseño | — | Media |
| Material 3 Expressive: `MaterialExpressiveTheme`, `MotionScheme.expressive()` y componentes Expressive | Revisión de diseño | `material3` 1.5.0-alpha (dependencia alpha) | Media |
| Color de marca en lugar del morado de la plantilla, que ven los teléfonos con Android 9–11 (sin color dinámico) | Revisión de diseño | Elegir color semilla | Media |
| Tipografía: Google Sans Flex para el texto y Google Sans Code para horas y números | Revisión de diseño | Incluir los archivos de la fuente (licencia OFL, ~cientos de KB) | Baja |
| Pantalla de alarma como momento estrella: hora muy grande, "Hecho" XL, animación que respete reducir movimiento | Revisión de diseño | Material 3 Expressive | Media |
| Detalle del plan: "Pausar" y "Voy tarde" como grupo de botones conectados | Revisión de diseño | Material 3 Expressive | Baja |
| Detalle y *Revisión*: dejar "Hecho" como única acción visible por fila; editar y borrar al tocar la fila, en un menú o deslizando | Revisión de diseño | Decidir el gesto (afecta a R-9 y R-4) | Media |
| Detalle: la cabecera repite el recuento ("Pendientes: 13 de 13" y "0 de 13 hechas"); dejar uno | Revisión de diseño | — | Baja |
| *Nueva rutina*: pasos numerados (1 plantilla → 2 IA → 3 JSON) y agrupar "Ver plantilla" y "Escanear QR", hoy botones de texto sueltos | Revisión de diseño | — | Media |
| *Ajustes*: "Exportar diagnóstico" tonal y "Borrar diagnóstico" de texto, en lugar de dos botones de texto sueltos | Revisión de diseño | — | Baja |
| Barra superior flexible que se pliega al hacer scroll | Revisión de diseño | Material 3 Expressive | Baja |
| Inicio: "Hecho" en la tarjeta de próxima alarma sin opción de deshacer; valorar un snackbar "Deshacer" como en el detalle | Reorganización D-99 | — | Media |
| Conflicto con un plan recurrente: la recurrencia aparece con mayúscula dentro del paréntesis ("Gimnasio (Se repite…)") | T-69 | Texto en minúscula para ese uso | Baja |

## 4. Pantallas grandes y plegables (revisión *adaptive*)

| Pendiente | Origen | Necesita | Prioridad |
|---|---|---|---|
| Migrar de Navigation Compose 2 a Navigation 3 | Revisión adaptive (requisito de la skill) | Dependencia `navigation3` | Media |
| Lista-detalle en tablet: Inicio y el detalle del plan lado a lado (`ListDetailSceneStrategy`) | Revisión adaptive | Navigation 3 y `adaptive-navigation3` | Media |
| Pruebas de capturas por tamaño (teléfono, plegable, tablet, escritorio) | Revisión adaptive | Plugin Compose Preview Screenshot Testing | Media |
| Revisar en tablet la pantalla de alarma y la barra inferior de *Revisión*, que siguen a todo el ancho | Revisión adaptive; D-100 | — | Baja |
| *Tooltips* en los iconos y atajos de teclado para uso con ratón y teclado | Revisión adaptive | — | Baja |
| El menú ⋮ del detalle se cierra al girar (`remember`); pasar a `rememberSaveable` | Revisión adaptive | — | Baja |

## 5. Rendimiento (revisión *jetpack-compose-performance*)

| Pendiente | Origen | Necesita | Prioridad |
|---|---|---|---|
| Baseline Profiles para el arranque en frío y el scroll de Inicio, detalle y *Revisión* | Revisión de rendimiento | Módulo de macrobenchmark y plugin `baselineprofile` | Media |
| Medir con un build de release (nunca con debug) arranque, scroll de un plan de 200 alarmas y *Revisión* | Revisión de rendimiento | R8 activado | Media |
| QR: dibujar los módulos con un `Path` cacheado (`drawWithCache`) en lugar de un rectángulo por módulo en cada repintado | Revisión de rendimiento | — | Baja |
| *Ajustes* usa una lista *lazy* para menos de 10 categorías; pasar a `Column` solo si una medición lo justifica | Revisión de rendimiento | Benchmark | Baja |

## 6. Seguridad y funciones

| Pendiente | Origen | Necesita | Prioridad |
|---|---|---|---|
| Repaso diario en un receiver no exportado, para que otra app no pueda forzar un reprogramado | D-97 | — | Baja |
| Ofrecer descargar Gemini Nano cuando el estado es `DOWNLOADABLE` | D-80 | Probar con un teléfono compatible | Baja |
| Reglas de repetición más ricas (cada dos días, mensual) | Límites del README, R-23 | Cambio de contrato (schema 3) | Baja |
| App propia de Wear OS (hoy solo notificaciones puenteadas) | D-82, design §15 | Módulo `:wear` y Data Layer | Baja |
