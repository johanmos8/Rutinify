# Requisitos — Rutinify (MVP)

> Formato EARS: *WHEN* (evento) · *WHILE* (estado) · *IF … THEN* (comportamiento no deseado) · *WHERE* (opcional) · ubicuo (*THE SYSTEM SHALL*).
> Cada criterio tiene un id `R-x.y` que se referencia en `tasks.md` y en los tests.

## Glosario

| Término | Significado |
|---|---|
| **Plantilla** | Texto de prompt que el usuario copia y pega en una IA externa. |
| **JSON de rutina** | Respuesta de la IA que cumple el contrato v1 (`design.md` §4). |
| **Borrador** | Rutina validada pero aún no programada (vive en la pantalla de Revisión). |
| **Orden** | Rutina importada y confirmada; agrupa sus alarmas. Estados: Activa, Pausada, Cancelada (y "Terminada" derivada). |
| **Alarma** | Una actividad con fecha y hora concretas. Estados: Pendiente, Disparada, Hecha, Omitida, Cancelada. |
| **Hora de disparo** | `routine.date + dayOffset` a la hora `time`, en la zona horaria actual del teléfono. |

---

## R-1 Plantilla de prompt

**Historia:** Como usuario, quiero copiar una plantilla de prompt para que una IA convierta mi rutina en texto libre en el JSON que la app entiende.

- **R-1.1** THE SYSTEM SHALL mostrar en la pantalla *Nueva rutina* un botón "Copiar plantilla".
- **R-1.2** WHEN el usuario pulsa "Copiar plantilla", THE SYSTEM SHALL copiar al portapapeles el texto completo de la plantilla (instrucciones + esquema de ejemplo v1 + marcador `[PEGA AQUÍ TU RUTINA]`) y mostrar la confirmación "Plantilla copiada".
- **R-1.3** THE SYSTEM SHALL cargar la plantilla desde un recurso de la app (`res/raw`), no desde texto escrito en la UI.
- **R-1.4** THE SYSTEM SHALL permitir ver la plantilla completa antes de copiarla (sección desplegable).

## R-2 Entrada del JSON

**Historia:** Como usuario, quiero pegar el JSON que me dio la IA para convertirlo en alarmas.

- **R-2.1** THE SYSTEM SHALL ofrecer un campo de texto multilínea para pegar el JSON.
- **R-2.2** WHEN el usuario pulsa "Pegar del portapapeles", THE SYSTEM SHALL reemplazar el contenido del campo por el texto del portapapeles.
- **R-2.3** IF el portapapeles está vacío o no contiene texto, THEN THE SYSTEM SHALL mostrar "El portapapeles está vacío" y no modificar el campo.
- **R-2.4** WHILE el campo está vacío, THE SYSTEM SHALL mantener deshabilitado el botón "Validar".
- **R-2.5** THE SYSTEM SHALL conservar el texto del campo si la app pasa a segundo plano o el sistema destruye el proceso.

## R-3 Extracción tolerante y validación

**Historia:** Como usuario no técnico, quiero que la app entienda el JSON aunque la IA lo envuelva en texto, y que me explique con claridad qué está mal si no lo entiende.

