# Requisitos — Rutinify (MVP, v1.1 y roadmap v1.2–v2.0)

> Formato EARS: *WHEN* (evento) · *WHILE* (estado) · *IF … THEN* (comportamiento no deseado) · *WHERE* (opcional) · ubicuo (*THE SYSTEM SHALL*).
> Cada criterio tiene un id `R-x.y` que se referencia en `tasks.md` y en los tests.

## Glosario

| Término | Significado |
|---|---|
| **Plantilla** | Texto de prompt que el usuario copia y pega en una IA externa. |
| **JSON de rutina** | Respuesta de la IA que cumple el contrato v1 (`design.md` §4). |
| **Borrador** | Rutina validada pero aún no programada (vive en la pantalla de Revisión). |
| **Orden** | Rutina importada y confirmada; agrupa sus alarmas. Estados: Activa, Pausada, Cancelada (y "Terminada" derivada). En el código es `RoutineOrder`; en la interfaz se llama **Plan** desde v1.2 (R-13.7). |
| **Plan** *(v1.2)* | Nombre de la orden en la interfaz ("Eliminar plan", "Plan no encontrado"). Los requisitos anteriores a v1.2 dicen "orden"; significan lo mismo. |
| **Rango de la orden** | Del día `routine.date` al día `routine.date + mayor dayOffset` de sus actividades. Una rutina de un día (con o sin madrugada) tiene `dayOffset` 0 o 1; un tratamiento de 7 días llega hasta `dayOffset` 6. |
| **Rutina de varios días** | Rutina cuyo mayor `dayOffset` es 2 o más, ya sea porque la IA escribió cada día o porque se expandió `repeatDays` (v1.1). |
| **`repeatDays`** | Campo opcional de `routine` (v1.1): repite las mismas actividades durante N días seguidos a partir de `routine.date`. |
| **Alarma** | Una actividad con fecha y hora concretas. Estados: Pendiente, Disparada, Hecha, Omitida, Cancelada. |
| **Hora de disparo** | `routine.date + dayOffset` a la hora `time`, en la zona horaria actual del teléfono. |
| **Categoría crítica** *(v1.2)* | Categoría que el usuario marca para que sus alarmas suenen como un despertador (R-15). La marca vale para todos los planes. |
| **Alarma crítica** *(v1.2)* | Alarma de una categoría crítica: `setAlarmClock`, sonido de alarma y pantalla completa sobre el bloqueo. |
| **Cuidador** *(v1.4)* | Persona que comparte un plan con otra (R-19); `assignee` dice qué actividades son suyas. |
| **Ventana de programación** *(v2.0)* | Próximos 7 días: en los planes recurrentes solo se crean y programan las alarmas que caen dentro (R-23.7). |

---

## R-1 Plantilla de prompt

**Historia:** Como usuario, quiero copiar una plantilla de prompt para que una IA convierta mi rutina en texto libre en el JSON que la app entiende.

