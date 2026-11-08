package com.mirkwood.rutinify.ui.common

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.Severity
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.domain.importer.ValueType
import org.junit.Test
import org.junit.runner.RunWith

/** T-12: every `ValidationIssue` becomes a Spanish message from resources (R-3.3–R-3.22, R-13.3). */
@RunWith(AndroidJUnit4::class)
class ErrorMessagesTest {

    private val resources: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun text(issue: ValidationIssue): String = ErrorMessages.message(issue).resolve(resources)

    private fun item(
        kind: IssueKind,
        field: String?,
        title: String? = "Siesta",
        time: String? = "09:30",
        received: String? = null,
        expected: ValueType? = null,
        limit: Int? = null,
        index: Int = 3,
    ) = ValidationIssue(
        itemIndex = index,
        itemTitle = title,
        itemTime = time,
        field = field,
        kind = kind,
        received = received,
        expected = expected,
        limit = limit,
    )

    @Test
    fun `cada tipo de problema tiene un mensaje no vacio`() { // R-13.3
        IssueKind.entries.forEach { kind ->
            val routineLevel = text(ValidationIssue(kind = kind, field = "routine.notes", received = "x", limit = 1))
            val itemLevel = text(item(kind, field = "time", received = "x", expected = ValueType.TEXT, limit = 1))

            assertThat(routineLevel).isNotEmpty()
            assertThat(itemLevel).isNotEmpty()
            assertThat(routineLevel).doesNotContain("%")
            assertThat(itemLevel).doesNotContain("%")
        }
    }

    @Test
    fun `sin JSON`() { // R-3.3
        assertThat(text(ValidationIssue(kind = IssueKind.NO_JSON_FOUND))).isEqualTo(
            "No encontré un JSON válido en el texto. Asegúrate de pegar la respuesta completa de la IA.",
        )
    }

    @Test
    fun `version no soportada con y sin valor recibido`() { // R-3.4
        assertThat(text(ValidationIssue(field = "schemaVersion", kind = IssueKind.UNSUPPORTED_SCHEMA_VERSION, received = "2")))
            .isEqualTo("Versión de formato no soportada (se esperaba 1); se recibió «2»")
        assertThat(text(ValidationIssue(field = "schemaVersion", kind = IssueKind.UNSUPPORTED_SCHEMA_VERSION)))
            .isEqualTo("Versión de formato no soportada (se esperaba 1); falta el campo schemaVersion")
    }

    @Test
    fun `rutina sin nombre ausente o vacio`() { // R-3.5
        val expected = "La rutina no tiene nombre (campo routine.name)"

        assertThat(text(ValidationIssue(field = "routine.name", kind = IssueKind.MISSING_FIELD))).isEqualTo(expected)
        assertThat(text(ValidationIssue(field = "routine.name", kind = IssueKind.BLANK_FIELD))).isEqualTo(expected)
    }

    @Test
    fun `falta el bloque routine`() {
        assertThat(text(ValidationIssue(field = "routine", kind = IssueKind.MISSING_FIELD)))
            .isEqualTo("Falta el campo routine con los datos de la rutina")
    }

    @Test
    fun `fecha no valida`() { // R-3.6
        assertThat(text(ValidationIssue(field = "routine.date", kind = IssueKind.INVALID_DATE, received = "05/11/2026")))
            .isEqualTo("La fecha «05/11/2026» no es válida; usa el formato 2026-11-05")
    }

    @Test
    fun `sin actividades`() { // R-3.7
        assertThat(text(ValidationIssue(field = "items", kind = IssueKind.NO_ITEMS)))
            .isEqualTo("La rutina no tiene actividades (campo items)")
    }

    @Test
    fun `hora no valida con titulo y valor`() { // R-3.8, R-3.17
        assertThat(text(item(IssueKind.INVALID_TIME, "time", time = "25:00", received = "25:00")))
            .isEqualTo("Actividad 3 («Siesta»): la hora «25:00» no es válida; usa el formato 24 h, por ejemplo 06:45")
    }

    @Test
    fun `falta la hora`() { // R-3.8
        assertThat(text(item(IssueKind.MISSING_FIELD, "time", time = null)))
            .isEqualTo("Actividad 3 («Siesta»): falta la hora; usa el formato 24 h, por ejemplo 06:45")
    }

    @Test
    fun `falta el titulo usa la hora como contexto`() { // R-3.9
        val expected = "Actividad 3 (a las 09:30): falta el título"

        assertThat(text(item(IssueKind.MISSING_FIELD, "title", title = null))).isEqualTo(expected)
        assertThat(text(item(IssueKind.BLANK_FIELD, "title", title = null))).isEqualTo(expected)
    }

    @Test
    fun `sin titulo ni hora solo se indica el numero`() { // R-3.17
        assertThat(text(item(IssueKind.MISSING_FIELD, "title", title = null, time = null)))
            .isEqualTo("Actividad 3: falta el título")
    }

    @Test
    fun `aviso de titulo recortado con el titulo acortado en el contexto`() { // R-3.10
        val longTitle = "a".repeat(90)
        val issue = item(IssueKind.TITLE_TRUNCATED, "title", title = longTitle).copy(severity = Severity.WARNING)

        assertThat(text(issue)).isEqualTo(
            "Actividad 3 («${"a".repeat(29)}…»): el título tenía más de 80 caracteres y se ha recortado",
        )
    }

