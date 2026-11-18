# Checklist de publicación — Rutinify

Lista para subir Rutinify a Google Play, primero a la pista de prueba cerrada (T-54) y después a producción.
Cada casilla se marca cuando está hecha en Play Console o en el repositorio. Lo que depende de una decisión de
Johan está marcado como **Decisión**.

Estado del documento: preparado en T-53 (2026-11-18); **pendiente de revisión por Johan**.

## 1. Cuenta y app en Play Console

- [ ] Cuenta de desarrollador activa. Si es personal y se creó después del 13-11-2023, Play exige una prueba cerrada
      con al menos 12 testers durante 14 días seguidos antes de pedir producción (T-54).
- [ ] App creada con nombre "Rutinify", idioma predeterminado español (España) y traducción a inglés (R-13.3).
- [ ] Tipo: aplicación; gratuita; sin anuncios.
- [ ] Paquete `com.mirkwood.rutinify` (no se puede cambiar después de la primera subida).

## 2. Compilación de release

- [ ] Clave de subida creada y guardada fuera del repositorio (nunca se commitea, `CLAUDE.md` §11). Firma de apps de
      Google Play activada.
- [ ] `signingConfig` de release leyendo la clave desde `local.properties` o variables de entorno, no desde el código.
- [ ] `versionCode` y `versionName` subidos en cada entrega (ahora `1` / `1.0`; para v1.2 propuesto `12` / `1.2`).
- [ ] **Decisión:** activar R8 (`isMinifyEnabled = true`) en release. Hoy está desactivado; si se activa, revisar reglas
      de Room, Hilt y kotlinx.serialization y probar la app firmada entera antes de subir.
- [ ] `./gradlew bundleRelease` genera el AAB; se instala con `bundletool` en un teléfono real y se repasa
      `specs/manual-test.md`.
- [ ] `targetSdk 36` (Play exige el nivel de API del último año) y `minSdk 28`.

## 3. Ficha de Play Store

- [ ] Nombre corto (30 caracteres): "Rutinify: alarmas de rutinas".
- [ ] Descripción breve (80 caracteres), en español y en inglés. Propuesta: "Convierte la rutina que te da tu IA
      en alarmas: medicación, cachorro, bebé o estudio." / "Turn the routine your AI writes into alarms: medication,
      puppy, baby or study."
- [ ] Descripción completa, en los dos idiomas: qué hace (plantilla → IA → JSON → alarmas), que funciona sin
      internet ni cuenta, alarmas críticas que suenan como despertador, y que la app no da consejos médicos (solo
      programa los horarios que escribe el usuario, R-16.5).
- [ ] Icono 512×512, gráfico de funciones 1024×500.
- [ ] Capturas de teléfono (mínimo 2, recomendado 4–8) en los dos idiomas: Inicio, *Nueva rutina* con la
      plantilla, *Revisión*, detalle del plan, pantalla de alarma, *Ajustes*.
- [ ] Categoría: Productividad (alternativa: Herramientas). Etiquetas: recordatorios, alarmas, rutinas.
- [ ] Datos de contacto: correo de soporte. **Decisión:** qué correo público usar.

## 4. Política de privacidad

Play la exige para cualquier app con permisos sensibles. Rutinify no tiene servidor, así que basta una página
estática (por ejemplo, GitHub Pages del repositorio).

- [ ] **Decisión:** dónde se publica la página y con qué nombre de responsable.
- [ ] Texto propuesto (adaptar el responsable y el correo):

> **Política de privacidad de Rutinify.** Rutinify no recoge, envía ni vende datos personales. Las rutinas, las
> alarmas y los ajustes se guardan solo en tu teléfono y se borran al desinstalar la app. La app no tiene cuenta de
> usuario, no usa internet (no pide ese permiso), no incluye anuncios ni herramientas de análisis de terceros.
> Si activas "Guardar diagnóstico en este teléfono", la app cuenta en el teléfono los pasos que usas y los errores
> al validar, sin nombres ni contenido de tus rutinas; esos contadores solo salen del teléfono si tú pulsas
> "Exportar diagnóstico" y eliges con quién compartirlos. Cuando copias la plantilla o la envías a una app de IA,
> lo que escribas en esa app se rige por la política de esa app. Contacto: [correo].

## 5. Seguridad de los datos (formulario de Play)

- [ ] ¿Recoge o comparte datos? **No.** Todo se queda en el teléfono; no hay transmisión fuera del dispositivo
      (Play considera "recogida" solo lo que sale del teléfono).
- [ ] El diagnóstico opcional no cuenta como recogida: se guarda en local y solo se comparte por acción explícita del
      usuario con el selector de Android (D-72, D-90).
- [ ] ¿Los datos se cifran en tránsito? No aplica (no hay tránsito).
- [ ] ¿El usuario puede pedir que se borren? Sí: borrando la app o los datos de la app; el diagnóstico tiene
      "Borrar diagnóstico".

## 6. Declaraciones de permisos

| Permiso | Para qué | Declaración en Play |
|---|---|---|
| `POST_NOTIFICATIONS` | Mostrar cada alarma con sus botones (R-7.1) | No requiere formulario |
| `SCHEDULE_EXACT_ALARM` | Que cada aviso suene a su hora (R-5.3) | No requiere formulario; el usuario lo concede (D-09, D-69) |
| `RECEIVE_BOOT_COMPLETED` | Reprogramar tras reiniciar (R-12.1) | No requiere formulario |
| `USE_FULL_SCREEN_INTENT` | Pantalla de alarma de las alarmas críticas (R-15.7) | **Sí.** Formulario "Intent de pantalla completa": función principal de alarma (D-68) |
| `USE_EXACT_ALARM` | Solo en la versión de prueba de T-54 (D-69, opción C) | **Sí.** Formulario de alarmas exactas: app de alarmas o recordatorios |

- [ ] Formulario de `USE_FULL_SCREEN_INTENT`: explicar que las alarmas críticas (medicación, cuidados) suenan como
      despertador y deben verse sobre la pantalla de bloqueo; vídeo corto con una alarma crítica sonando. Si Play no lo
      aprueba, la app sigue funcionando con notificación emergente (R-15.11).
- [ ] Formulario de alarmas exactas solo para la versión con `USE_EXACT_ALARM` de la pista cerrada (T-54).
- [ ] Sin `INTERNET` (R-13.6), comprobado por `ManifestPermissionsTest`.

## 7. Clasificación de contenido y público

- [ ] Cuestionario IARC: sin violencia, sin contenido sexual, sin apuestas, sin interacción entre usuarios, sin
      compras. Resultado esperado: PEGI 3 / Everyone.
- [ ] Público objetivo: mayores de 18 años (cuidadores). No dirigida a niños, así que no aplica la política de
      familias.
- [ ] Declaraciones: sin anuncios, sin acceso a datos financieros ni de salud gestionados por la app (la app no
      interpreta ni guarda datos de salud: solo textos y horas que escribe el usuario).
- [ ] **Decisión:** la descripción menciona "medicación"; confirmar que no se presenta como app médica (no lo es) para
      no entrar en la política de apps de salud.

## 8. Antes de cada subida

- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug koverVerify` en verde.
- [ ] Pasos de `specs/manual-test.md` de la versión en un teléfono real con Android 14 o superior.
- [ ] README y notas de la versión en español e inglés.