- **R-1.1** THE SYSTEM SHALL mostrar en la pantalla *Nueva rutina* un botón "Copiar plantilla".
- **R-1.2** WHEN el usuario pulsa "Copiar plantilla", THE SYSTEM SHALL copiar al portapapeles el texto completo de la plantilla (instrucciones + esquema de ejemplo v1 + marcador `[PEGA AQUÍ TU RUTINA]`) y mostrar la confirmación "Plantilla copiada".
- **R-1.3** THE SYSTEM SHALL cargar la plantilla desde un recurso de la app (`res/raw`), no desde texto escrito en la UI.
- **R-1.4** THE SYSTEM SHALL permitir ver la plantilla completa antes de copiarla (sección desplegable).
- **R-1.5** *(v1.2)* THE SYSTEM SHALL ofrecer una plantilla por escenario (R-16) y aplicar R-1.2–R-1.4 a la del escenario elegido; además de copiarla, se puede enviar a una IA con "Abrir en IA" (R-14.7).

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
- **R-3.11** IF `dayOffset` está presente y no es un entero entre 0 y 30, THEN THE SYSTEM SHALL mostrar "Actividad {n} («{title}»): dayOffset debe ser un número entero entre 0 y 30 (días después de la fecha de la rutina)". *(v1.1: antes 0–1, D-55)*
- **R-3.12** IF `durationMin` está presente y no es un entero entre 1 y 1440, THEN THE SYSTEM SHALL mostrar un error que indique la actividad y el campo.
- **R-3.13** IF un campo tiene un tipo incorrecto (por ejemplo, `time` numérico o `title` como lista), THEN THE SYSTEM SHALL indicar la actividad, el campo y el tipo esperado en lenguaje no técnico.
- **R-3.14** WHERE `category` falta o está vacía, THE SYSTEM SHALL asignar `"general"`; en otro caso THE SYSTEM SHALL normalizarla (minúsculas, sin espacios al inicio ni al final, espacios internos convertidos a `-`).
- **R-3.15** WHERE `dayOffset` falta o es `null`, THE SYSTEM SHALL usar `0`.
- **R-3.16** THE SYSTEM SHALL mostrar **todos** los errores encontrados en una sola validación (hasta 20, con "y {k} errores más"), no solo el primero.
- **R-3.17** THE SYSTEM SHALL numerar las actividades desde 1 en los mensajes, según su posición en el JSON.
- **R-3.18** IF el JSON contiene más de 200 actividades, THEN THE SYSTEM SHALL rechazarlo con "Demasiadas actividades ({n}); el máximo es 200".
- **R-3.19** WHEN la validación termina sin errores, THE SYSTEM SHALL abrir la pantalla de *Revisión* con el borrador y los avisos.
- **R-3.20** IF el texto pegado supera 100 KB, THEN THE SYSTEM SHALL rechazarlo sin analizarlo y mostrar "El texto es demasiado largo ({n} KB); el máximo es 100 KB".
- **R-3.21** IF `routine.notes` o `description` superan 500 caracteres, `assignee` supera 40 o `category` supera 30 (tras normalizarla), THEN THE SYSTEM SHALL mostrar un error que indique la actividad (si aplica), el campo y el máximo.
- **R-3.22** IF la rutina tiene más de 10 categorías distintas (tras normalizarlas), THEN THE SYSTEM SHALL rechazarla con "Demasiadas categorías distintas ({n}); el máximo es 10".
- **R-3.23** THE SYSTEM SHALL analizar el texto fuera del hilo principal, probar como mucho 20 candidatos a objeto JSON y descartar objetos con más de 32 niveles de anidamiento, de modo que ningún texto pegado pueda congelar ni cerrar la app; si no queda ningún candidato válido se aplica R-3.3.
- **R-3.24** *(v1.1)* IF `routine.repeatDays` está presente y no es un entero entre 1 y 30, THEN THE SYSTEM SHALL mostrar "repeatDays debe ser un número entero entre 1 y 30 (días que se repite la rutina)". WHERE falta o es `null`, THE SYSTEM SHALL usar `1` (sin repetición).
- **R-3.25** *(v1.1)* WHERE `repeatDays` es N > 1, THE SYSTEM SHALL expandir las actividades: por cada día k de 0 a N−1, una copia de cada actividad con `dayOffset` = k + su `dayOffset` original. Una toma de madrugada (`dayOffset` 1) del último día cae así en el día N+1.
- **R-3.26** *(v1.1)* IF `repeatDays` es mayor que 1 AND alguna actividad tiene `dayOffset` mayor que 1, THEN THE SYSTEM SHALL mostrar "Actividad {n} («{title}»): con repeatDays solo se admite dayOffset 0 o 1; para días distintos entre sí, escribe cada día sin repeatDays".
- **R-3.27** *(v1.1)* THE SYSTEM SHALL aplicar el máximo de 200 actividades (R-3.18) **después** de expandir `repeatDays`, con el mensaje "Demasiadas actividades ({n} contando las repeticiones); el máximo es 200"; los demás errores se informan sobre las actividades del JSON, numeradas según su posición (R-3.17), no sobre las copias.

## R-4 Revisión del borrador

**Historia:** Como usuario, quiero revisar y ajustar las actividades antes de programarlas.

- **R-4.1** THE SYSTEM SHALL mostrar las actividades ordenadas por hora de disparo (`dayOffset`, luego `time`), cada una con hora, título, categoría y descripción (si existe).
- **R-4.2** THE SYSTEM SHALL permitir editar la hora, el título y la categoría de cada actividad, aplicando las mismas reglas de R-3.8, R-3.9, R-3.10 y R-3.14.
- **R-4.3** THE SYSTEM SHALL permitir quitar una actividad del borrador (con opción de deshacer).
- **R-4.4** WHERE `routine.date` venía en el JSON, THE SYSTEM SHALL usarla como fecha preseleccionada.
- **R-4.5** WHERE `routine.date` no venía en el JSON, THE SYSTEM SHALL preseleccionar **hoy** si al menos una actividad tiene hora de disparo futura y, si no, **mañana**, e informar "No venía fecha; elegí {fecha}". WHERE la rutina es de varios días, solo cuentan las actividades del Día 1 (`dayOffset` 0), para no empezar con un Día 1 ya pasado.
- **R-4.6** THE SYSTEM SHALL permitir cambiar la fecha con un selector de fecha y recalcular de inmediato las horas de disparo y las actividades pasadas.
- **R-4.7** WHILE la hora de disparo de una actividad es anterior o igual al momento actual, THE SYSTEM SHALL mostrarla como "Ya pasó", atenuada y excluida por defecto.
- **R-4.8** THE SYSTEM SHALL permitir excluir manualmente cualquier actividad futura (casilla) sin quitarla de la lista.
- **R-4.9** THE SYSTEM SHALL mostrar un resumen con el número de alarmas a programar y el rango horario, p. ej. "28 alarmas, de 06:45 a 02:00 (+1 día)".
- **R-4.10** IF no queda ninguna actividad futura incluida, THEN THE SYSTEM SHALL deshabilitar "Programar alarmas" y mostrar "No hay actividades futuras para programar".
- **R-4.11** IF la fecha elegida está en el pasado y todas las actividades ya pasaron, THEN THE SYSTEM SHALL sugerir cambiar la fecha a mañana.
- **R-4.12** THE SYSTEM SHALL mostrar las notas de la rutina (`routine.notes`) si existen.
- **R-4.13** WHERE la rutina es de varios días, THE SYSTEM SHALL agrupar las actividades por día con una cabecera "Día {k} · {fecha}" (p. ej. "Día 3 · lunes 16 de noviembre") y mostrar cada hora sin el sufijo "(+N días)"; WHERE no lo es, THE SYSTEM SHALL mantener la lista sin cabeceras y el sufijo "(+1 día)" de R-4.9.
- **R-4.14** WHERE la rutina es de varios días, THE SYSTEM SHALL mostrar el resumen como "{n} alarmas en {d} días, del {primer día} al {último día}" (p. ej. "21 alarmas en 7 días, del sábado 14 al viernes 20 de noviembre"), contando solo los días con alguna alarma incluida.
- **R-4.15** WHEN el usuario cambia la fecha (R-4.6), THE SYSTEM SHALL desplazar todo el rango: la fecha elegida pasa a ser el Día 1 y cada actividad conserva su `dayOffset`.
- **R-4.16** *(v1.1)* THE SYSTEM SHALL tratar en Revisión cada copia expandida de `repeatDays` como una actividad independiente (editar, excluir o quitar la toma de un día no cambia las de otros días).

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
- **R-5.10** IF programar la orden haría que la app superara 500 alarmas pendientes en total (límite de Android), THEN THE SYSTEM SHALL no guardar ni programar nada y mostrar "No se pueden programar {n} alarmas: se superaría el máximo de 500 alarmas pendientes ({m} ya programadas)".

