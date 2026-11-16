package com.mirkwood.rutinify.domain.model

/** A category used by some plan and whether its alarms are critical (R-15.1, D-66). */
data class CategorySetting(val name: String, val critical: Boolean)