- **R-3.1** WHEN el usuario pulsa "Validar", THE SYSTEM SHALL extraer el primer objeto JSON válido del texto, aceptando bloques de código Markdown (```` ```json … ``` ```` o ```` ``` … ``` ````) y texto antes o después del objeto.
- **R-3.2** THE SYSTEM SHALL ignorar los campos desconocidos en cualquier nivel del JSON.
- **R-3.3** IF el texto no contiene ningún objeto JSON que se pueda leer, THEN THE SYSTEM SHALL mostrar "No encontré un JSON válido en el texto. Asegúrate de pegar la respuesta completa de la IA."
- **R-3.4** IF falta `schemaVersion` o su valor no es `1`, THEN THE SYSTEM SHALL mostrar "Versión de formato no soportada (se esperaba 1)" e indicar el valor recibido.
- **R-3.5** IF `routine.name` falta o está vacío, THEN THE SYSTEM SHALL mostrar "La rutina no tiene nombre (campo routine.name)".
- **R-3.6** IF `routine.date` está presente y no tiene formato `yyyy-MM-dd` o no es una fecha real, THEN THE SYSTEM SHALL mostrar "La fecha «{valor}» no es válida; usa el formato 2026-11-05".
- **R-3.7** IF `items` falta, no es una lista o está vacía, THEN THE SYSTEM SHALL mostrar "La rutina no tiene actividades (campo items)".
- **R-3.8** IF un item no tiene `time` o este no corresponde a una hora real de 24 h (`H:mm` o `HH:mm`, 00:00–23:59), THEN THE SYSTEM SHALL mostrar "Actividad {n} («{title}»): la hora «{valor}» no es válida; usa el formato 24 h, por ejemplo 06:45".
- **R-3.9** IF un item no tiene `title` o está vacío, THEN THE SYSTEM SHALL mostrar "Actividad {n} (a las {time}): falta el título".
- **R-3.10** IF un `title` supera 80 caracteres, THEN THE SYSTEM SHALL recortarlo a 80 y mostrar un **aviso** no bloqueante que lo indique.
- **R-3.11** IF `dayOffset` está presente y no es un entero entre 0 y 1, THEN THE SYSTEM SHALL mostrar "Actividad {n} («{title}»): dayOffset debe ser 0 (mismo día) o 1 (día siguiente)".
- **R-3.12** IF `durationMin` está presente y no es un entero entre 1 y 1440, THEN THE SYSTEM SHALL mostrar un error que indique la actividad y el campo.
- **R-3.13** IF un campo tiene un tipo incorrecto (por ejemplo, `time` numérico o `title` como lista), THEN THE SYSTEM SHALL indicar la actividad, el campo y el tipo esperado en lenguaje no técnico.
- **R-3.14** WHERE `category` falta o está vacía, THE SYSTEM SHALL asignar `"general"`; en otro caso THE SYSTEM SHALL normalizarla (minúsculas, sin espacios al inicio ni al final, espacios internos convertidos a `-`).
- **R-3.15** WHERE `dayOffset` falta o es `null`, THE SYSTEM SHALL usar `0`.
- **R-3.16** THE SYSTEM SHALL mostrar **todos** los errores encontrados en una sola validación (hasta 20, con "y {k} errores más"), no solo el primero.
- **R-3.17** THE SYSTEM SHALL numerar las actividades desde 1 en los mensajes, según su posición en el JSON.
- **R-3.18** IF el JSON contiene más de 200 actividades, THEN THE SYSTEM SHALL rechazarlo con "Demasiadas actividades ({n}); el máximo es 200".
- **R-3.19** WHEN la validación termina sin errores, THE SYSTEM SHALL abrir la pantalla de *Revisión* con el borrador y los avisos.

## R-4 Revisión del borrador

**Historia:** Como usuario, quiero revisar y ajustar las actividades antes de programarlas.

- **R-4.1** THE SYSTEM SHALL mostrar las actividades ordenadas por hora de disparo (`dayOffset`, luego `time`), cada una con hora, título, categoría y descripción (si existe).
- **R-4.2** THE SYSTEM SHALL permitir editar la hora, el título y la categoría de cada actividad, aplicando las mismas reglas de R-3.8, R-3.9, R-3.10 y R-3.14.
- **R-4.3** THE SYSTEM SHALL permitir quitar una actividad del borrador (con opción de deshacer).
- **R-4.4** WHERE `routine.date` venía en el JSON, THE SYSTEM SHALL usarla como fecha preseleccionada.
- **R-4.5** WHERE `routine.date` no venía en el JSON, THE SYSTEM SHALL preseleccionar **hoy** si al menos una actividad tiene hora de disparo futura y, si no, **mañana**, e informar "No venía fecha; elegí {fecha}".
- **R-4.6** THE SYSTEM SHALL permitir cambiar la fecha con un selector de fecha y recalcular de inmediato las horas de disparo y las actividades pasadas.
- **R-4.7** WHILE la hora de disparo de una actividad es anterior o igual al momento actual, THE SYSTEM SHALL mostrarla como "Ya pasó", atenuada y excluida por defecto.
- **R-4.8** THE SYSTEM SHALL permitir excluir manualmente cualquier actividad futura (casilla) sin quitarla de la lista.
- **R-4.9** THE SYSTEM SHALL mostrar un resumen con el número de alarmas a programar y el rango horario, p. ej. "28 alarmas, de 06:45 a 02:00 (+1 día)".
- **R-4.10** IF no queda ninguna actividad futura incluida, THEN THE SYSTEM SHALL deshabilitar "Programar alarmas" y mostrar "No hay actividades futuras para programar".
- **R-4.11** IF la fecha elegida está en el pasado y todas las actividades ya pasaron, THEN THE SYSTEM SHALL sugerir cambiar la fecha a mañana.
- **R-4.12** THE SYSTEM SHALL mostrar las notas de la rutina (`routine.notes`) si existen.