## R-6 Importación duplicada / reemplazo

**Historia:** Como usuario que importa la rutina del día cada mañana, quiero decidir qué pasa si ya hay una orden para esa fecha.

- **R-6.1** WHEN el usuario pulsa "Programar alarmas" AND existe al menos una orden *Activa* o *Pausada* con la misma fecha, THE SYSTEM SHALL mostrar un diálogo con las opciones "Reemplazar la anterior", "Mantener ambas" y "Cancelar".
- **R-6.2** WHEN el usuario elige "Reemplazar la anterior", THE SYSTEM SHALL cancelar todas las alarmas pendientes de las órdenes en conflicto, marcarlas *Canceladas*, poner esas órdenes en estado *Cancelada* y luego programar la nueva orden.
- **R-6.3** WHEN el usuario elige "Mantener ambas", THE SYSTEM SHALL programar la nueva orden sin modificar las existentes.
- **R-6.4** WHEN el usuario elige "Cancelar", THE SYSTEM SHALL volver a la revisión sin cambios.
- **R-6.5** IF hay varias órdenes en conflicto, THEN THE SYSTEM SHALL listarlas por nombre en el diálogo, y "Reemplazar" SHALL aplicarse a todas.
- **R-6.6** *(v1.1, sustituye "misma fecha" en R-6.1)* THE SYSTEM SHALL considerar en conflicto toda orden *Activa* o *Pausada* cuyo rango comparta al menos un día con el rango de la nueva orden. Para esta comparación, una rutina que no es de varios días solo ocupa su fecha (su madrugada no cuenta), así dos rutinas diarias seguidas nunca chocan. *(D-56, D-58)*
- **R-6.7** WHERE alguna orden en conflicto o la nueva es de varios días, THE SYSTEM SHALL mostrar junto a cada nombre su rango ("del 14 al 20 de noviembre") y avisar en el diálogo que "Reemplazar la anterior" cancela la rutina **completa**, no solo los días que coinciden.

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
- **R-8.2** THE SYSTEM SHALL mostrar por orden: nombre, fecha, número de alarmas pendientes / total, y estado (Activa, Pausada, Cancelada o Terminada). WHERE la orden es de varios días, la fecha SHALL mostrarse como rango ("del sábado 14 al viernes 20 de noviembre de 2026").
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
- **R-9.6** WHERE la orden es de varios días, THE SYSTEM SHALL agrupar la línea de tiempo por día con las mismas cabeceras que R-4.13; WHERE no lo es, THE SYSTEM SHALL mantener la etiqueta "+1 día" actual.

## R-10 Gestión de la orden completa

**Historia:** Como usuario, quiero eliminar, pausar o duplicar una rutina entera con un toque.