    @Test
    fun `dayOffset fuera de rango`() { // R-3.11
        assertThat(text(item(IssueKind.DAY_OFFSET_OUT_OF_RANGE, "dayOffset", received = "2")))
            .isEqualTo("Actividad 3 («Siesta»): dayOffset debe ser 0 (mismo día) o 1 (día siguiente)")
    }

    @Test
    fun `duracion fuera de rango indica actividad y campo`() { // R-3.12
        assertThat(text(item(IssueKind.DURATION_OUT_OF_RANGE, "durationMin", received = "0")))
            .isEqualTo("Actividad 3 («Siesta»): durationMin debe ser un número de minutos entre 1 y 1440")
    }

    @Test
    fun `tipo incorrecto indica actividad campo tipo esperado y valor`() { // R-3.13
        assertThat(text(item(IssueKind.WRONG_TYPE, "time", received = "645", expected = ValueType.TEXT)))
            .isEqualTo("Actividad 3 («Siesta»): el campo time debe ser un texto entre comillas (se recibió «645»)")
    }

    @Test
    fun `tipo incorrecto describe cada tipo en lenguaje no tecnico`() { // R-3.13
        val expectedTypes = mapOf(
            ValueType.TEXT to "un texto entre comillas",
            ValueType.WHOLE_NUMBER to "un número entero",
            ValueType.OBJECT to "un bloque entre llaves { }",
            ValueType.LIST to "una lista entre corchetes [ ]",
        )

        expectedTypes.forEach { (type, words) ->
            assertThat(text(ValidationIssue(field = "routine", kind = IssueKind.WRONG_TYPE, expected = type)))
                .isEqualTo("El campo routine debe ser $words")
        }
    }

    @Test
    fun `una actividad que no es un bloque`() { // R-3.13
        assertThat(text(item(IssueKind.WRONG_TYPE, field = null, title = null, time = null, received = "suelto", expected = ValueType.OBJECT)))
            .isEqualTo("Actividad 3: debe ser un bloque entre llaves { } (se recibió «suelto»)")
    }

    @Test
    fun `demasiadas actividades`() { // R-3.18
        assertThat(text(ValidationIssue(field = "items", kind = IssueKind.TOO_MANY_ITEMS, received = "201")))
            .isEqualTo("Demasiadas actividades (201); el máximo es 200")
    }

    @Test
    fun `texto pegado demasiado largo`() { // R-3.20
        assertThat(text(ValidationIssue(kind = IssueKind.INPUT_TOO_LONG, received = "101", limit = 100)))
            .isEqualTo("El texto es demasiado largo (101 KB); el máximo es 100 KB")
    }

    @Test
    fun `texto de un campo demasiado largo indica actividad campo y maximo`() { // R-3.21
        assertThat(text(item(IssueKind.TEXT_TOO_LONG, "description", limit = 500)))
            .isEqualTo("Actividad 3 («Siesta»): el campo description supera el máximo de 500 caracteres")
        assertThat(text(ValidationIssue(field = "routine.notes", kind = IssueKind.TEXT_TOO_LONG, limit = 500)))
            .isEqualTo("El campo routine.notes supera el máximo de 500 caracteres")
    }

    @Test
    fun `demasiadas categorias`() { // R-3.22
        assertThat(text(ValidationIssue(field = "items", kind = IssueKind.TOO_MANY_CATEGORIES, received = "11", limit = 10)))
            .isEqualTo("Demasiadas categorías distintas (11); el máximo es 10")
    }

    @Test
    fun `campo desconocido ausente usa un mensaje generico`() {
        assertThat(text(item(IssueKind.MISSING_FIELD, "otro"))).isEqualTo("Actividad 3 («Siesta»): falta el campo otro")
        assertThat(text(ValidationIssue(field = "otro", kind = IssueKind.MISSING_FIELD))).isEqualTo("Falta el campo otro")
    }

    // R-3.16
    @Test
    fun `con 20 errores o menos se muestran todos sin resumen`() {
        val lines = ErrorMessages.list(List(20) { ValidationIssue(kind = IssueKind.NO_ITEMS) }).map { it.resolve(resources) }

        assertThat(lines).hasSize(20)
        assertThat(lines.last()).isEqualTo("La rutina no tiene actividades (campo items)")
    }

    @Test
    fun `con mas de 20 errores se muestran 20 y el numero restante`() { // R-3.16
        val lines = ErrorMessages.list(List(25) { ValidationIssue(kind = IssueKind.NO_ITEMS) }).map { it.resolve(resources) }

        assertThat(lines).hasSize(21)
        assertThat(lines.last()).isEqualTo("y 5 errores más")
    }

    @Test
    fun `con 21 errores el resumen va en singular`() { // R-3.16
        val lines = ErrorMessages.list(List(21) { ValidationIssue(kind = IssueKind.NO_ITEMS) }).map { it.resolve(resources) }

        assertThat(lines.last()).isEqualTo("y 1 error más")
    }
}