## R-5 Programación de alarmas

**Historia:** Como usuario, quiero que al confirmar se programen todas las alarmas para que suenen a su hora aunque el teléfono esté en reposo.

- **R-5.1** WHEN el usuario pulsa "Programar alarmas", THE SYSTEM SHALL guardar la orden y sus alarmas incluidas en almacenamiento local y luego programar una alarma del sistema por cada actividad incluida.
- **R-5.2** THE SYSTEM SHALL calcular la hora de disparo como `fecha + dayOffset` a la hora `time`, en la zona horaria actual del teléfono.
- **R-5.3** WHILE la app tiene permiso de alarmas exactas, THE SYSTEM SHALL programar cada alarma como exacta permitida en reposo (*exact & allow while idle*).
- **R-5.4** IF la app no tiene permiso de alarmas exactas, THEN THE SYSTEM SHALL programar cada alarma con una ventana de hasta 10 minutos y mostrar en la orden el aviso "Las alarmas pueden retrasarse unos minutos (permiso de alarmas exactas desactivado)".
- **R-5.5** THE SYSTEM SHALL guardar las actividades excluidas o pasadas con estado *Omitida* para que figuren en el detalle de la orden.
- **R-5.6** WHEN la programación termina, THE SYSTEM SHALL abrir el detalle de la orden creada y mostrar "{n} alarmas programadas".
- **R-5.7** IF falla la escritura en almacenamiento local, THEN THE SYSTEM SHALL no programar ninguna alarma del sistema y mostrar un error con opción de reintentar.
- **R-5.8** IF la hora de una actividad no existe en la zona horaria (salto por horario de verano), THEN THE SYSTEM SHALL programarla desplazada la duración del salto (p. ej. 02:30 → 03:30); IF la hora es ambigua (retraso de hora), THEN THE SYSTEM SHALL usar la primera ocurrencia.
- **R-5.9** THE SYSTEM SHALL crear, antes de programar, el canal de notificación de cada categoría nueva de la orden.

## R-6 Importación duplicada / reemplazo

**Historia:** Como usuario que importa la rutina del día cada mañana, quiero decidir qué pasa si ya hay una orden para esa fecha.

- **R-6.1** WHEN el usuario pulsa "Programar alarmas" AND existe al menos una orden *Activa* o *Pausada* con la misma fecha, THE SYSTEM SHALL mostrar un diálogo con las opciones "Reemplazar la anterior", "Mantener ambas" y "Cancelar".
- **R-6.2** WHEN el usuario elige "Reemplazar la anterior", THE SYSTEM SHALL cancelar todas las alarmas pendientes de las órdenes en conflicto, marcarlas *Canceladas*, poner esas órdenes en estado *Cancelada* y luego programar la nueva orden.
- **R-6.3** WHEN el usuario elige "Mantener ambas", THE SYSTEM SHALL programar la nueva orden sin modificar las existentes.
- **R-6.4** WHEN el usuario elige "Cancelar", THE SYSTEM SHALL volver a la revisión sin cambios.
- **R-6.5** IF hay varias órdenes en conflicto, THEN THE SYSTEM SHALL listarlas por nombre en el diálogo, y "Reemplazar" SHALL aplicarse a todas.

## R-7 Notificación de la alarma

**Historia:** Como usuario, quiero un aviso claro a la hora de cada actividad, y poder marcarla como hecha o posponerla.

- **R-7.1** WHEN llega la hora de disparo de una alarma *Pendiente* de una orden *Activa*, THE SYSTEM SHALL mostrar una notificación con título = `title`, texto = `description` (o la hora si no hay descripción) y subtítulo = nombre de la orden, y marcar la alarma como *Disparada*.
- **R-7.2** THE SYSTEM SHALL publicar la notificación en el canal de la categoría de la alarma.
- **R-7.3** THE SYSTEM SHALL incluir las acciones "Hecho" y "+5 min".
- **R-7.4** WHEN el usuario pulsa "Hecho", THE SYSTEM SHALL marcar la alarma como *Hecha* y quitar la notificación.
- **R-7.5** WHEN el usuario pulsa "+5 min", THE SYSTEM SHALL quitar la notificación, volver a poner la alarma en *Pendiente* con hora de disparo = ahora + 5 min y programarla de nuevo.
- **R-7.6** WHEN el usuario toca el cuerpo de la notificación, THE SYSTEM SHALL abrir el detalle de la orden con esa alarma resaltada.
- **R-7.7** IF al dispararse la alarma ya no existe, ya no está *Pendiente* o su orden no está *Activa*, THEN THE SYSTEM SHALL no mostrar notificación.
- **R-7.8** IF el permiso de notificaciones está denegado, THEN THE SYSTEM SHALL marcar la alarma como *Disparada* igualmente (el aviso de permisos se muestra en la app según R-11).