- **R-10.1** WHEN el usuario pulsa "Eliminar todas las alarmas", THE SYSTEM SHALL pedir confirmación indicando cuántas alarmas pendientes se cancelarán.
- **R-10.2** WHEN el usuario confirma "Eliminar todas las alarmas", THE SYSTEM SHALL cancelar en el sistema todas las alarmas *Pendientes* de la orden, quitar sus notificaciones visibles, marcarlas *Canceladas* y poner la orden en estado *Cancelada*, conservando el historial (Hechas, Disparadas, Omitidas).
- **R-10.3** IF la orden tiene alarmas ya *Disparadas* o *Hechas*, THEN THE SYSTEM SHALL conservarlas con su estado al eliminar todas las alarmas y quitar de la bandeja las notificaciones aún visibles de esa orden.
- **R-10.4** WHEN el usuario pulsa "Eliminar orden" (menú) y confirma, THE SYSTEM SHALL cancelar sus alarmas pendientes, quitar sus notificaciones y borrar la orden y todas sus alarmas del almacenamiento.
- **R-10.5** WHEN el usuario pausa una orden *Activa*, THE SYSTEM SHALL cancelar en el sistema sus alarmas *Pendientes* (manteniéndolas *Pendientes* en almacenamiento) y poner la orden en *Pausada*.
- **R-10.6** WHEN el usuario reanuda una orden *Pausada*, THE SYSTEM SHALL marcar como *Omitidas* las alarmas pendientes cuya hora ya pasó, programar las futuras y poner la orden en *Activa*.
- **R-10.7** WHEN el usuario pulsa "Duplicar para otra fecha" y elige una fecha, THE SYSTEM SHALL crear un borrador con las mismas actividades (sin estados) y abrir la *Revisión* con esa fecha. En una orden de varios días la fecha elegida es el nuevo Día 1 y se conservan los `dayOffset`.
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
- **R-13.3** THE SYSTEM SHALL mostrar todos los textos desde recursos, en español por defecto y en inglés cuando el idioma del teléfono sea inglés *(v1.2; antes solo español, D-71)*. Las plantillas también existen en los dos idiomas.
- **R-13.4** THE SYSTEM SHALL soportar tema claro y oscuro.
- **R-13.5** THE SYSTEM SHALL tener cobertura de tests ≥ 80 % en dominio e importador.
- **R-13.6** THE SYSTEM SHALL no solicitar el permiso `INTERNET` (ver R-22.7).
- **R-13.7** *(v1.2)* THE SYSTEM SHALL llamar "Plan" en la interfaz a lo que los requisitos y el código llaman orden ("Eliminar plan", "Plan no encontrado", "Pausar"…), sin cambiar nombres internos (`RoutineOrder`), rutas, deep links ni base de datos *(D-63)*.

## R-14 Entrada compartida *(v1.2)*

**Historia:** Como usuario, quiero enviar la respuesta de mi IA a Rutinify con "Compartir" y mandar la plantilla a mi IA sin copiar y pegar, para crear un plan en menos pasos.

- **R-14.1** THE SYSTEM SHALL aparecer como destino de "Compartir" para texto (`ACTION_SEND` con `text/plain`) desde cualquier app.
- **R-14.2** WHEN la app recibe un texto compartido, THE SYSTEM SHALL abrir *Nueva rutina* con ese texto en el campo, reemplazando lo que hubiera, y validarlo de inmediato con las reglas de R-3 (incluido el límite de R-3.20). *(D-64)*
- **R-14.3** IF la validación del texto compartido encuentra errores, THEN THE SYSTEM SHALL mostrarlos en *Nueva rutina* como en R-3.16, con el texto en el campo para corregirlo; WHEN no hay errores, THE SYSTEM SHALL abrir *Revisión* (R-3.19).
- **R-14.4** IF el envío no trae texto o el texto está vacío, THEN THE SYSTEM SHALL abrir *Nueva rutina* sin cambiar el campo y mostrar "No llegó ningún texto para importar".
- **R-14.5** WHEN llega un texto compartido mientras hay un borrador abierto en *Revisión*, THE SYSTEM SHALL descartar ese borrador y abrir *Nueva rutina* con el texto nuevo. *(D-64)*
- **R-14.6** THE SYSTEM SHALL mostrar en *Nueva rutina* un botón "Abrir en IA" junto a "Copiar plantilla".
- **R-14.7** WHEN el usuario pulsa "Abrir en IA", THE SYSTEM SHALL abrir el selector de apps de Android (`ACTION_SEND`, `text/plain`) con el título "Enviar la plantilla a…" y el texto completo de la plantilla del escenario elegido (R-16.3).
- **R-14.8** IF ninguna app puede recibir el texto, THEN THE SYSTEM SHALL copiar la plantilla al portapapeles y mostrar "No hay apps para enviar la plantilla; la copié al portapapeles".

## R-15 Modo alarma real por categoría *(v1.2)*

**Historia:** Como cuidador, quiero que las actividades importantes (una toma, sacar al cachorro de madrugada) suenen como un despertador aunque el teléfono esté bloqueado o en "No molestar", y que el resto siga siendo un aviso discreto.

