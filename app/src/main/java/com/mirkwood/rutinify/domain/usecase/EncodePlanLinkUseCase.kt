package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.importer.PlanLink
import com.mirkwood.rutinify.domain.importer.QrEncoder
import com.mirkwood.rutinify.domain.importer.QrMatrix
import javax.inject.Inject

/** "Mostrar QR" (R-19.4, R-19.5): the plan's link and its QR code, or why it does not fit. */
sealed interface PlanQrResult {
    data class Ok(val link: String, val qr: QrMatrix) : PlanQrResult
    data object TooLarge : PlanQrResult
    data object NotFound : PlanQrResult
}

class EncodePlanLinkUseCase @Inject constructor(
    private val exportPlan: ExportPlanUseCase,
    private val qrEncoder: QrEncoder,
) {
    suspend operator fun invoke(orderId: Long): PlanQrResult {
        val json = exportPlan(orderId, pretty = false) ?: return PlanQrResult.NotFound
        return when (val encoded = PlanLink.encode(json)) {
            is PlanLink.Encoded.Ok -> PlanQrResult.Ok(encoded.link, qrEncoder.encode(encoded.link))
            is PlanLink.Encoded.TooLarge -> PlanQrResult.TooLarge
        }
    }
}
