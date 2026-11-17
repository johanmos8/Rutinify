package com.mirkwood.rutinify.domain.model

import java.util.Locale

/** A category used by some plan and whether its alarms are critical (R-15.1, D-66). */
data class CategorySetting(val name: String, val critical: Boolean)

/** Readable name of a normalized category: "paseo-largo" → "Paseo largo" (design §8). */
fun categoryDisplayName(category: String): String =
    category.replace('-', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
