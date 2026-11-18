package com.mirkwood.rutinify.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.data.local.DbTestData
import com.mirkwood.rutinify.data.local.RutinifyDatabase
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import com.mirkwood.rutinify.domain.importer.IssueKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** T-51: local diagnostic counters (R-17.1–R-17.4, R-17.7, D-72). */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsRepositoryImplTest {

    private val db: RutinifyDatabase = DbTestData.inMemoryDb()
    private val repository = DiagnosticsRepositoryImpl(ApplicationProvider.getApplicationContext<Context>(), db.diagnosticCounterDao())

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `apagado por defecto no cuenta nada`() = runTest { // R-17.1
        assertThat(repository.isEnabled()).isFalse()

        repository.record(DiagnosticEvent.Step.NEW_ROUTINE)

        assertThat(repository.counters()).isEmpty()
    }

    @Test
    fun `encendido suma cada evento por su clave`() = runTest { // R-17.2, R-17.3
        repository.setEnabled(true)

        repository.record(DiagnosticEvent.Step.VALIDATE)
        repository.record(DiagnosticEvent.Step.VALIDATE)
        repository.record(DiagnosticEvent.ValidationFailed(IssueKind.INVALID_TIME))
        repository.record(DiagnosticEvent.PlanCreated)

        assertThat(repository.counters()).containsExactly(
            "issue.INVALID_TIME", 1,
            "plans.created", 1,
            "step.validate", 2,
        ).inOrder()
    }

    @Test
    fun `cada tipo de error tiene su propio contador`() = runTest { // R-17.2
        repository.setEnabled(true)

        IssueKind.entries.forEach { repository.record(DiagnosticEvent.ValidationFailed(it)) }

        assertThat(repository.counters().keys).containsExactlyElementsIn(IssueKind.entries.map { "issue.${it.name}" })
    }

    @Test
    fun `apagar deja de contar y borra los contadores`() = runTest { // R-17.7
        repository.setEnabled(true)
        repository.record(DiagnosticEvent.PlanCreated)

        repository.setEnabled(false)
        repository.record(DiagnosticEvent.PlanCreated)

        assertThat(repository.counters()).isEmpty()
        assertThat(repository.isEnabled()).isFalse()
    }

    @Test
    fun `borrar pone todo a cero y sigue contando`() = runTest { // R-17.6
        repository.setEnabled(true)
        repository.record(DiagnosticEvent.PlanCreated)

        repository.clear()
        repository.record(DiagnosticEvent.Step.REVIEW)

        assertThat(repository.counters()).containsExactly("step.review", 1)
    }
}
