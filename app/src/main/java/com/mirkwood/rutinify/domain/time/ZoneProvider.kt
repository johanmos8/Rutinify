package com.mirkwood.rutinify.domain.time

import java.time.ZoneId

/** Zona horaria vigente. Se consulta en cada uso porque puede cambiar en caliente (R-12.2). */
fun interface ZoneProvider {
    fun zone(): ZoneId
}