- **R-15.1** THE SYSTEM SHALL permitir marcar o desmarcar cada categoría como "crítica" en *Ajustes* › "Alarmas críticas", que lista las categorías de los planes guardados. La marca es global: vale para todos los planes que usan esa categoría. *(D-66, abierta)*
- **R-15.2** WHEN el usuario marca o desmarca una categoría, THE SYSTEM SHALL reprogramar con el modo nuevo las alarmas *Pendientes* de esa categoría en planes *Activos*.
- **R-15.3** WHERE la categoría de una alarma es crítica AND la app tiene permiso de alarmas exactas, THE SYSTEM SHALL programarla con `setAlarmClock` en vez de `setExactAndAllowWhileIdle`.
- **R-15.4** IF la categoría es crítica AND falta el permiso de alarmas exactas, THEN THE SYSTEM SHALL programarla en modo ventana (R-5.4) y mostrar en *Ajustes* y en el detalle del plan "Las alarmas críticas necesitan el permiso de alarmas exactas para sonar a su hora".
- **R-15.5** WHEN el usuario marca la primera categoría crítica AND falta el permiso de alarmas exactas, THE SYSTEM SHALL explicar por qué hace falta y ofrecer abrir el ajuste del sistema, como en R-11.2.
- **R-15.6** WHEN se dispara una alarma crítica, THE SYSTEM SHALL publicar una notificación de categoría alarma en el canal "Alarmas críticas", con sonido de alarma (uso `USAGE_ALARM`) que se repite hasta que el usuario actúe o pasen 10 minutos. *(D-67)*
- **R-15.7** WHERE la app puede usar intents de pantalla completa, THE SYSTEM SHALL mostrar la *pantalla de alarma* sobre la pantalla de bloqueo, encendiendo la pantalla, con el título, la descripción (si existe), la hora, el nombre del plan y los botones "Hecho", "+5 min" y "Silenciar".
- **R-15.8** WHEN el usuario pulsa "Hecho" o "+5 min" en la pantalla de alarma o en la notificación, THE SYSTEM SHALL aplicar R-7.4 o R-7.5, detener el sonido y cerrar la pantalla de alarma.
- **R-15.9** WHEN el usuario pulsa "Silenciar", THE SYSTEM SHALL detener el sonido, cerrar la pantalla de alarma y dejar una notificación sin sonido con "Hecho" y "+5 min"; la alarma sigue *Disparada*.
- **R-15.10** WHEN pasan 10 minutos desde el disparo sin que el usuario actúe, THE SYSTEM SHALL silenciar la alarma como en R-15.9.
- **R-15.11** IF la app no puede usar intents de pantalla completa (Android 14+ sin el permiso `USE_FULL_SCREEN_INTENT`), THEN THE SYSTEM SHALL mostrar la alarma como notificación emergente con el mismo sonido y los mismos botones, y mostrar en *Permisos* la fila "Alarmas a pantalla completa: desactivado" con un botón que abra el ajuste del sistema. *(D-68)*
- **R-15.12** WHILE "No molestar" deja pasar las alarmas (lo predeterminado en Android), THE SYSTEM SHALL hacer sonar las alarmas críticas; las no críticas siguen las reglas de "No molestar".
- **R-15.13** WHEN el teléfono arranca o cambia la hora o la zona horaria (R-12), THE SYSTEM SHALL reprogramar las alarmas críticas con `setAlarmClock`, como las demás.
- **R-15.14** THE SYSTEM SHALL mantener R-7 sin cambios para las alarmas de categorías no críticas.

## R-16 Plantillas por escenario *(v1.2)*

**Historia:** Como usuario, quiero una plantilla pensada para mi caso (cachorro, medicación, bebé, estudio) para que la IA me devuelva un plan útil a la primera.

- **R-16.1** THE SYSTEM SHALL ofrecer cinco plantillas: "Cachorro", "Medicación", "Bebé", "Estudio" y "Genérica", cada una en su propio recurso de `res/raw`. *(D-70)*
- **R-16.2** THE SYSTEM SHALL mostrar en *Nueva rutina*, antes de "Ver plantilla", "Copiar plantilla" y "Abrir en IA", un selector de escenario con "Genérica" elegida la primera vez, y recordar la última elección.
- **R-16.3** WHEN el usuario elige un escenario, THE SYSTEM SHALL usar su plantilla en "Ver plantilla" (R-1.4), "Copiar plantilla" (R-1.2) y "Abrir en IA" (R-14.7).
- **R-16.4** THE SYSTEM SHALL pedir con todas las plantillas el mismo contrato JSON (v1 hasta v2.0); cada plantilla incluye un ejemplo válido de su escenario.
- **R-16.5** THE SYSTEM SHALL indicar en la plantilla "Medicación" que la IA use solo los horarios y dosis que escribe el usuario, sin proponer dosis ni cambiar la pauta.

## R-17 Diagnóstico local *(v1.3, opcional)*

**Historia:** Como desarrollador en la prueba cerrada, quiero saber dónde se atascan los usuarios sin recoger datos personales ni usar la red.