## R-8 Inicio (lista de órdenes)

**Historia:** Como usuario, quiero ver mis rutinas importadas y su estado de un vistazo.

- **R-8.1** THE SYSTEM SHALL listar las órdenes ordenadas por fecha descendente y, a igual fecha, por fecha de creación descendente.
- **R-8.2** THE SYSTEM SHALL mostrar por orden: nombre, fecha, número de alarmas pendientes / total, y estado (Activa, Pausada, Cancelada o Terminada).
- **R-8.3** THE SYSTEM SHALL considerar *Terminada* una orden Activa sin alarmas *Pendientes*.
- **R-8.4** THE SYSTEM SHALL mostrar un botón flotante "Nueva rutina".
- **R-8.5** WHILE no hay órdenes, THE SYSTEM SHALL mostrar un estado vacío que explique el flujo en 3 pasos (copiar plantilla → pedir JSON a la IA → pegarlo).
- **R-8.6** THE SYSTEM SHALL actualizar la lista automáticamente cuando cambie el estado de cualquier alarma u orden.

## R-9 Detalle de orden

**Historia:** Como usuario, quiero ver la línea de tiempo de una rutina y gestionar sus alarmas.

- **R-9.1** THE SYSTEM SHALL mostrar las alarmas de la orden en una línea de tiempo ordenada por hora de disparo, con su estado: Pendiente, Disparada, Hecha, Omitida o Cancelada.
- **R-9.2** THE SYSTEM SHALL destacar la próxima alarma pendiente.
- **R-9.3** WHEN el usuario elimina una alarma individual, THE SYSTEM SHALL cancelarla en el sistema (si estaba pendiente), quitar su notificación si está visible, borrarla del almacenamiento y ofrecer "Deshacer" durante unos segundos.
- **R-9.4** WHEN el usuario pulsa "Deshacer" tras eliminar una alarma, THE SYSTEM SHALL restaurarla y, si su hora sigue siendo futura, volver a programarla.
- **R-9.5** THE SYSTEM SHALL permitir marcar manualmente como *Hecha* una alarma *Pendiente* o *Disparada*.

## R-10 Gestión de la orden completa

**Historia:** Como usuario, quiero eliminar, pausar o duplicar una rutina entera con un toque.

- **R-10.1** WHEN el usuario pulsa "Eliminar todas las alarmas", THE SYSTEM SHALL pedir confirmación indicando cuántas alarmas pendientes se cancelarán.
- **R-10.2** WHEN el usuario confirma "Eliminar todas las alarmas", THE SYSTEM SHALL cancelar en el sistema todas las alarmas *Pendientes* de la orden, quitar sus notificaciones visibles, marcarlas *Canceladas* y poner la orden en estado *Cancelada*, conservando el historial (Hechas, Disparadas, Omitidas).
- **R-10.3** IF la orden tiene alarmas ya *Disparadas* o *Hechas*, THEN THE SYSTEM SHALL conservarlas con su estado al eliminar todas las alarmas y quitar de la bandeja las notificaciones aún visibles de esa orden.
- **R-10.4** WHEN el usuario pulsa "Eliminar orden" (menú) y confirma, THE SYSTEM SHALL cancelar sus alarmas pendientes, quitar sus notificaciones y borrar la orden y todas sus alarmas del almacenamiento.
- **R-10.5** WHEN el usuario pausa una orden *Activa*, THE SYSTEM SHALL cancelar en el sistema sus alarmas *Pendientes* (manteniéndolas *Pendientes* en almacenamiento) y poner la orden en *Pausada*.
- **R-10.6** WHEN el usuario reanuda una orden *Pausada*, THE SYSTEM SHALL marcar como *Omitidas* las alarmas pendientes cuya hora ya pasó, programar las futuras y poner la orden en *Activa*.
- **R-10.7** WHEN el usuario pulsa "Duplicar para otra fecha" y elige una fecha, THE SYSTEM SHALL crear un borrador con las mismas actividades (sin estados) y abrir la *Revisión* con esa fecha.
- **R-10.8** WHILE una orden está *Cancelada*, THE SYSTEM SHALL ocultar las acciones Pausar/Reanudar/Eliminar todas las alarmas y mantener Duplicar y Eliminar orden.

