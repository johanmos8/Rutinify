package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.system.DocumentExporter
import javax.inject.Inject

/** "Exportar historial" › "PDF" (R-20.5, R-20.6): the already-written lines into a PDF; null when it fails. */
class ExportHistoryPdfUseCase @Inject constructor(private val exporter: DocumentExporter) {
    suspend operator fun invoke(title: String, lines: List<String>): String? = exporter.writePdf(title, lines)
}