- **R-17.1** THE SYSTEM SHALL ofrecer en *Ajustes* el interruptor "Guardar diagnóstico en este teléfono", desactivado por defecto. *(D-72)*
- **R-17.2** WHILE el interruptor está activado, THE SYSTEM SHALL contar localmente, sin identificadores ni contenido de las rutinas: las validaciones fallidas por tipo de error y las veces que se llega a cada paso (abrir *Nueva rutina*, pulsar "Validar", abrir *Revisión*, pulsar "Programar alarmas" y programar un plan).
- **R-17.3** WHILE el interruptor está activado, THE SYSTEM SHALL contar los planes programados, para medir si el usuario crea una segunda rutina.
- **R-17.4** THE SYSTEM SHALL no enviar los contadores a ningún sitio ni solicitar el permiso `INTERNET` (R-13.6).
- **R-17.5** WHEN el usuario pulsa "Exportar diagnóstico", THE SYSTEM SHALL abrir el selector de Android (`ACTION_SEND`, `text/plain`) con los contadores en texto legible y la versión de la app.
- **R-17.6** WHEN el usuario pulsa "Borrar diagnóstico", THE SYSTEM SHALL poner a cero todos los contadores y mostrar "Diagnóstico borrado".
- **R-17.7** WHEN el usuario desactiva el interruptor, THE SYSTEM SHALL dejar de contar y borrar los contadores.

## R-18 Reprogramar en bloque *(v1.4)*

**Historia:** Como cuidador, si el día se retrasa ("voy tarde"), quiero mover de una vez todas las alarmas pendientes de un plan, o corregir una sola, sin volver a importarlo.

- **R-18.1** THE SYSTEM SHALL ofrecer en el detalle de un plan *Activo* o *Pausado* la acción "Voy tarde", que mueve todas sus alarmas *Pendientes* el mismo número de minutos.
- **R-18.2** WHEN el usuario pulsa "Voy tarde", THE SYSTEM SHALL pedir el sentido ("Retrasar" o "Adelantar") y los minutos (de 1 a 720), y mostrar la vista previa "{n} alarmas; la próxima pasa de las {hora} a las {hora}". *(D-73)*
- **R-18.3** WHEN el usuario confirma, THE SYSTEM SHALL cambiar la hora de pared de cada alarma *Pendiente* (pasando al día siguiente o al anterior si cruza la medianoche), dejar sin cambios las *Disparadas*, *Hechas*, *Omitidas* y *Canceladas*, reprogramar en el sistema las de planes *Activos* y mostrar "{n} alarmas movidas".
- **R-18.4** WHERE una alarma *Pendiente* está pospuesta (+5 min), THE SYSTEM SHALL mover también su hora pospuesta.
- **R-18.5** IF al adelantar alguna alarma quedaría en el pasado (anterior o igual a ahora), THEN THE SYSTEM SHALL no cambiar nada y mostrar "Con ese ajuste, {k} alarmas quedarían en el pasado; elige menos minutos".
- **R-18.6** IF alguna alarma quedaría antes del Día 1 del plan o después del Día 31, THEN THE SYSTEM SHALL no cambiar nada y mostrar "Con ese ajuste, alguna alarma saldría del rango del plan (del día 1 al día 31)".
- **R-18.7** WHILE el plan está *Pausado*, THE SYSTEM SHALL guardar las horas nuevas sin programarlas; se programan al reanudar (R-10.6).
- **R-18.8** THE SYSTEM SHALL no volver a comprobar conflictos (R-6) al mover alarmas, aunque el rango del plan cambie un día.
- **R-18.9** THE SYSTEM SHALL permitir editar la hora, el título y la categoría de una alarma *Pendiente* desde el detalle, con las reglas de R-4.2, y reprogramarla si el plan está *Activo*.
- **R-18.10** IF la hora editada ya pasó, THEN THE SYSTEM SHALL no guardarla y mostrar "Esa hora ya pasó; elige una hora futura".

## R-19 Compartir con otro cuidador sin servidor *(v1.4)*

**Historia:** Como cuidador, quiero pasar el plan al teléfono de otra persona (pareja, cuidadora) con un QR o un mensaje, y que cada uno pueda quedarse solo con sus tareas.

- **R-19.1** THE SYSTEM SHALL ofrecer en el menú del detalle "Compartir plan", que genera un JSON del contrato del plan (v1, o v2 si es recurrente) con nombre, fecha, notas y todas sus actividades (incluido `assignee`), sin estados ni posposiciones.
- **R-19.2** WHEN el usuario pulsa "Compartir plan", THE SYSTEM SHALL ofrecer "Mostrar QR" y "Compartir como texto".
- **R-19.3** WHEN el usuario elige "Compartir como texto", THE SYSTEM SHALL abrir el selector de Android con la línea "Importa este plan en Rutinify" y el JSON en un bloque de código; el otro teléfono lo importa con R-14 o pegándolo.
- **R-19.4** WHEN el usuario elige "Mostrar QR" AND el enlace cabe en un QR, THE SYSTEM SHALL mostrar un QR con el enlace `rutinify://import?…` (D-75) y el texto "Escanéalo desde Rutinify en el otro teléfono".
- **R-19.5** IF el enlace supera 2.331 bytes (QR versión 40 con corrección M), THEN THE SYSTEM SHALL no generar el QR y mostrar "Este plan es demasiado grande para un QR; compártelo como texto" con el botón "Compartir como texto".
- **R-19.6** THE SYSTEM SHALL ofrecer en *Nueva rutina* el botón "Escanear QR".
- **R-19.7** WHEN la app lee un QR de Rutinify o abre un enlace `rutinify://import`, THE SYSTEM SHALL decodificar el JSON, ponerlo en *Nueva rutina* y validarlo como en R-14.2.
- **R-19.8** IF el QR o el enlace no contienen un plan de Rutinify o están dañados, THEN THE SYSTEM SHALL mostrar "Este código no contiene un plan de Rutinify".
- **R-19.9** WHERE el borrador tiene actividades con `assignee`, THE SYSTEM SHALL mostrar en *Revisión* el filtro "Solo las tareas de…" con cada responsable distinto y "Todas"; al elegir un responsable, THE SYSTEM SHALL quitar del borrador las actividades de los demás responsables y conservar las que no tienen responsable; al elegir "Todas", volver a mostrarlas. *(D-76)*
- **R-19.10** THE SYSTEM SHALL no usar ningún servidor: el plan viaja solo dentro del QR, del enlace o del texto.

