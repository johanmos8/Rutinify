package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.importer.PlanLink
import javax.inject.Inject

/** A scanned QR or an opened `rutinify://import` link (R-19.7, R-19.8): the plan JSON, or null if it is not one. */
class DecodePlanLinkUseCase @Inject constructor() {
    fun isLink(text: String): Boolean = PlanLink.isLink(text)

    operator fun invoke(text: String): String? = PlanLink.decode(text)
}