## R-11 Permisos

**Historia:** Como usuario, quiero entender qué permisos necesita la app y qué pasa si no los doy.

- **R-11.1** WHEN el usuario va a programar alarmas por primera vez en Android 13+ y no se ha concedido `POST_NOTIFICATIONS`, THE SYSTEM SHALL solicitar el permiso con una explicación previa.
- **R-11.2** WHEN el usuario va a programar alarmas y `canScheduleExactAlarms()` es falso, THE SYSTEM SHALL explicar por qué hace falta y ofrecer abrir el ajuste del sistema (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`) o continuar sin alarmas exactas.
- **R-11.3** WHILE falta algún permiso, THE SYSTEM SHALL mostrar en Inicio un banner persistente que lleve a la pantalla *Permisos*.
- **R-11.4** THE SYSTEM SHALL ofrecer una pantalla *Permisos* con el estado de cada permiso (notificaciones, alarmas exactas) y un botón para corregirlo.
- **R-11.5** WHEN la app vuelve a primer plano, THE SYSTEM SHALL volver a comprobar los permisos y actualizar banners y pantalla.
- **R-11.6** WHEN se concede el permiso de alarmas exactas, THE SYSTEM SHALL reprogramar como exactas todas las alarmas pendientes de órdenes activas.
- **R-11.7** IF se revoca el permiso de alarmas exactas (el sistema cancela las alarmas exactas), THEN THE SYSTEM SHALL, en cuanto la app se abra o reciba un evento de reprogramación, reprogramar las pendientes en modo ventana.
- **R-11.8** IF el usuario denegó permanentemente las notificaciones, THEN THE SYSTEM SHALL ofrecer abrir los ajustes de notificaciones de la app.

## R-12 Robustez: reinicio, hora y zona horaria

**Historia:** Como usuario, quiero que las alarmas sigan funcionando si reinicio el teléfono o cambia la hora.

- **R-12.1** WHEN el teléfono termina de arrancar, THE SYSTEM SHALL reprogramar todas las alarmas *Pendientes* de órdenes *Activas*.
- **R-12.2** WHEN cambia la hora del sistema o la zona horaria, THE SYSTEM SHALL recalcular las horas de disparo a partir de fecha + `dayOffset` + `time` en la zona actual (hora de pared) y reprogramarlas.
- **R-12.3** WHEN la app se actualiza (`MY_PACKAGE_REPLACED`) o se abre, THE SYSTEM SHALL reconciliar: reprogramar las alarmas pendientes futuras de forma idempotente.
- **R-12.4** IF al reprogramar una alarma *Pendiente* su hora ya pasó hace 15 minutos o menos, THEN THE SYSTEM SHALL dispararla de inmediato.
- **R-12.5** IF al reprogramar una alarma *Pendiente* su hora ya pasó hace más de 15 minutos, THEN THE SYSTEM SHALL marcarla como *Omitida* sin notificar.
- **R-12.6** THE SYSTEM SHALL garantizar que reprogramar varias veces seguidas no crea alarmas duplicadas.
- **R-12.7** IF una alarma pospuesta (+5 min) estaba pendiente durante un reinicio, THEN THE SYSTEM SHALL reprogramarla con su nueva hora pospuesta.

## R-13 No funcionales

- **R-13.1** THE SYSTEM SHALL funcionar sin conexión a Internet y sin cuenta de usuario.
- **R-13.2** THE SYSTEM SHALL validar un JSON de 200 actividades en menos de 300 ms en un dispositivo de gama media.
- **R-13.3** THE SYSTEM SHALL mostrar todos los textos en español desde recursos.
- **R-13.4** THE SYSTEM SHALL soportar tema claro y oscuro.
- **R-13.5** THE SYSTEM SHALL tener cobertura de tests ≥ 80 % en dominio e importador.
- **R-13.6** THE SYSTEM SHALL no solicitar el permiso `INTERNET`.

---

## Fuera de alcance (v2 — el diseño lo deja preparado)

Alarmas relativas a "me desperté" · rutinas recurrentes · widget "lo siguiente" · llamada directa a la API de Claude · exportar a `.ics` · compartir rutina entre dispositivos.
