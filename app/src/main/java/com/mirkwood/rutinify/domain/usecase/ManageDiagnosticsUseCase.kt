package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.diagnostics.DiagnosticsRepository
import javax.inject.Inject

/** The diagnostics switch and "Borrar diagnóstico" in Settings (R-17.1, R-17.6, R-17.7). */
class ManageDiagnosticsUseCase @Inject constructor(private val diagnostics: DiagnosticsRepository) {

    fun isEnabled(): Boolean = diagnostics.isEnabled()

    /** Turning it off also deletes the counters (R-17.7). */
    suspend fun setEnabled(enabled: Boolean) = diagnostics.setEnabled(enabled)

    suspend fun clear() = diagnostics.clear()
}