## R-20 Historial y exportación *(v1.4)*

**Historia:** Como cuidador, quiero un registro de qué se hizo y cuándo, para enseñárselo al veterinario o al médico.

- **R-20.1** THE SYSTEM SHALL registrar, con fecha y hora, cada vez que una alarma de un plan pasa a *Hecha*, pasa a *Omitida* o se pospone (+5 min). *(D-77)*
- **R-20.2** THE SYSTEM SHALL mostrar en el detalle del plan el resumen "{h} de {t} hechas" (t = alarmas del plan que no están *Canceladas*) y, si las hay, "{o} omitidas · {p} pospuestas".
- **R-20.3** THE SYSTEM SHALL ofrecer en el menú del detalle "Exportar historial" con las opciones "Texto" y "PDF".
- **R-20.4** WHEN el usuario elige "Texto", THE SYSTEM SHALL abrir el selector de Android con el nombre y el rango del plan, el resumen de R-20.2 y, por día, cada alarma con su hora, título, estado, hora en que se marcó *Hecha* y veces que se pospuso.
- **R-20.5** WHEN el usuario elige "PDF", THE SYSTEM SHALL generar en el teléfono, sin red, un PDF con el mismo contenido y compartirlo como archivo (`application/pdf`). *(D-78)*
- **R-20.6** IF no se puede generar el PDF, THEN THE SYSTEM SHALL mostrar "No se pudo crear el PDF; prueba a exportarlo como texto".

## R-21 Widget "Lo siguiente" *(v1.4)*

**Historia:** Como cuidador, quiero ver en la pantalla de inicio del teléfono qué toca ahora y marcarlo como hecho sin abrir la app.

- **R-21.1** THE SYSTEM SHALL ofrecer un widget "Lo siguiente" con la próxima alarma *Pendiente* de los planes *Activos*: hora (y día, si no es hoy), título y nombre del plan. *(D-79)*
- **R-21.2** THE SYSTEM SHALL incluir en el widget el botón "Hecho", que aplica R-9.5 a esa alarma.
- **R-21.3** WHILE no hay alarmas pendientes en planes activos, THE SYSTEM SHALL mostrar en el widget "Nada pendiente".
- **R-21.4** THE SYSTEM SHALL actualizar el widget cuando una alarma se dispara, se marca, se pospone, se mueve o se reprograma, y cuando un plan cambia de estado o se borra.
- **R-21.5** WHEN el usuario toca el widget fuera del botón, THE SYSTEM SHALL abrir el detalle del plan con esa alarma resaltada (R-7.6).

## R-22 IA en el dispositivo *(v2.0)*

**Historia:** Como usuario de un teléfono compatible, quiero escribir mi rutina y que el propio teléfono la convierta, sin salir de la app ni usar otra IA.

- **R-22.1** WHERE Gemini Nano está disponible en el dispositivo (estado `AVAILABLE` de ML Kit GenAI), THE SYSTEM SHALL mostrar en *Nueva rutina* la opción "Convertir en el teléfono", con un campo para escribir la rutina en texto libre. *(D-80)*
- **R-22.2** WHEN el usuario pulsa "Convertir", THE SYSTEM SHALL pedir al modelo el JSON del contrato con las instrucciones de la plantilla del escenario elegido, validar el resultado con R-3 (y R-23 si aplica) y abrir *Revisión* si no hay errores; si los hay, mostrar el JSON generado con los errores, como en R-3.16.
- **R-22.3** WHILE la conversión está en curso, THE SYSTEM SHALL mostrar "Convirtiendo…" y permitir cancelarla.
- **R-22.4** IF el modelo no está disponible (`UNAVAILABLE`, `DOWNLOADABLE` o `DOWNLOADING`), THEN THE SYSTEM SHALL ocultar la opción y mantener el flujo de copiar y pegar sin cambios.
- **R-22.5** IF la conversión falla, THEN THE SYSTEM SHALL mostrar "La IA del teléfono no pudo convertir la rutina; usa la plantilla con tu IA de siempre".
- **R-22.6** IF el texto supera el límite de entrada del modelo (unos 4.000 tokens), THEN THE SYSTEM SHALL no enviarlo y mostrar "El texto es demasiado largo para la IA del teléfono; acórtalo o usa tu IA de siempre".
- **R-22.7** THE SYSTEM SHALL procesar el texto solo en el teléfono. Si la dependencia exige el permiso `INTERNET`, la compatibilidad con R-13.6 se decide antes de implementar *(D-80, abierta)*.

