package com.mirkwood.rutinify.domain.system

/** The installed app's version, for the diagnostics export (R-17.5). */
interface AppInfo {
    val versionName: String
    val versionCode: Long
}
