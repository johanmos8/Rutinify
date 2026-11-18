package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.diagnostics.DiagnosticsRepository
import com.mirkwood.rutinify.domain.system.AppInfo
import javax.inject.Inject

/** What "Exportar diagnóstico" shares: the counters and the app version, nothing about the routines (R-17.5). */
data class DiagnosticsReport(val versionName: String, val versionCode: Long, val counters: Map<String, Int>)

class ExportDiagnosticsUseCase @Inject constructor(
    private val diagnostics: DiagnosticsRepository,
    private val appInfo: AppInfo,
) {
    suspend operator fun invoke(): DiagnosticsReport =
        DiagnosticsReport(appInfo.versionName, appInfo.versionCode, diagnostics.counters())
}
