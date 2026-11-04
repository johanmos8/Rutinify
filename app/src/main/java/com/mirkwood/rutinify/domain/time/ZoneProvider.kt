package com.mirkwood.rutinify.domain.time

import java.time.ZoneId

/** Current time zone. Read on every use because it can change at runtime (R-12.2). */
fun interface ZoneProvider {
    fun zone(): ZoneId
}