## R-23 Schema v2 con repetición sin fin *(v2.0)*

**Historia:** Como usuario con rutinas que se repiten (los paseos de cada día, la clase de los lunes), quiero importarlas una vez y que se repitan hasta que las pare.

- **R-23.1** THE SYSTEM SHALL aceptar `schemaVersion` 1 y 2. Un JSON v1 se trata igual que hasta ahora; v2 es v1 más el campo `routine.recurrence`. R-3.4 pasa a decir "Versión de formato no soportada (se esperaba 1 o 2)". *(D-81)*
- **R-23.2** THE SYSTEM SHALL aceptar en v2 `routine.recurrence` con `weekdays` (lista de 1 a 7 días distintos entre `"MO"`, `"TU"`, `"WE"`, `"TH"`, `"FR"`, `"SA"` y `"SU"`) y `until` opcional (`yyyy-MM-dd`); sin `until`, la rutina se repite sin fin.
- **R-23.3** IF `recurrence` aparece con `schemaVersion` 1, THEN THE SYSTEM SHALL mostrar "El campo recurrence necesita schemaVersion 2".
- **R-23.4** IF `weekdays` falta, está vacío o tiene valores desconocidos o repetidos, THEN THE SYSTEM SHALL mostrar "recurrence.weekdays debe ser una lista de días como [\"MO\", \"WE\"]".
- **R-23.5** IF `until` no es una fecha válida o es anterior a `routine.date`, THEN THE SYSTEM SHALL mostrar "recurrence.until debe ser una fecha igual o posterior a la de la rutina".
- **R-23.6** IF `recurrence` aparece junto con `repeatDays` mayor que 1 o con alguna actividad con `dayOffset` mayor que 1, THEN THE SYSTEM SHALL mostrar "recurrence no se puede combinar con repeatDays ni con dayOffset mayor que 1".
- **R-23.7** THE SYSTEM SHALL crear y programar solo las alarmas de los próximos 7 días (*ventana de programación*) y, una vez al día y en cada evento de R-12, crear y programar las que entran en la ventana.
- **R-23.8** THE SYSTEM SHALL contar para el máximo de 500 alarmas pendientes (R-5.10) solo las alarmas creadas dentro de la ventana; IF programar un plan recurrente superaría ese máximo, THEN THE SYSTEM SHALL aplicar el mensaje de R-5.10.
- **R-23.9** THE SYSTEM SHALL considerar en conflicto (R-6) un plan recurrente y otro plan si algún día del otro plan cae en uno de los días de la semana del recurrente, entre su fecha de inicio y `until` (o sin límite).
- **R-23.10** THE SYSTEM SHALL mostrar la recurrencia en Inicio y en el detalle, p. ej. "Lunes, miércoles y viernes, desde el 16 de noviembre" o "… hasta el 20 de diciembre".
- **R-23.11** WHILE un plan recurrente sin `until` está *Activo*, THE SYSTEM SHALL no considerarlo *Terminado* (R-8.3).
- **R-23.12** WHILE un plan recurrente está *Pausado*, THE SYSTEM SHALL no crear alarmas nuevas; WHEN se reanuda, THE SYSTEM SHALL crear las de la ventana a partir de ese momento, sin crear las de los días en pausa.
- **R-23.13** WHEN el usuario duplica un plan recurrente (R-10.7), THE SYSTEM SHALL conservar la recurrencia con la fecha elegida como inicio.
- **R-23.14** THE SYSTEM SHALL mostrar en *Revisión* de un plan recurrente cómo queda una semana (*abierta, D-81*).

## R-24 Wear OS *(v2.0)*

**Historia:** Como cuidador con las manos ocupadas, quiero marcar una actividad como hecha o posponerla desde el reloj.

- **R-24.1** THE SYSTEM SHALL mostrar las notificaciones de alarma en un reloj Wear OS emparejado, con las acciones "Hecho" y "+5 min" (notificaciones puenteadas, sin app de reloj). *(D-82)*
- **R-24.2** WHEN el usuario pulsa "Hecho" o "+5 min" en el reloj, THE SYSTEM SHALL aplicar R-7.4 o R-7.5 en el teléfono.
- **R-24.3** WHERE la alarma es crítica, THE SYSTEM SHALL incluir también en el reloj la acción "Silenciar" (R-15.9).

---

## Fuera de alcance

Alarmas relativas a "me desperté" · llamada directa a la API de Claude u otra IA en la nube · exportar a `.ics` · app propia de Wear OS (más allá de las notificaciones puenteadas de R-24) · sincronizar planes entre teléfonos con un servidor (compartir sin servidor está en R-19).

Pasaron al roadmap: rutinas recurrentes sin fin (R-23, v2.0), widget "Lo siguiente" (R-21, v1.4) y compartir un plan entre teléfonos (R-19, v1.4).
